package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.io.File;
import java.util.List;

/**
 * A few seconds of play captured at {@link #FRAMES_PER_SECOND}, sharing one group id so the
 * manager can list the first frame as a key and fold the rest under it (see {@link BurstFrame},
 * {@code VoxelCamIO}'s collapse), and export them as an animation that plays back for as long as
 * the moment lasted.
 *
 * <p>A burst is a length of time, not a number of frames:
 * <ol>
 *   <li>{@link #request()} arms it (from the capture menu) and reserves every slot's name up
 *       front, synchronously, so no two overlapping captures can ever resolve the same one.</li>
 *   <li>{@link #beforeBlit()} issues a frame on the first blit at or after each slot's time —
 *       one every {@link #FRAME_INTERVAL_MILLIS}. A slot is also gated by {@link
 *       ScreenshotHandler#beginSave}: each in-flight frame is a full-res {@code NativeImage} held
 *       until {@code Util.ioPool()} finishes encoding it, so {@code MAX_IN_FLIGHT} bounds memory.
 *       A slot that passes while the cap is full is <em>skipped</em>, never caught up: catching
 *       up would bunch frames together, and every frame records its real offset, so playback
 *       stays true to time with one frame fewer rather than stuttering.</li>
 *   <li>{@link #frameCompleted(boolean)} hears back from each frame's write, on the render
 *       thread, and is what actually announces the group once every issued frame has reported
 *       in — see its own javadoc for why that has to wait rather than speak for frame 0 alone.</li>
 * </ol>
 *
 * <p>Unlike {@link BigScreenshot}, a burst consumer mutates nothing shared — it only writes a
 * file to a name reserved for it — so a consumer that arrives after the burst has moved on (or
 * been aborted) is not a hazard the way a late window-restore would be: it simply finishes a
 * frame that was already claimed. No generation tag is needed for that reason; the target
 * {@code File} and {@link BurstFrame} are captured by value at the point each frame is issued.
 */
public final class Burst {

	private enum State {
		IDLE,
		RUNNING
	}

	/**
	 * The memory ceiling, not the burst length: each in-flight frame is a full-res {@code
	 * NativeImage} (~8 MiB at 1080p, ~32 MiB at 4K) held until {@code Util.ioPool()} finishes
	 * encoding it. Matches {@code ScreenshotImageCache.MAX_FULL_SIZE} for the same reason.
	 */
	static final int MAX_IN_FLIGHT = 3;

	/**
	 * Ten a second: about the slowest rate at which motion still reads as motion rather than a
	 * slideshow — the rate of a reaction GIF. Fifteen is smoother, but costs half again the disk
	 * and the sharing copy's size, for moments only a few seconds long.
	 */
	public static final int FRAMES_PER_SECOND = 10;
	public static final int FRAME_INTERVAL_MILLIS = 1000 / FRAMES_PER_SECOND;

	/** Long enough to hold a jump, a kill or a firework from start to finish. */
	public static final int MIN_SECONDS = 2;
	/** Fifty frames: a 1080p burst at this length is on the order of 100 MB of PNGs. */
	public static final int MAX_SECONDS = 5;
	public static final int DEFAULT_SECONDS = 3;
	/** The most frames one burst can write; frame names keep two digits up to 99. */
	public static final int MAX_FRAMES = MAX_SECONDS * FRAMES_PER_SECOND;

	/**
	 * The length options on the capture menu's outer ring, in seconds — every length there is.
	 * Four fit a 120° wedge with room to spare at the ring's 24°-per-option spacing.
	 */
	public static final int[] DIAL_OPTIONS = { 2, 3, 4, 5 };

	/** Persisted through {@code VoxelCamConfig}. What the <em>next</em> burst will run for; not
	 * touched once a burst is running. */
	private static volatile int seconds = DEFAULT_SECONDS;

