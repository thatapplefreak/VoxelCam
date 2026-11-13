package com.thatapplefreak.voxelcam.client.screenshot;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Every piece of capture feedback VoxelCam gives — in progress, saved, failed, refused — and the
 * one rule that keeps it out of the frames it reports on. {@link CaptureStatusHud} draws what this
 * says; this class holds no Minecraft rendering types, so what it decides is unit-testable with no
 * client.
 *
 * <p>This replaced chat as the feedback channel in 2.5.0. Chat said nothing at all with no player,
 * which is the normal case from the title screen, and every new message used to need a decision
 * about which of two channels it went to. The one thing chat had that this does not is
 * scroll-back: a notice cannot be re-read once it fades, which is why one carrying a file name is
 * held longer than a plain flash.
 *
 * <h2>Two kinds of thing are shown</h2>
 * <ul>
 *   <li><b>Notices</b>, {@link #post posted} by a call site at the moment something happened. A
 *       completion cannot be polled for: {@code ScreenshotHandler}'s counter drops to zero on {@code
 *       Util.ioPool()} between two frames, and a save that starts and finishes inside one frame
 *       interval is never observed by polling at all.</li>
 *   <li>The <b>ambient busy state</b>, derived every frame rather than posted, because "a save is
 *       still running" has no single moment to announce. See {@link #busyAt} and {@link
 *       #showBusyAt}.</li>
 * </ul>
 *
 * <h2>The HUD must never be in a capture</h2>
 * HUD elements are extracted <em>before</em> the frame is drawn, and every capture path reads the
 * frame back after it is drawn, just before it is presented. So a notice on screen when a frame is
 * read back is burnt into the PNG — every burst frame after the first, the oversized frame, and a
 * "saved as" from the previous shot landing in the next. {@link #beforeFrame()} answers, at the head
 * of each frame, whether this frame <em>will</em> be read back, and the HUD stays blank for exactly
 * those frames. It is the same shape as {@code CaptureMenu}'s {@code pendingFrameStarted}, which is
 * how the dial already keeps itself out of the screenshot it fires.
 */
public final class CaptureStatus {

	public enum Kind {
		/** A capture was refused because another is still running. */
		BUSY,
		SAVED,
		FAILED,
		/** A capture was refused for a reason the player can act on. */
		REFUSED
	}

	/**
	 * One posted message: a translation key and its arguments rather than a built {@code Component},
	 * so the queue's rules can be exercised in plain JUnit, and two posts of the same message can be
	 * recognised as the same message.
	 */
	public record Notice(Kind kind, String key, List<Object> args, long postedAtMillis) {

		public Notice {
			Objects.requireNonNull(kind);
			Objects.requireNonNull(key);
			args = List.copyOf(args);
		}

		public Component text() {
			return Component.translatable(key, args.toArray());
		}

		/** How long this notice stays up, from the moment it was posted. */
		long holdMillis() {
			return switch (kind) {
				case BUSY -> HOLD_MILLIS;
				// A file name is the one thing a player might want to read back, and chat used to
				// let them; holding it longer is the stand-in for scroll-back.
				case SAVED -> args.isEmpty() ? HOLD_MILLIS : HOLD_NAMED_MILLIS;
				case REFUSED -> HOLD_NAMED_MILLIS;
				case FAILED -> HOLD_ERROR_MILLIS;
			};
		}

		boolean visibleAt(long nowMillis) {
			long age = nowMillis - postedAtMillis;
			return age >= 0 && age < holdMillis();
		}

		/** 1 for most of the hold, easing to 0 across the last {@link #FADE_MILLIS}. */
		public float opacityAt(long nowMillis) {
			long remaining = postedAtMillis + holdMillis() - nowMillis;
			if (remaining <= 0) {
				return 0F;
			}
			return remaining >= FADE_MILLIS ? 1F : remaining / (float) FADE_MILLIS;
		}

		private boolean sameMessageAs(Notice other) {
			return kind == other.kind && key.equals(other.key) && args.equals(other.args);
		}
	}

	/** A plain flash, and a busy refusal: long enough to notice, nothing to read twice. */
	static final long HOLD_MILLIS = 1500L;
	/** One carrying a file name or a reason. */
	static final long HOLD_NAMED_MILLIS = 4000L;
	/** A failure is held longest: it is the one the player most needs to have seen. */
	static final long HOLD_ERROR_MILLIS = 6000L;
	static final long FADE_MILLIS = 300L;

	/** The most notices on screen at once, newest first. */
	static final int MAX_VISIBLE = 3;

	/**
	 * The shortest the busy line stays up once it appears. A plain screenshot encodes in well under
	 * this, and shown for only as long as the save really ran it would flicker for a frame or two.
	 * Cut short by a result: once the save's own "saved as" or failure is posted, the line has
	 * nothing left to say.
	 */
	static final long BUSY_MIN_SHOW_MILLIS = 500L;

	/** One step of the busy spinner; {@link #SPINNER_STEPS} of them make a turn. */
	static final long SPINNER_STEP_MILLIS = 100L;
	static final int SPINNER_STEPS = 8;

	/**
	 * The longest the busy state is shown for one unbroken spell of busy. An oversized readback
	 * has no frame budget by design — only its consumer knows the GPU is done — so a consumer that
	 * never arrives leaves {@code BigScreenshot.isBusy()} true for the rest of the session. Bounding
	 * that <em>state</em> would break the readback invariant; bounding its display does not.
	 */
	static final long BUSY_MAX_MILLIS = 60_000L;

	static final long NOT_BUSY = Long.MIN_VALUE;

	// Posted from the render thread in practice — the IO-pool paths bounce through client.execute
	// first — but synchronised anyway: a notice is too cheap to be worth a threading rule.
	private static final Deque<Notice> notices = new ArrayDeque<>();

	// Render-thread state only, written by beforeFrame() and read by the HUD in the same frame.
	private static boolean frameIsCaptured;
	private static long busySinceMillis = NOT_BUSY;
	private static long busyEndedMillis = NOT_BUSY;
	private static long lastPostedMillis = NOT_BUSY;
	private static boolean busy;

	private CaptureStatus() {
	}

	/** Posts a notice for the HUD, the replacement for what used to be a chat line. */
	public static void post(Kind kind, String key, Object... args) {
		post(new Notice(kind, key, Arrays.asList(args), Util.getMillis()));
	}

	/**
	 * Posting a message that is already up replaces it rather than stacking a second copy: holding
	 * the key against a busy capture would otherwise fill the corner with the same refusal.
	 */
	static synchronized void post(Notice notice) {
		notices.removeIf(notice::sameMessageAs);
		notices.addFirst(notice);
		lastPostedMillis = notice.postedAtMillis();
		// Expired entries are pruned on read; this only bounds a queue nobody is reading, such as
		// one posted into with the HUD switched off.
		while (notices.size() > MAX_VISIBLE * 4) {
			notices.removeLast();
		}
	}

	/** What should be on screen right now, newest first. Pure, apart from pruning what has expired. */
	public static synchronized List<Notice> visibleAt(long nowMillis) {
		List<Notice> visible = new ArrayList<>(MAX_VISIBLE);
		for (Iterator<Notice> it = notices.iterator(); it.hasNext(); ) {
			Notice notice = it.next();
			if (!notice.visibleAt(nowMillis)) {
				// The deque is newest-first, but holds differ by kind, so an expired notice can sit
				// in front of a live one; remove it, and keep looking.
				if (nowMillis - notice.postedAtMillis() >= 0) {
					it.remove();
				}
				continue;
			}
			if (visible.size() < MAX_VISIBLE) {
				visible.add(notice);
			}
		}
		return visible;
	}

	/**
	 * Whether any capture is still running. One state, not one per source, so that an oversized
	 * capture — which both joins the save counter and is {@code BigScreenshot.isBusy()} — reads as
	 * one thing rather than two.
	 *
	 * <p>A burst is read from its own state and never from the counter alone: the counter oscillates
	 * as frames finish and are issued, and between two of them it can touch zero mid-burst.
	 */
	static boolean busyAt(int savesInFlight, boolean bigScreenshotBusy, boolean burstBusy) {
		return savesInFlight > 0 || bigScreenshotBusy || burstBusy;
	}

	/**
	 * Whether the busy line should be showing at {@code now}, for a spell of busy that began at
	 * {@code busySince} and ended at {@code busyEnded} ({@link #NOT_BUSY} while it is still going).
	 * Shown from the first frame of a save; held for {@link #BUSY_MIN_SHOW_MILLIS} past a save that
	 * finished sooner, unless a notice has been posted since the save began — that notice is the
	 * answer the line was standing in for.
	 */
	static boolean showBusyAt(long busySince, long busyEnded, long lastPosted, long nowMillis) {
		if (busySince == NOT_BUSY) {
			return false;
		}
		long elapsed = nowMillis - busySince;
		if (busyEnded == NOT_BUSY) {
			return elapsed < BUSY_MAX_MILLIS;
		}
		if (lastPosted != NOT_BUSY && lastPosted >= busySince) {
			return false;
		}
		return elapsed < BUSY_MIN_SHOW_MILLIS;
	}

	/** Which of the spinner's {@link #SPINNER_STEPS} positions is lit at {@code now}. */
	public static int spinnerStepAt(long nowMillis) {
		return (int) Math.floorMod(nowMillis / SPINNER_STEP_MILLIS, (long) SPINNER_STEPS);
	}

	/**
	 * Whether this frame is about to be read back by any capture path — every one of which issues
	 * its readback from {@code beforeBlit}, after the HUD has already been extracted. Each term is
	 * that path's own "I will read this frame" condition, as it stands once every {@code
	 * beforeFrame} has run:
	 * <ul>
	 *   <li>an oversized capture that has resized and is waiting for this blit;</li>
	 *   <li>a capture-menu selection waiting for its first clean frame — whatever mode it fires, a
	 *       plain shot and a burst's key frame are both taken from this very blit. A screenshot-key
	 *       press that bypassed the menu is queued the same way, so it is covered here too;</li>
	 *   <li>a burst frame this frame's head settled on — only those, so the recording cue can
	 *       show on every frame in between;</li>
	 *   <li>an automatic moment, armed and waiting for the frame its gate accepts.</li>
	 * </ul>
	 * Over-hiding costs a frame of a notice; under-hiding puts it in a PNG. So each term errs wide.
	 */
	static boolean capturesFrame(boolean bigScreenshotCapturing, boolean menuCapturePending,
			boolean burstCapturing, boolean momentPending) {
		return bigScreenshotCapturing || menuCapturePending || burstCapturing || momentPending;
	}

	/**
	 * Head of {@code Minecraft.renderFrame}, after every capture path's own {@code beforeFrame} —
	 * {@code BigScreenshot} moves into its capturing state there, {@code CaptureMenu} marks the
	 * frame it will fire from, and {@code Burst} settles whether this frame is one of its slots.
	 */
	public static void beforeFrame() {
		frameIsCaptured = capturesFrame(BigScreenshot.isCapturing(), CaptureMenu.capturesThisFrame(),
				Burst.capturesThisFrame(), AutoCapture.isPending());

		long now = Util.getMillis();
		boolean nowBusy = busyAt(ScreenshotHandler.savesInFlight(), BigScreenshot.isBusy(), Burst.isBusy());
		if (nowBusy) {
			// A new save, or one starting again while the last one's line was still being held.
			if (busySinceMillis == NOT_BUSY || busyEndedMillis != NOT_BUSY) {
				busySinceMillis = now;
				busyEndedMillis = NOT_BUSY;
			}
		} else if (busySinceMillis != NOT_BUSY && busyEndedMillis == NOT_BUSY) {
			busyEndedMillis = now;
		}
		long lastPosted;
		synchronized (CaptureStatus.class) {
			lastPosted = lastPostedMillis;
		}
		busy = showBusyAt(busySinceMillis, busyEndedMillis, lastPosted, now);
		if (!busy && !nowBusy) {
			busySinceMillis = NOT_BUSY;
			busyEndedMillis = NOT_BUSY;
		}
	}

	/** Whether the HUD has to stay blank this frame, because the frame is going into a PNG. */
	public static boolean isFrameCaptured() {
		return frameIsCaptured;
	}

	/** Whether the ambient busy state is showing this frame. */
	public static boolean isBusyShowing() {
		return busy;
	}

	/** Test-only reset: every field here is static and outlives any one test. Public because the
	 * client game tests live in another package. */
	public static synchronized void forgetAll() {
		notices.clear();
		frameIsCaptured = false;
		busySinceMillis = NOT_BUSY;
		busyEndedMillis = NOT_BUSY;
		lastPostedMillis = NOT_BUSY;
		busy = false;
	}
}
