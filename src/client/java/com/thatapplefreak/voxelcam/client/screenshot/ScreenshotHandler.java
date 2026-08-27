package com.thatapplefreak.voxelcam.client.screenshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.util.ChatMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
	public static boolean onScreenshotKeyPressed(RenderTarget framebuffer) {
		if (saving || BigScreenshot.isBusy()) {
			ChatMessages.send("voxelcam.savingpleasewait");
			return true;
		}

		Minecraft client = Minecraft.getInstance();
		boolean shiftHeld = InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LSHIFT)
				|| InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RSHIFT);
		if (shiftHeld) {
			BigScreenshot.request();
			// Always cancel vanilla, refusals included: letting it through would write a file
			// under its own naming scheme, bypassing ScreenshotNamer entirely.
			return true;
		}

		capture(framebuffer);
		return true;
	}

	private static void capture(RenderTarget framebuffer) {
		saving = true;
		try {
			Screenshot.takeScreenshot(framebuffer, ScreenshotHandler::saveCapturedImage);
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
		File screenshotsDir = new File(Minecraft.getInstance().gameDirectory, Screenshot.SCREENSHOT_DIR);
		if (!screenshotsDir.exists()) {
			screenshotsDir.mkdirs();
		}
		File target = ScreenshotNamer.getScreenshotName(screenshotsDir);

		// This runs on the render thread, where encoding an oversized PNG would stall the game
		// for seconds. The saving flag stays up until the write is actually finished.
		Util.ioPool().execute(() -> write(image, target));
	}

	private static void write(NativeImage image, File target) {
		Minecraft client = Minecraft.getInstance();
		try (image) {
			image.writeToFile(target);
			client.execute(() -> ChatMessages.send("voxelcam.savedscreenshotas", target.getName()));
		} catch (IOException e) {
			VoxelCamClient.LOGGER.error("Failed to save screenshot to {}", target, e);
			client.execute(() -> ChatMessages.send("voxelcam.savefailed"));
		} finally {
			saving = false;
		}
	}
}
