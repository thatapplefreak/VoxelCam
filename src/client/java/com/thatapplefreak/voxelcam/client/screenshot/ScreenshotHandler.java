package com.thatapplefreak.voxelcam.client.screenshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.util.ChatMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces VoxelCommon/LiteLoader's ScreenshotListener callback. Invoked from
 * {@code ScreenshotRecorderMixin} in place of vanilla's screenshot save.
 */
public final class ScreenshotHandler {

	/**
	 * Captures between their readback request and their finished write. A plain or oversized
	 * capture claims exactly one slot; a burst claims up to {@code Burst.MAX_IN_FLIGHT} so several
	 * frames can be mid-encode on {@code Util.ioPool()} at once.
	 *
	 * <p>An {@code AtomicInteger} rather than a {@code volatile int}: every begin runs on the
	 * render thread, while an end runs from {@link #write}'s {@code finally} on {@code
	 * Util.ioPool()}. {@code volatile} gives visibility, not atomicity — a plain
	 * increment/decrement racing across those two threads can lose an update, and a lost decrement
	 * bricks capture for the session while a lost increment lets two captures share a name. The
	 * old {@code volatile boolean} was only ever safe because both of its writes were idempotent
	 * constants.
	 */
	private static final AtomicInteger inFlight = new AtomicInteger();

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
		if (CaptureMenu.isArmed()) {
			return true;
		}
		if (VoxelCamClient.isCaptureMenuKeyDown() && Minecraft.getInstance().gui.screen() == null) {
			// This call *is* the press, and it is the only signal that reliably arrives: both
			// bindings want F2, vanilla's key map hands the key to exactly one of them, and if
			// that one is vanilla's then nothing on the tick side ever sees the key at all.
			//
			// With a screen open the menu has nowhere to go, so that press falls through and takes
			// an ordinary screenshot the way the key always did over a GUI.
			CaptureMenu.onKeyDown();
			return true;
		}

