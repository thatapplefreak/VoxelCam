package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.util.ChatMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.io.File;
import java.util.List;

/**
 * A run of plain-size captures fired across consecutive frames, sharing one group id so the
 * manager can list the first as a key and fold the rest under it (see {@link BurstFrame},
 * {@code VoxelCamIO}'s collapse).
 *
 * <p>The capture spans as many frames as the in-flight cap lets it, rather than a fixed
 * schedule:
 * <ol>
 *   <li>{@link #request()} arms it (from the capture menu) and reserves every frame's name up
 *       front, synchronously, so no two overlapping captures can ever resolve the same one.</li>
 *   <li>{@link #beforeBlit()} issues one frame per call, throttled by {@link
 *       ScreenshotHandler#beginSave} rather than by a timer — the in-flight cap {@code
 *       MAX_IN_FLIGHT} <em>is</em> the schedule, since each in-flight frame is a full-res {@code
 *       NativeImage} held until {@code Util.ioPool()} finishes encoding it.</li>
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

	/** Also the bound a burst-frames filmstrip caps its thumbnails at. */
	public static final int MAX_LENGTH = 20;
	public static final int DEFAULT_LENGTH = 8;

	/** Frames a burst may sit stalled between issues before giving up rather than hanging the
	 * capture key forever if the IO pool never drains. */
	private static final int STALL_FRAMES = 120;

	/** Session-only, like {@link BigScreenshot#getSize()}. What the <em>next</em> burst will
	 * run at; not touched once a burst is running. */
	private static volatile int length = DEFAULT_LENGTH;

	// Render-thread state only: request() runs from the capture menu's fire(), the rest from
	// beforeBlit(); frameCompleted() arrives from ScreenshotHandler.write() (on Util.ioPool())
	// only via client.execute, which is what keeps it render-thread-only too.
	private static State state = State.IDLE;
	private static List<File> names;
	private static String groupId;
	private static int nextIndex;
	/** The length the running burst was armed with — a separate field from {@link #length} so a
	 * unit test driving {@link #beginBurst} directly cannot change the session preference as a
	 * side effect. */
	private static int runningLength;
	private static int framesSinceIssue;
	private static long startMillis;
	/** How many issued frames have finished writing, success or failure alike. Compared against
	 * {@link #nextIndex} (which stops changing once the burst leaves {@code RUNNING}, whether by
	 * finishing normally or by {@link #abort}) to know when nothing is left outstanding. */
	private static int completedFrames;
	private static int succeededFrames;

	private Burst() {
	}

	public static int getLength() {
		return length;
	}

	public static void setLength(int newLength) {
		length = Math.clamp(newLength, 1, MAX_LENGTH);
	}

	public static boolean isBusy() {
		return state != State.IDLE;
	}

	/** Arms a burst for the next blit, explaining itself if it cannot. */
	public static void request() {
		if (state != State.IDLE || BigScreenshot.isBusy() || ScreenshotHandler.isSaving()) {
			ChatMessages.send("voxelcam.savingpleasewait");
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (!canCapture(client)) {
			ChatMessages.send("voxelcam.burst.unavailable");
			return;
		}

		try {
			File screenshotsDir = new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR);
			if (!screenshotsDir.exists()) {
				screenshotsDir.mkdirs();
			}
			names = ScreenshotNamer.reserveBurstNames(screenshotsDir, length);
			groupId = baseName(names.get(0).getName());
			beginBurst(length, Util.getMillis());
		} catch (Throwable t) {
			VoxelCamClient.LOGGER.error("Failed to reserve names for a burst capture", t);
			ChatMessages.send("voxelcam.burst.failed");
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
	 * roughly a second of frames, long enough for a screen to open or the world to go away in the
	 * middle, not just between one request and the next frame the way {@code BigScreenshot} has
	 * to guard against.
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
		if (++framesSinceIssue > STALL_FRAMES) {
			abort("the IO pool never drained");
			return;
		}
		if (!ScreenshotHandler.beginSave(MAX_IN_FLIGHT)) {
			// Throttled, not dropped: the cap is the schedule, so this frame is simply retried
			// on the next blit once a write finishes and frees a slot.
			return;
		}

		int index = claimFrame();
		if (index < 0) {
			ScreenshotHandler.endSave();
			finishBurst();
			return;
		}

		framesSinceIssue = 0;
		File target = names.get(index);
		BurstFrame frame = new BurstFrame(groupId, index, offsetAt(Util.getMillis()));
		ScreenshotHandler.captureBurstFrame(client.gameRenderer.mainRenderTarget(), target, frame);
	}

	/**
	 * Called on the render thread — directly from {@link #beforeBlit()} when a readback itself
	 * throws before any consumer exists, or via {@code client.execute} from {@link
	 * ScreenshotHandler#write}, which runs on {@code Util.ioPool()} — once for every frame of
	 * the burst that finishes, success or failure alike.
	 *
	 * <p>A burst spans roughly a second across several frames encoding concurrently (up to
	 * {@link #MAX_IN_FLIGHT} at once), so the frame that happens to finish writing first —
	 * usually whichever is smallest, or whichever the IO pool got to first — is not reliably the
	 * last one issued. Announcing off the key frame's own completion, the way a single-frame
	 * capture would, lands the "saved" chat message somewhere in the middle of the burst instead
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
	 * {@code nextIndex} is how many frames actually got issued — {@code runningLength} for a
	 * burst that ran to completion, fewer for one {@link #abort}ed partway through — so the
	 * count reported here is always what really landed, not what was originally planned.
	 */
	private static void announceCompletion() {
		if (names == null || names.isEmpty() || nextIndex <= 0) {
			return;
		}
		if (succeededFrames > 0) {
			ChatMessages.send("voxelcam.burst.saved", succeededFrames, names.get(0).getName());
		} else {
			ChatMessages.send("voxelcam.burst.failed");
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

	static void beginBurst(int burstLength, long burstStartMillis) {
		state = State.RUNNING;
		nextIndex = 0;
		framesSinceIssue = 0;
		startMillis = burstStartMillis;
		runningLength = burstLength;
		completedFrames = 0;
		succeededFrames = 0;
	}

	/** The next 0-based frame index to issue, or -1 once every frame has been claimed. */
	static int claimFrame() {
		if (nextIndex >= runningLength) {
			return -1;
		}
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
		framesSinceIssue = 0;
		completedFrames = 0;
		succeededFrames = 0;
	}
}
