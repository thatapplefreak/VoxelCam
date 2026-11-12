package com.thatapplefreak.voxelcam.client.share;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The OS-level sharing this mod can actually reach. There is no cross-platform
 * share sheet available to a JVM process: macOS NSSharingServicePicker and
 * Windows DataTransferManager both need native interop and a window to anchor
 * to, and Linux has no equivalent portal at all. What is reachable is the
 * native file dialog LWJGL already ships with Minecraft, the platform file
 * manager, and vanilla's (text-only) clipboard.
 */
public final class NativeShare {

	private NativeShare() {
	}

	/**
	 * Opens the platform's native Save-As dialog and copies the screenshot to
	 * wherever the user points it - a synced Dropbox/iCloud/OneDrive folder
	 * included, which is as close to "share" as this gets.
	 *
	 * <p>Completes with null if the user cancelled.
	 *
	 * <p>26.3 moved the client from GLFW to SDL and dropped {@code lwjgl-tinyfd} with it,
	 * so the blocking {@code tinyfd_saveFileDialog} this used to call no longer exists.
	 * SDL's dialog is its successor and is <em>asynchronous</em>: it must be shown from
	 * the main thread and answers through a callback invoked later, on a thread SDL does
	 * not document. So the shape here inverts - the dialog is opened on the calling (render)
	 * thread rather than on {@code Util.ioPool()}, and only the copy that follows runs on
	 * the IO worker. The returned future is unchanged, which is what keeps
	 * {@code SharePopup} untouched.
	 *
	 * <p>{@code dialogTitle} is accepted and unused: SDL's simple save-dialog entry point
	 * takes no caption, so the OS default shows instead. It stays in the signature, with its
	 * lang key, so that restoring it later costs one call rather than a signature change -
	 * see ADR-0012.
	 */
	public static CompletableFuture<Path> saveCopy(File screenshot, String dialogTitle, String filterDescription) {
		return showSaveDialog(screenshot, filterDescription).thenApplyAsync(chosen -> {
			if (chosen == null) {
				return null;
			}
			try {
				return copyTo(screenshot, chosen);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}, Util.ioPool());
	}

	/**
	 * Shows SDL's save dialog and completes with the chosen path, or null on cancel.
	 *
	 * <p>Nothing handed to SDL here may live on the {@code MemoryStack}: the callback can
	 * arrive long after a stack frame would have been popped, and SDL does not copy the
	 * filters. So the filter struct, its two strings and
	 * the default location are all allocated off-heap and freed in the callback - which is
	 * also the only place the callback itself can be freed, since it has to outlive the
	 * call that registers it.
	 *
	 * <p>The default location is the screenshot's own full path, so the dialog opens in the
	 * screenshots folder with that name filled in. tinyfd got the bare filename, which left
	 * the starting folder to the process's working directory - this is the better default,
	 * and it means the pre-filled target is the source file, which {@code Files.copy} treats
	 * as a no-op rather than an error if the player just presses Save.
	 */
	private static CompletableFuture<String> showSaveDialog(File screenshot, String filterDescription) {
		CompletableFuture<String> chosen = new CompletableFuture<>();

		ByteBuffer filterName = MemoryUtil.memUTF8(filterDescription);
		ByteBuffer filterPattern = MemoryUtil.memUTF8("png");
		ByteBuffer location = MemoryUtil.memUTF8(screenshot.getAbsolutePath());
		SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
		filters.get(0).set(filterName, filterPattern);

		// The callback frees itself, so it needs a handle on itself: hence the
		// one-element holder rather than a plain local.
		SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
		callback[0] = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
			try {
				if (filelist == MemoryUtil.NULL) {
					chosen.completeExceptionally(
							new IOException("The save dialog failed: " + SDLError.SDL_GetError()));
					return;
				}
				// A non-null but empty list is a cancel, which is not a failure.
				long first = MemoryUtil.memGetAddress(filelist);
				chosen.complete(first == MemoryUtil.NULL ? null : MemoryUtil.memUTF8(first));
			} finally {
				filters.free();
				MemoryUtil.memFree(filterName);
				MemoryUtil.memFree(filterPattern);
				MemoryUtil.memFree(location);
				callback[0].free();
			}
		});