	// Render-thread state only: request() runs from the capture menu's fire(), the rest from
	// beforeBlit(); frameCompleted() arrives from ScreenshotHandler.write() (on Util.ioPool())
	// only via client.execute, which is what keeps it render-thread-only too.
	private static State state = State.IDLE;
	private static List<File> names;
	private static String groupId;
	/** How many frames have been issued — also the next frame's index and file name, so a burst
	 * that skipped slots still writes contiguous {@code _burstNN} names. */
	private static int nextIndex;
	/** The first schedule slot not yet issued or skipped. */
	private static int nextSlot;
	/** How many slots the running burst has — a separate field from {@link #seconds} so a unit
	 * test driving {@link #beginBurst} directly cannot change the session preference as a side
	 * effect. */
	private static int runningSlots;
	private static long startMillis;
	/** How many issued frames have finished writing, success or failure alike. Compared against
	 * {@link #nextIndex} (which stops changing once the burst leaves {@code RUNNING}, whether by
	 * finishing normally or by {@link #abort}) to know when nothing is left outstanding. */
	private static int completedFrames;
	private static int succeededFrames;

	private Burst() {
	}

	public static int getSeconds() {
		return seconds;
	}

	public static void setSeconds(int newSeconds) {
		seconds = Math.clamp(newSeconds, MIN_SECONDS, MAX_SECONDS);
	}

	/** How many frames a burst of this many seconds takes, if no slot is skipped. */
	public static int framesFor(int burstSeconds) {
		return burstSeconds * FRAMES_PER_SECOND;
	}

	public static boolean isBusy() {
		return state != State.IDLE;
	}

	/** Arms a burst for the next blit, explaining itself if it cannot. */
	public static void request() {
		if (state != State.IDLE || BigScreenshot.isBusy() || ScreenshotHandler.isSaving()) {
			CaptureStatus.post(CaptureStatus.Kind.BUSY, "voxelcam.savingpleasewait");
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (!canCapture(client)) {
			CaptureStatus.post(CaptureStatus.Kind.REFUSED, "voxelcam.burst.unavailable");
			return;
		}

		try {
			File screenshotsDir = new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR);
			if (!screenshotsDir.exists()) {
				screenshotsDir.mkdirs();
			}
			int slots = framesFor(seconds);
			names = ScreenshotNamer.reserveBurstNames(screenshotsDir, slots);
			groupId = baseName(names.get(0).getName());
			beginBurst(slots, Util.getMillis());
		} catch (Throwable t) {
			VoxelCamClient.LOGGER.error("Failed to reserve names for a burst capture", t);
			CaptureStatus.post(CaptureStatus.Kind.FAILED, "voxelcam.burst.failed");
		}
	}

	/** The same {@code ScreenshotIncapable} gate {@code BigScreenshot} checks. */
	private static boolean canCapture(Minecraft client) {
		return client.level != null && client.gui.screen() == null;
	}

	private static String baseName(String pngFileName) {
		return pngFileName.endsWith(".png") ? pngFileName.substring(0, pngFileName.length() - 4) : pngFileName;
	}

	/**
	 * Just before the frame is presented, the same point the oversized path reads back from.
	 * Re-checks the gate on every call rather than only at {@link #request()}: a burst spans
	 * seconds, long enough for a screen to open or the world to go away in the middle, not just
	 * between one request and the next frame the way {@code BigScreenshot} has to guard against.
	 */
	public static void beforeBlit() {
		if (state != State.RUNNING) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (!canCapture(client)) {
			abort("a screen opened or the world went away mid-burst");
			return;
		}

		long now = Util.getMillis();
		int slot = dueSlot(now);
		if (slot == DONE) {
			finishBurst();
			return;
		}
		if (slot == WAIT) {
			return;
		}
		if (!ScreenshotHandler.beginSave(MAX_IN_FLIGHT)) {
			// Every in-flight slot is still encoding. Retried on the next blit; if this slot's
			// time passes first, dueSlot() moves on to the next one and this one is skipped.
			return;
		}

		int index = claimFrame(slot);
		File target = names.get(index);
		BurstFrame frame = new BurstFrame(groupId, index, offsetAt(now));
		ScreenshotHandler.captureBurstFrame(client.gameRenderer.mainRenderTarget(), target, frame);
	}

