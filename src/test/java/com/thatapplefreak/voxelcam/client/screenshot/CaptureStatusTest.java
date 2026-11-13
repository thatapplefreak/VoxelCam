package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.screenshot.CaptureStatus.Kind;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureStatus.Notice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureStatusTest {

	@BeforeEach
	@AfterEach
	void reset() {
		CaptureStatus.forgetAll();
	}

	private static Notice notice(Kind kind, String key, long at, Object... args) {
		return new Notice(kind, key, List.of(args), at);
	}

	@Test
	void aNoticeExpiresAtExactlyItsHold() {
		CaptureStatus.post(notice(Kind.BUSY, "voxelcam.savingpleasewait", 1000L));

		assertEquals(1, CaptureStatus.visibleAt(1000L + CaptureStatus.HOLD_MILLIS - 1).size());
		assertTrue(CaptureStatus.visibleAt(1000L + CaptureStatus.HOLD_MILLIS).isEmpty());
	}

	@Test
	void aFileNameOutlivesAPlainFlash() {
		Notice plain = notice(Kind.SAVED, "voxelcam.saved", 0L);
		Notice named = notice(Kind.SAVED, "voxelcam.savedscreenshotas", 0L, "2026-11-12_10.00.00.png");

		assertTrue(named.holdMillis() > plain.holdMillis());
	}

	@Test
	void aFailureIsHeldLongerThanAnySuccess() {
		Notice saved = notice(Kind.SAVED, "voxelcam.savedscreenshotas", 0L, "a.png");
		Notice failed = notice(Kind.FAILED, "voxelcam.savefailed", 0L);

		assertTrue(failed.holdMillis() > saved.holdMillis());
		assertEquals(Kind.FAILED, failed.kind());
	}

	@Test
	void repostingTheSameMessageReplacesItRatherThanStacking() {
		CaptureStatus.post(notice(Kind.BUSY, "voxelcam.savingpleasewait", 0L));
		CaptureStatus.post(notice(Kind.BUSY, "voxelcam.savingpleasewait", 1000L));

		List<Notice> visible = CaptureStatus.visibleAt(1000L);
		assertEquals(1, visible.size());
		// ...and the timer restarted with it, so a player still pressing still sees it.
		assertEquals(1000L, visible.get(0).postedAtMillis());
	}

	@Test
	void differentArgumentsAreDifferentMessages() {
		CaptureStatus.post(notice(Kind.SAVED, "voxelcam.savedscreenshotas", 0L, "a.png"));
		CaptureStatus.post(notice(Kind.SAVED, "voxelcam.savedscreenshotas", 10L, "b.png"));

		assertEquals(2, CaptureStatus.visibleAt(20L).size());
	}

	@Test
	void onlyTheNewestFewAreShownNewestFirst() {
		for (int i = 0; i < CaptureStatus.MAX_VISIBLE + 2; i++) {
			CaptureStatus.post(notice(Kind.SAVED, "voxelcam.savedscreenshotas", i, i + ".png"));
		}

		List<Notice> visible = CaptureStatus.visibleAt(10L);
		assertEquals(CaptureStatus.MAX_VISIBLE, visible.size());
		assertEquals(List.of((Object) ((CaptureStatus.MAX_VISIBLE + 1) + ".png")), visible.get(0).args());
	}

	@Test
	void anExpiredNoticeInFrontDoesNotHideALiveOneBehindIt() {
		// The failure is older but held longer, so it outlives the newer flash in front of it.
		CaptureStatus.post(notice(Kind.FAILED, "voxelcam.savefailed", 0L));
		CaptureStatus.post(notice(Kind.BUSY, "voxelcam.savingpleasewait", 100L));

		List<Notice> visible = CaptureStatus.visibleAt(100L + CaptureStatus.HOLD_MILLIS);
		assertEquals(1, visible.size());
		assertEquals(Kind.FAILED, visible.get(0).kind());
	}

	@Test
	void aNoticeFadesOnlyAtTheEndOfItsHold() {
		Notice n = notice(Kind.BUSY, "voxelcam.savingpleasewait", 0L);

		assertEquals(1F, n.opacityAt(0L));
		assertEquals(1F, n.opacityAt(CaptureStatus.HOLD_MILLIS - CaptureStatus.FADE_MILLIS));
		float midFade = n.opacityAt(CaptureStatus.HOLD_MILLIS - CaptureStatus.FADE_MILLIS / 2);
		assertTrue(midFade > 0F && midFade < 1F, "was " + midFade);
		assertEquals(0F, n.opacityAt(CaptureStatus.HOLD_MILLIS));
	}

	@Test
	void aBurstStaysBusyWhileTheCounterOscillates() {
		// 1 -> 2 -> 3 -> 2 -> 0 -> 3: the counter touches zero between frames; the burst does not.
		for (int inFlight : new int[] { 1, 2, 3, 2, 0, 3 }) {
			assertTrue(CaptureStatus.busyAt(inFlight, false, true), "in flight " + inFlight);
		}
		assertFalse(CaptureStatus.busyAt(0, false, false));
	}

	@Test
	void aBigScreenshotStaysBusyUntilItsRestoreEvenWithNothingInFlight() {
		// After the readback consumer releases its slot the state machine can still be restoring.
		assertTrue(CaptureStatus.busyAt(0, true, false));
		assertTrue(CaptureStatus.busyAt(1, true, false));
	}

	private static final long NONE = CaptureStatus.NOT_BUSY;

	@Test
	void theBusyLineShowsFromTheFirstFrameOfASave() {
		assertTrue(CaptureStatus.showBusyAt(5000L, NONE, NONE, 5000L));
		assertFalse(CaptureStatus.showBusyAt(NONE, NONE, NONE, 5000L));
	}

	@Test
	void aSaveThatFinishesQuicklyIsStillShownLongEnoughToSee() {
		long since = 5000L;
		long ended = since + 60;
		assertTrue(CaptureStatus.showBusyAt(since, ended, NONE, since + CaptureStatus.BUSY_MIN_SHOW_MILLIS - 1));
		assertFalse(CaptureStatus.showBusyAt(since, ended, NONE, since + CaptureStatus.BUSY_MIN_SHOW_MILLIS));
	}

	@Test
	void theSavesOwnResultEndsTheHoldEarly() {
		long since = 5000L;
		long ended = since + 60;
		assertFalse(CaptureStatus.showBusyAt(since, ended, ended + 1, ended + 2));
		// A notice from before this save began is not its answer.
		assertTrue(CaptureStatus.showBusyAt(since, ended, since - 1, ended + 2));
	}

	@Test
	void aStillRunningSaveIsShownUntilTheCapThenGivenUpOn() {
		long since = 5000L;
		// Even with a notice posted meanwhile — a "please wait" from a second press is not a result.
		assertTrue(CaptureStatus.showBusyAt(since, NONE, since + 10, since + CaptureStatus.BUSY_MAX_MILLIS - 1));
		assertFalse(CaptureStatus.showBusyAt(since, NONE, NONE, since + CaptureStatus.BUSY_MAX_MILLIS));
	}

	@Test
	void theSpinnerStepsRoundAndWraps() {
		assertEquals(0, CaptureStatus.spinnerStepAt(0L));
		assertEquals(1, CaptureStatus.spinnerStepAt(CaptureStatus.SPINNER_STEP_MILLIS));
		assertEquals(0, CaptureStatus.spinnerStepAt(CaptureStatus.SPINNER_STEP_MILLIS * CaptureStatus.SPINNER_STEPS));
		for (long t = 0; t < 5000; t += 37) {
			int step = CaptureStatus.spinnerStepAt(t);
			assertTrue(step >= 0 && step < CaptureStatus.SPINNER_STEPS, "step " + step + " at " + t);
		}
	}

	@Test
	void anyPathAboutToReadTheFrameBackHidesTheHud() {
		assertFalse(CaptureStatus.capturesFrame(false, false, false, false));
		assertTrue(CaptureStatus.capturesFrame(true, false, false, false));
		assertTrue(CaptureStatus.capturesFrame(false, true, false, false));
		assertTrue(CaptureStatus.capturesFrame(false, false, true, false));
		assertTrue(CaptureStatus.capturesFrame(false, false, false, true));
	}
}
