package com.thatapplefreak.voxelcam.client.screenshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.util.ChatMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replaces VoxelCommon/LiteLoader's ScreenshotListener callback. Invoked from
 * {@code ScreenshotRecorderMixin} in place of vanilla's screenshot save.
 */
public final class ScreenshotHandler {

	private static volatile boolean saving = false;

	private ScreenshotHandler() {
	}

	/**
	 * Always cancels vanilla's own save: letting it through would write a file under its own
	 * naming scheme, bypassing {@link ScreenshotNamer} entirely.
	 *
	 * <p>Whether it captures is a different question. Vanilla's screenshot key fires this once at
	 * key-down and cannot tell a tap from a hold, so while VoxelCam's own binding on that key is
	 * involved the decision belongs to {@link CaptureMenu}, which makes it on release — capturing
	 * here as well would take a second, unwanted screenshot on every hold.
	 *
	 * <p>Everything else still captures on the spot. {@code Screenshot.grab} is public API that
	 * other mods and code paths call, and a press brief enough that the tick-based edge detector
	 * never sees the key down never reaches {@link CaptureMenu} at all — swallowing those would
	 * lose screenshots VoxelCam used to take.
	 *
	 * @return true, always, so vanilla's own save is cancelled.
	 */
	public static boolean onScreenshotKeyPressed(RenderTarget framebuffer) {
		if (VoxelCamClient.isCaptureMenuKeyDown() || CaptureMenu.isArmed()) {
			return true;
		}

		captureNow(framebuffer);
		return true;
	}

	static boolean isSaving() {
		return saving;
	}

	/** Takes a plain screenshot right now, resolving its own framebuffer. */
	static void captureNow() {
		captureNow(Minecraft.getInstance().gameRenderer.mainRenderTarget());
	}

	/** Takes a plain screenshot of the given frame, refusing while another save is in flight. */
	static void captureNow(RenderTarget framebuffer) {
		if (saving || BigScreenshot.isBusy()) {
			ChatMessages.send("voxelcam.savingpleasewait");
			return;
		}
		capture(framebuffer);
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
		// Read while still on the render thread: by the time write() runs on the IO pool,
		// the player may have moved on to a different frame's state entirely.
		CaptureContext context = CaptureContext.capture();

		// This runs on the render thread, where encoding an oversized PNG would stall the game
		// for seconds. The saving flag stays up until the write is actually finished.
		Util.ioPool().execute(() -> write(image, target, context));
	}

	private static void write(NativeImage image, File target, CaptureContext context) {
		Minecraft client = Minecraft.getInstance();
		try (image) {
			writeOrDiscard(target, image::writeToFile);
			embedMetadata(target, context);
			client.execute(() -> ChatMessages.send("voxelcam.savedscreenshotas", target.getName()));
		} catch (IOException e) {
			VoxelCamClient.LOGGER.error("Failed to save screenshot to {}", target, e);
			client.execute(() -> ChatMessages.send("voxelcam.savefailed"));
		} finally {
			saving = false;
		}
	}

	/**
	 * {@code NativeImage.writeToFile} opens the target WRITE|CREATE|TRUNCATE_EXISTING and only
	 * then throws if the stb encode fails, so a failed save would otherwise leave an empty or
	 * half-written .png behind: the manager lists it forever, never decodes it, and re-reads its
	 * header every frame it is on screen. A file that was already there is left alone — this
	 * undoes what the failed write itself created, nothing else.
	 *
	 * <p>Split out from {@link #write} so it can be tested; encoding a real NativeImage needs a
	 * GL context.
	 */
	static void writeOrDiscard(File target, PngWriter writer) throws IOException {
		boolean existed = target.exists();
		try {
			writer.writeTo(target);
		} catch (IOException e) {
			if (!existed) {
				target.delete();
			}
			throw e;
		}
	}

	/** The encode step of a save, as a seam. */
	@FunctionalInterface
	interface PngWriter {
		void writeTo(File target) throws IOException;
	}

	/**
	 * The screenshot itself is already saved and good at this point; losing a tag is a
	 * shame, not a failure worth surfacing to the player the way a failed save is. Mod
	 * provenance is captured here rather than on the render thread with the capture
	 * context: the mod list is fixed for the session and the shader pack reads fresh
	 * either way, so neither needs a snapshot before the frame moves on.
	 */
	private static void embedMetadata(File target, CaptureContext context) {
		Map<String, String> tags = new LinkedHashMap<>();
		if (context != null) {
			tags.putAll(context.toTags());
		}
		tags.putAll(ModProvenance.capture().toTags());
		if (tags.isEmpty()) {
			return;
		}
		try {
			PngTextChunk.embed(target, tags);
		} catch (IOException e) {
			VoxelCamClient.LOGGER.error("Failed to embed metadata into {}", target, e);
		}
	}
}
