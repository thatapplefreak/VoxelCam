package com.thatapplefreak.voxelcam.client.share;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import net.minecraft.client.MinecraftClient;
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
			// The dialog does not enforce the filter's extension on every platform.
			Path target = chosen.toLowerCase().endsWith(".png") ? Path.of(chosen) : Path.of(chosen + ".png");
			try {
				Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return target;
		}, Util.getIoWorkerExecutor());
	}

	/**
	 * Shows the file selected in the platform file manager. Only macOS and
	 * Windows have a documented "reveal this item" command; elsewhere the
	 * containing folder is opened instead.
	 */
	public static void revealInFileManager(File screenshot) {
		File parent = screenshot.getParentFile();
		String[] command = switch (Util.getOperatingSystem()) {
			case OSX -> new String[] { "open", "-R", screenshot.getAbsolutePath() };
			case WINDOWS -> new String[] { "explorer.exe", "/select," + screenshot.getAbsolutePath() };
			default -> null;
		};
		if (command == null) {
			Util.getOperatingSystem().open(parent);
			return;
		}
		try {
			new ProcessBuilder(command).start();
		} catch (IOException e) {
			// Explorer and open are not guaranteed present (stripped installs, WSL);
			// showing the folder is still better than doing nothing.
			VoxelCamClient.LOGGER.warn("Could not reveal {} in the file manager", screenshot, e);
			Util.getOperatingSystem().open(parent);
		}
	}

	/** GLFW's clipboard carries text only, so this shares the path, not the image. */
	public static void copyPath(File screenshot) {
		MinecraftClient.getInstance().keyboard.setClipboard(screenshot.getAbsolutePath());
	}

	public static void copyText(String text) {
		MinecraftClient.getInstance().keyboard.setClipboard(text);
	}
}
