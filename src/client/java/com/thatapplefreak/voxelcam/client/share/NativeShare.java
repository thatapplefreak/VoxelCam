package com.thatapplefreak.voxelcam.client.share;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * manager, and GLFW's (text-only) clipboard.
 */
public final class NativeShare {

	private NativeShare() {
	}

	/**
	 * Opens the platform's native Save-As dialog and copies the screenshot to
	 * wherever the user points it - a synced Dropbox/iCloud/OneDrive folder
	 * included, which is as close to "share" as this gets.
	 *
	 * <p>The dialog blocks its thread and, on macOS, drives AppleScript, so it
	 * runs on the IO worker rather than the render thread. Completes with null
	 * if the user cancelled.
	 */
	public static CompletableFuture<Path> saveCopy(File screenshot, String dialogTitle, String filterDescription) {
		return CompletableFuture.supplyAsync(() -> {
			String chosen;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.png"));
				filters.flip();
				chosen = TinyFileDialogs.tinyfd_saveFileDialog(
						dialogTitle, screenshot.getName(), filters, filterDescription);
			}
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
	 * Everything saveCopy does once the user has picked a path. Split out from the
	 * dialog so it can be tested: the dialog itself is a blocking native call with
	 * no headless mode.
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
				() -> reveal(command, parent, () -> Util.getPlatform().openFile(parent)), Util.ioPool());
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

	/** GLFW's clipboard carries text only, so this shares the path, not the image. */
	public static void copyPath(File screenshot) {
		Minecraft.getInstance().keyboardHandler.setClipboard(screenshot.getAbsolutePath());
	}

	public static void copyText(String text) {
		Minecraft.getInstance().keyboardHandler.setClipboard(text);
	}
}