		captureNow(framebuffer);
		return true;
	}

	static boolean isSaving() {
		return savesInFlight() > 0;
	}

	static int savesInFlight() {
		return inFlight.get();
	}

	/**
	 * Claims a slot if fewer than {@code limit} are currently in flight. A CAS loop rather than
	 * {@code getAndIncrement} plus a rollback on overflow: a rollback would briefly let a
	 * concurrent reader observe the counter sitting over the cap.
	 */
	static boolean beginSave(int limit) {
		while (true) {
			int current = inFlight.get();
			if (current >= limit) {
				return false;
			}
			if (inFlight.compareAndSet(current, current + 1)) {
				return true;
			}
		}
	}

	/**
	 * Claims a slot for a capture whose busy-guard lives elsewhere. The oversized path is always
	 * gated by {@code BigScreenshot}'s own state before this can run — nothing can be in flight
	 * when its blit fires — so there is no cap to refuse against here.
	 */
	static void joinSave() {
		inFlight.incrementAndGet();
	}

	/**
	 * Releases a slot. Clamped at zero and logged rather than left to go negative: a negative
	 * count would silently widen every future gate instead of failing where the imbalance was
	 * actually introduced.
	 */
	static void endSave() {
		int updated = inFlight.decrementAndGet();
		if (updated < 0) {
			VoxelCamClient.LOGGER.error("Save counter went negative — an endSave() was not paired with a begin");
			inFlight.compareAndSet(updated, 0);
		}
	}

	/** Test-only reset: the counter is static and outlives any one test. */
	static void forgetSavesInFlight() {
		inFlight.set(0);
	}

	/** Takes a plain screenshot right now, resolving its own framebuffer. */
	static void captureNow() {
		captureNow(Minecraft.getInstance().gameRenderer.mainRenderTarget());
	}

	/** Takes a plain screenshot of the given frame, refusing while another save is in flight. */
	static void captureNow(RenderTarget framebuffer) {
		if (BigScreenshot.isBusy() || Burst.isBusy() || !beginSave(1)) {
			ChatMessages.send("voxelcam.savingpleasewait");
			return;
		}
		capture(framebuffer);
	}

	/**
	 * Takes the single frame an {@link AutoCapture} moment armed, tagged with what armed it.
	 *
	 * <p>Deliberately not {@link #captureNow(RenderTarget)}: that path answers a refusal with
	 * {@code voxelcam.savingpleasewait} in chat, which is right for a keypress and wrong here.
	 * Nobody asked for this screenshot, so nobody wants to be told at length why it did not happen
	 * — the caller gets a {@code false} and drops it.
	 *
	 * @return whether the readback was actually issued.
	 */
	static boolean captureMoment(RenderTarget framebuffer, MomentTag moment) {
		if (BigScreenshot.isBusy() || Burst.isBusy() || !beginSave(1)) {
			return false;
		}
		try {
			Screenshot.takeScreenshot(framebuffer, image -> saveCapturedImage(image, moment));
		} catch (Throwable t) {
			endSave();
			VoxelCamClient.LOGGER.error("Failed to read back an automatic {} capture",
					moment.trigger().token(), t);
			return false;
		}
		return true;
	}

	private static void capture(RenderTarget framebuffer) {
		try {
			Screenshot.takeScreenshot(framebuffer, ScreenshotHandler::saveCapturedImage);
		} catch (Throwable t) {
			endSave();
			VoxelCamClient.LOGGER.error("Failed to read back a screenshot", t);
			ChatMessages.send("voxelcam.savefailed");
		}
	}

	/**
	 * Takes one frame of a burst at a name {@code Burst} has already reserved, tagged with its
	 * place in the group. The caller has already claimed this slot with {@link #beginSave}; a
	 * readback that throws releases it and tells the burst to stop rather than leaving a silent
	 * gap in the middle of a sequence.
	 */
	static void captureBurstFrame(RenderTarget framebuffer, File target, BurstFrame frame) {
		try {
			Screenshot.takeScreenshot(framebuffer, image -> saveCapturedImage(image, target, frame, null));
		} catch (Throwable t) {
			endSave();
			VoxelCamClient.LOGGER.error("Failed to read back burst frame {}", frame.index(), t);
			// This frame claimed a slot in Burst's issued count (nextIndex) and must balance it
			// in the completed count too, or the group's end-of-burst announcement waits
			// forever for a frame that will never report in on its own.
			Burst.frameCompleted(false);
			Burst.abort("a readback failed");
		}
	}

	/**
	 * Names and writes an already-captured frame. Shared with {@link BigScreenshot}, so the
	 * oversized path lands in the same folder under the same naming scheme and shows up in
	 * the manager without any extra plumbing.
	 */
	static void saveCapturedImage(NativeImage image) {
		saveCapturedImage(image, null);
	}

	static void saveCapturedImage(NativeImage image, MomentTag moment) {
		File screenshotsDir = new File(Minecraft.getInstance().gameDirectory, Screenshot.SCREENSHOT_DIR);
		if (!screenshotsDir.exists()) {
			screenshotsDir.mkdirs();
		}
		File target = ScreenshotNamer.getScreenshotName(screenshotsDir);
		saveCapturedImage(image, target, null, moment);
	}

	/**
	 * Shared by the plain/oversized path ({@code burst == null}) and {@link #captureBurstFrame}.
	 *
	 * <p>{@code burst} and {@code moment} stay separate parameters rather than one bag of extras
	 * because they are not the same kind of thing: {@code burst} carries <em>behaviour</em> — the
	 * completion accounting {@link Burst#frameCompleted} depends on, and the chat suppression that
	 * keeps a burst from announcing every frame — while {@code moment} carries only tags and a
	 * different chat key. Both are null for an ordinary keypress, and never both non-null.
	 */
	static void saveCapturedImage(NativeImage image, File target, BurstFrame burst, MomentTag moment) {
		// Read while still on the render thread: by the time write() runs on the IO pool, the
		// player may have moved on to a different frame's state entirely.
		//
		// Guarded end to end: if anything between here and a successful submit throws — a
		// snapshot that touches Minecraft state at an unlucky moment, Util.ioPool() rejecting
		// during shutdown — the image would otherwise leak its native memory (write()'s try
		// (image) never runs) on top of the slot leaking with it.
		boolean submitted = false;
		try {
			CaptureContext context = CaptureContext.capture();
			// This runs on the render thread, where encoding an oversized PNG would stall the
			// game for seconds. The slot stays claimed until the write is actually finished.
			Util.ioPool().execute(() -> write(image, target, context, burst, moment));
			submitted = true;
		} finally {
			if (!submitted) {
				image.close();
				endSave();
				// Same accounting as captureBurstFrame's catch: this frame's slot was already
				// counted as issued, so it has to report a completion of its own — nothing
				// downstream of here will ever run for it otherwise.
				if (burst != null) {
					Burst.frameCompleted(false);
				}
			}
		}
	}

	private static void write(NativeImage image, File target, CaptureContext context, BurstFrame burst,
			MomentTag moment) {
		Minecraft client = Minecraft.getInstance();
		boolean succeeded = false;
		try (image) {
			writeOrDiscard(target, image::writeToFile);
			succeeded = true;
			embedMetadata(target, context, burst, moment);
			if (burst == null) {
				client.execute(() -> announceSaved(target, moment));
			}
		} catch (IOException e) {
			VoxelCamClient.LOGGER.error("Failed to save screenshot to {}", target, e);
			if (burst == null) {
				client.execute(() -> ChatMessages.send("voxelcam.savefailed"));
			}
		} finally {
			// A burst's frames stay quiet individually — one voxelcam.savedscreenshotas per
			// frame would spam chat for what the player experienced as a single press — and
			// Burst.frameCompleted is what eventually speaks for the whole group, once, only
			// after every frame it issued has reported in this same way. This runs on
			// Util.ioPool(), so it reaches Burst's otherwise render-thread-only state through
			// client.execute the same way the chat messages above do.
			if (burst != null) {
				boolean frameSucceeded = succeeded;
				client.execute(() -> Burst.frameCompleted(frameSucceeded));
			}
			endSave();
		}
	}

	/**
	 * An automatic capture says what caught it, not just where it landed — the file name alone
	 * would leave the player wondering what they had just been given. Unlike most of VoxelCam's
	 * feedback this can safely go to chat: {@code ChatMessages} is silent with no player, and every
	 * moment trigger requires one.
	 */
	private static void announceSaved(File target, MomentTag moment) {
		if (moment == null) {
			ChatMessages.send("voxelcam.savedscreenshotas", target.getName());
			return;
		}
		ChatMessages.send(Component.translatable("voxelcam.moment.saved", moment.describe(), target.getName()));
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
	private static void embedMetadata(File target, CaptureContext context, BurstFrame burst, MomentTag moment) {
		Map<String, String> tags = new LinkedHashMap<>();
		if (context != null) {
			tags.putAll(context.toTags());
		}
		tags.putAll(ModProvenance.capture().toTags());
		if (burst != null) {
			tags.putAll(burst.toTags());
		}
		if (moment != null) {
			tags.putAll(moment.toTags());
		}
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
