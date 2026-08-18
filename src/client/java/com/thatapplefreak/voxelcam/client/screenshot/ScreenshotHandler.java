package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.util.ChatMessages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;

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
		if (saving) {
			ChatMessages.send("voxelcam.savingpleasewait");
			return true;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		boolean shiftHeld = InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_LEFT_SHIFT)
				|| InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_RIGHT_SHIFT);
		if (shiftHeld) {
			// Big/panorama-style oversized screenshots (the old BigScreenshotTaker) relied on
			// reflectively resizing the actual game window and rendering into a VoxelCommon FBO.
			// Modern MC's GPU-command-encoder render pipeline has no equivalent hook verified yet;
			// porting this needs dedicated, visually-tested work rather than a guessed reimplementation.
			ChatMessages.send("voxelcam.bigscreenshotunsupported");
			return true;
		}

		capture(framebuffer);
		return true;
	}

	private static void capture(Framebuffer framebuffer) {
		File screenshotsDir = new File(MinecraftClient.getInstance().runDirectory, ScreenshotRecorder.SCREENSHOTS_DIRECTORY);
		if (!screenshotsDir.exists()) {
			screenshotsDir.mkdirs();
		}
		File target = ScreenshotNamer.getScreenshotName(screenshotsDir);

		saving = true;
		ScreenshotRecorder.takeScreenshot(framebuffer, nativeImage -> save(nativeImage, target));
	}

	private static void save(NativeImage image, File target) {
		try (image) {
			image.writeTo(target);
			ChatMessages.send("voxelcam.savedscreenshotas", target.getName());
		} catch (IOException e) {
			VoxelCamClient.LOGGER.error("Failed to save screenshot to {}", target, e);
			ChatMessages.send("voxelcam.savefailed");
		} finally {
			saving = false;
		}
	}
}
