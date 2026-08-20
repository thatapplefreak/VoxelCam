package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.util.ChatMessages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Util;

import java.io.File;
import java.io.IOException;

/**
 * Replaces VoxelCommon/LiteLoader's ScreenshotListener callback. Invoked from
 * {@code ScreenshotRecorderMixin} in place of vanilla's screenshot save.
 */
public final class ScreenshotHandler {

	private static volatile boolean saving = false;

	private ScreenshotHandler() {
	}

	/** @return true if VoxelCam handled the screenshot and vanilla's save should be cancelled. */
	public static boolean onScreenshotKeyPressed(Framebuffer framebuffer) {
		if (saving || BigScreenshot.isBusy()) {
			ChatMessages.send("voxelcam.savingpleasewait");
			return true;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		boolean shiftHeld = InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_LEFT_SHIFT)
				|| InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_RIGHT_SHIFT);
		if (shiftHeld) {
			BigScreenshot.request();
			// Always cancel vanilla, refusals included: letting it through would write a file
			// under its own naming scheme, bypassing ScreenshotNamer entirely.
			return true;
		}

		capture(framebuffer);
		return true;
	}

	private static void capture(Framebuffer framebuffer) {
		saving = true;
		try {
			ScreenshotRecorder.takeScreenshot(framebuffer, ScreenshotHandler::saveCapturedImage);
		} catch (Throwable t) {
			saving = false;
			VoxelCamClient.LOGGER.error("Failed to read back a screenshot", t);
			ChatMessages.send("voxelcam.savefailed");
		}
	}

	/**
	 * Names and writes an already-captured frame. Shared with {@link BigScreenshot}, so the
	 * oversized path lands in the same folder under the same naming scheme and shows up in
	 * the manager without any extra plumbing.
	 */
	static void saveCapturedImage(NativeImage image) {
		saving = true;
		File screenshotsDir = new File(MinecraftClient.getInstance().runDirectory, ScreenshotRecorder.SCREENSHOTS_DIRECTORY);
		if (!screenshotsDir.exists()) {
			screenshotsDir.mkdirs();
		}
		File target = ScreenshotNamer.getScreenshotName(screenshotsDir);

		// This runs on the render thread, where encoding an oversized PNG would stall the game
		// for seconds. The saving flag stays up until the write is actually finished.
		Util.getIoWorkerExecutor().execute(() -> write(image, target));
	}

	private static void write(NativeImage image, File target) {
		MinecraftClient client = MinecraftClient.getInstance();
		try (image) {
			image.writeTo(target);
			client.execute(() -> ChatMessages.send("voxelcam.savedscreenshotas", target.getName()));
		} catch (IOException e) {
			VoxelCamClient.LOGGER.error("Failed to save screenshot to {}", target, e);
			client.execute(() -> ChatMessages.send("voxelcam.savefailed"));
		} finally {
			saving = false;
		}
	}
}