		SDLDialog.SDL_ShowSaveFileDialog(callback[0], MemoryUtil.NULL,
				Minecraft.getInstance().getWindow().handle(), filters, location);
		return chosen;
	}

	/**
	 * Everything saveCopy does once the user has picked a path. Split out from the
	 * dialog so it can be tested: the dialog itself is a native window with no
	 * headless mode, and now answers through an SDL callback besides.
	 */
	static Path copyTo(File screenshot, String chosen) throws IOException {
		Path target = targetPath(chosen);
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		return target;
	}

	/** The dialog does not enforce the filter's extension on every platform. */
	static Path targetPath(String chosen) {
		return chosen.toLowerCase().endsWith(".png") ? Path.of(chosen) : Path.of(chosen + ".png");
	}

	/** What a reveal actually managed to put on screen. */
	public enum RevealOutcome {
		/** The file manager opened with the screenshot itself selected. */
		REVEALED,
		/** No reveal command, or it failed: the containing folder was opened. */
		OPENED_FOLDER,
		/** Not even the folder is there any more, so nothing was opened. */
		FAILED
	}

	/**
	 * How long to wait for the reveal command before assuming it worked. Both
	 * {@code open} and {@code explorer.exe} hand off to an already-running file
	 * manager and return immediately, so a child still alive after this is unusual —
	 * but that is not proof it failed, which is why {@link #runReveal} still credits
	 * it rather than stacking a folder window on top of a slow reveal.
	 */
	private static final int REVEAL_TIMEOUT_SECONDS = 2;

	/**
	 * Shows the file selected in the platform file manager. Only macOS and
	 * Windows have a documented "reveal this item" command; elsewhere the
	 * containing folder is opened instead.
	 *
	 * <p>The child process is waited on, which is why this is async: a reveal that
	 * launches and then fails (the file was deleted under us) exits non-zero
	 * rather than throwing, and telling the player it worked would leave them
	 * hunting for a window that never opened.
	 */
	public static CompletableFuture<RevealOutcome> revealInFileManager(File screenshot) {
		File parent = screenshot.getParentFile();
		String[] command = revealCommand(Util.getPlatform(), screenshot);
		return CompletableFuture.supplyAsync(
				() -> reveal(command, parent, () -> openFolder(parent)), Util.ioPool());
	}

	/**
	 * The reveal with the platform lookup and the folder fallback handed in, so the
	 * outcome can be tested without a file manager opening on the tester's desktop.
	 */
	static RevealOutcome reveal(String[] command, File parent, Runnable openFolder) {
		if (command != null && runReveal(command)) {
			return RevealOutcome.REVEALED;
		}
		if (parent == null || !parent.isDirectory()) {
			return RevealOutcome.FAILED;
		}
		openFolder.run();
		return RevealOutcome.OPENED_FOLDER;
	}

	/** Runs one reveal command to completion and reports whether it revealed anything. */
	static boolean runReveal(String[] command) {
		try {
			Process process = new ProcessBuilder(command)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			if (!process.waitFor(REVEAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				// Still running: treating that as a failure would stack a folder window
				// on top of a reveal that is merely slow, which is worse than trusting it.
				return true;
			}
			int exit = process.exitValue();
			if (revealed(command, exit)) {
				return true;
			}
			// The child's own explanation went to a discarded stderr, so the exit code
			// is all there is to record.
			VoxelCamClient.LOGGER.warn("Reveal command {} exited {}", command[0], exit);
			return false;
		} catch (IOException e) {
			// Explorer and open are not guaranteed present (stripped installs, WSL);
			// showing the folder is still better than doing nothing.
			VoxelCamClient.LOGGER.warn("Could not run the reveal command {}", command[0], e);
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Whether a finished reveal command actually revealed the file. explorer.exe is
	 * documented to exit 1 whether or not it opened the window, so its status says
	 * nothing and the folder fallback would fire on every Windows reveal.
	 */
	static boolean revealed(String[] command, int exitCode) {
		return command[0].equals("explorer.exe") || exitCode == 0;
	}

	/**
	 * Opens a directory in the platform file manager.
	 *
	 * <p>26.3 reduced {@code Util.OS} to a telemetry tag - {@code openFile(File)} and the
	 * rest of its behaviour went with the GLFW-to-SDL switch - so there is no vanilla
	 * call left to delegate to and the command is ours to name. Failure is logged and
	 * swallowed: this backs a button and a fallback, neither of which has anywhere to
	 * report to.
	 *
	 * <p><strong>Runs the command on the calling thread</strong>, which waits on the child
	 * for up to {@link #REVEAL_TIMEOUT_SECONDS}. That suits {@link #reveal}'s fallback,
	 * which is already on {@code Util.ioPool()} and wants the wait; the one render-thread
	 * caller (the manager's folder button) dispatches for itself. Dispatching in here
	 * instead would hand the fallback a second pool hop and let it report
	 * {@code OPENED_FOLDER} before the command had even started.
	 */
	public static void openFolder(File directory) {
		String[] command = openFolderCommand(Util.getPlatform(), directory);
		if (command == null) {
			VoxelCamClient.LOGGER.warn("No folder-open command for platform {}", Util.getPlatform());
			return;
		}
		runReveal(command);
	}

	/**
	 * The "open this folder" command for a platform, or null where there is none.
	 * Separate from {@link #revealCommand} because xdg-open is the right tool for a
	 * directory on Linux while no Linux file manager has a documented reveal.
	 */
	static String[] openFolderCommand(Util.OS platform, File directory) {
		if (directory == null) {
			return null;
		}
		return switch (platform) {
			case OSX -> new String[] { "open", directory.getAbsolutePath() };
			case WINDOWS -> new String[] { "explorer.exe", directory.getAbsolutePath() };
			case LINUX, SOLARIS -> new String[] { "xdg-open", directory.getAbsolutePath() };
			default -> null;
		};
	}

	/**
	 * The "reveal this item" command for a platform, or null where there is none and
	 * the containing folder has to be opened instead.
	 */
	static String[] revealCommand(Util.OS platform, File screenshot) {
		return switch (platform) {
			case OSX -> new String[] { "open", "-R", screenshot.getAbsolutePath() };
			case WINDOWS -> new String[] { "explorer.exe", "/select," + screenshot.getAbsolutePath() };
			default -> null;
		};
	}

	/** The platform clipboard carries text only here, so this shares the path, not the image. */
	public static void copyPath(File screenshot) {
		Minecraft.getInstance().keyboardHandler.setClipboard(screenshot.getAbsolutePath());
	}

	public static void copyText(String text) {
		Minecraft.getInstance().keyboardHandler.setClipboard(text);
	}
}
