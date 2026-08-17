package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.util.ChatMessages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
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

	public static boolean isSaving() {
		return saving;
	}

	/** @return true if VoxelCam handled the screenshot and vanilla's save should be cancelled. */
	public static boolean onScreenshotKeyPressed(Framebuffer framebuffer) {
		if (saving) {
			ChatMessages.send("voxelcam.savingpleasewait");
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
			ChatMessages.send("voxelcam.uploadfailed");
		} finally {
			saving = false;
		}
	}
}