	/**
	 * Called on the render thread — directly from {@link #beforeBlit()} when a readback itself
	 * throws before any consumer exists, or via {@code client.execute} from {@link
	 * ScreenshotHandler#write}, which runs on {@code Util.ioPool()} — once for every frame of
	 * the burst that finishes, success or failure alike.
	 *
	 * <p>A burst spans seconds, with several frames encoding concurrently (up to
	 * {@link #MAX_IN_FLIGHT} at once), so the frame that happens to finish writing first —
	 * usually whichever is smallest, or whichever the IO pool got to first — is not reliably the
	 * last one issued. Announcing off the key frame's own completion, the way a single-frame
	 * capture would, lands the "saved" notice somewhere in the middle of the burst instead
	 * of after it. Waiting for every issued frame to report in is what fixes that.
	 */
	static void frameCompleted(boolean succeeded) {
		completedFrames++;
		if (succeeded) {
			succeededFrames++;
		}
		if (state != State.RUNNING) {
			maybeAnnounceCompletion();
		}
	}

	/**
	 * Whether the burst has stopped issuing (by finishing normally or by {@link #abort}) is not
	 * the same moment every issued frame has finished writing — either can come first. This is
	 * called from both directions: from {@link #frameCompleted} once the state has already left
	 * {@code RUNNING}, and from {@link #finishBurst}/{@link #abort} in case every frame had
	 * already reported in before the state itself caught up.
	 */
	private static void maybeAnnounceCompletion() {
		if (completedFrames >= nextIndex) {
			announceCompletion();
		}
	}

	/**
	 * {@code nextIndex} is how many frames actually got issued — every slot for a burst that kept
	 * up, fewer for one that skipped slots or was {@link #abort}ed partway through — so the
	 * count reported here is always what really landed, not what was originally planned.
	 */
	private static void announceCompletion() {
		if (names == null || names.isEmpty() || nextIndex <= 0) {
			return;
		}
		if (succeededFrames > 0) {
			CaptureStatus.post(CaptureStatus.Kind.SAVED, "voxelcam.burst.saved", succeededFrames, names.get(0).getName());
		} else {
			CaptureStatus.post(CaptureStatus.Kind.FAILED, "voxelcam.burst.failed");
		}
	}

	/**
	 * Drops back to idle without issuing anything further. Frames already issued still land as
	 * finished files — each has already claimed its own slot and will balance it on its own — so
	 * nothing already written is undone.
	 */
	static void abort(String reason) {
		VoxelCamClient.LOGGER.warn("Burst capture stopped: {}", reason);
		state = State.IDLE;
		maybeAnnounceCompletion();
	}

	// --- pure seams, unit-testable with no client ------------------------------------------

	/** {@link #dueSlot}: no slot is due yet on this blit. */
	static final int WAIT = -1;
	/** {@link #dueSlot}: the burst's time is up. */
	static final int DONE = -2;

	static void beginBurst(int slots, long burstStartMillis) {
		state = State.RUNNING;
		nextIndex = 0;
		nextSlot = 0;
		startMillis = burstStartMillis;
		runningSlots = slots;
		completedFrames = 0;
		succeededFrames = 0;
	}

	/**
	 * The schedule slot a frame taken at {@code nowMillis} would fill: the latest one whose time
	 * has come, so a slot already passed is skipped rather than issued late. {@link #WAIT} if
	 * that slot has already been taken, {@link #DONE} once the burst's whole length has passed.
	 */
	static int dueSlot(long nowMillis) {
		int slot = (int) Math.max(0, (nowMillis - startMillis) / FRAME_INTERVAL_MILLIS);
		if (slot >= runningSlots) {
			return DONE;
		}
		return slot >= nextSlot ? slot : WAIT;
	}

	/** Takes {@code slot} and returns the 0-based index of the frame that fills it. */
	static int claimFrame(int slot) {
		nextSlot = slot + 1;
		return nextIndex++;
	}

	static int offsetAt(long nowMillis) {
		return (int) (nowMillis - startMillis);
	}

	static void finishBurst() {
		state = State.IDLE;
		maybeAnnounceCompletion();
	}

	/** Test-only reset. */
	static void forgetBurst() {
		state = State.IDLE;
		names = null;
		groupId = null;
		nextIndex = 0;
		nextSlot = 0;
		completedFrames = 0;
		succeededFrames = 0;
	}
}
