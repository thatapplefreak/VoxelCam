package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The capture itself needs a live client — {@code beforeBlit()} reads {@code
 * Minecraft.getInstance()} on its very first line — and is covered by the gametest suite. What is
 * reachable here is the pure bookkeeping {@code beforeBlit()} is built on: which slot of the
 * 10-a-second schedule is due, when it considers itself done, none of which touches {@code
 * Minecraft} at all.
 */
class BurstTest {

	private static final int ORIGINAL_SECONDS = Burst.getSeconds();
	private static final int STEP = Burst.FRAME_INTERVAL_MILLIS;

	/** The state machine is static, so a test that leaves one running would follow the next one
	 * in; {@link Burst#setSeconds} is restored for the same reason a leaked locale is in {@code
	 * ScreenshotNamerTest} — a later test asking for the session default should get it. */
	@AfterEach
	void settle() {
		Burst.forgetBurst();
		Burst.setSeconds(ORIGINAL_SECONDS);
	}

	@Test
	void aBurstTakesOneFrameEveryTenthOfASecondUntilItsTimeIsUp() {
		Burst.beginBurst(5, 1000L);

		for (int slot = 0; slot < 5; slot++) {
			long due = 1000L + slot * STEP;
			assertEquals(slot, Burst.dueSlot(due), "slot " + slot + " is due at its own time");
			assertEquals(slot, Burst.claimFrame(Burst.dueSlot(due)));
			assertEquals(Burst.WAIT, Burst.dueSlot(due + STEP - 1), "nothing more until the next slot's time");
		}
		assertEquals(Burst.DONE, Burst.dueSlot(1000L + 5 * STEP), "five slots at ten a second is half a second");
	}

	@Test
	void aSlotMissedWhileTheCapIsFullIsSkippedNotCaughtUp() {
		Burst.beginBurst(10, 0L);
		assertEquals(0, Burst.claimFrame(Burst.dueSlot(0L)));

		// Slots 1 and 2 pass with every in-flight frame still encoding; the next blit that can
		// issue is in slot 3's time, and takes slot 3 — not slot 1, which would bunch the frames.
		long late = 3 * STEP + 40;
		assertEquals(3, Burst.dueSlot(late));
		assertEquals(1, Burst.claimFrame(Burst.dueSlot(late)), "the second frame written, whatever slot it filled");
		assertEquals(Burst.WAIT, Burst.dueSlot(late + 1));
		assertEquals(4, Burst.dueSlot(4 * STEP));
	}

	@Test
	void secondsBecomeFramesAtTenASecond() {
		assertEquals(10, Burst.FRAMES_PER_SECOND);
		assertEquals(30, Burst.framesFor(3));
		assertEquals(Burst.MAX_FRAMES, Burst.framesFor(Burst.MAX_SECONDS));
		assertTrue(Burst.MAX_FRAMES <= 99, "frame names keep two digits");
	}

	@Test
	void everyRingOptionIsALengthTheSettingAccepts() {
		for (int seconds : Burst.DIAL_OPTIONS) {
			Burst.setSeconds(seconds);
			assertEquals(seconds, Burst.getSeconds());
		}
	}

	@Test
	void isBusyIsTrueOnlyWhileRunning() {
		assertFalse(Burst.isBusy(), "idle before anything is requested");

		Burst.beginBurst(3, 1000L);
		assertTrue(Burst.isBusy());

		Burst.finishBurst();
		assertFalse(Burst.isBusy(), "finishing returns the state machine to idle");
	}

	@Test
	void offsetIsMeasuredFromTheBurstsOwnStart() {
		Burst.beginBurst(3, 1_000L);

		assertEquals(0, Burst.offsetAt(1_000L), "the key frame is offset zero from itself");
		assertEquals(420, Burst.offsetAt(1_420L));
	}

	/** Re-arming has to reset the counters, not carry them over from whatever ran before — a
	 * second burst in the same session must start counting frames and slots from zero again. */
	@Test
	void aFreshBurstStartsItsIndexAndScheduleAtZero() {
		Burst.beginBurst(4, 500L);
		Burst.claimFrame(Burst.dueSlot(500L));
		Burst.claimFrame(Burst.dueSlot(500L + STEP));

		Burst.beginBurst(4, 900L);

		assertEquals(0, Burst.dueSlot(900L), "re-arming must restart the schedule");
		assertEquals(0, Burst.claimFrame(0), "re-arming must reset the index, not continue the last burst's count");
	}

	@Test
	void setSecondsClampsToTheValidRange() {
		Burst.setSeconds(0);
		assertEquals(Burst.MIN_SECONDS, Burst.getSeconds(), "shorter than this cannot hold a moment from start to finish");

		Burst.setSeconds(Burst.MAX_SECONDS + 50);
		assertEquals(Burst.MAX_SECONDS, Burst.getSeconds());
	}

	/** {@code beginBurst} takes an explicit slot count so a test can drive an arbitrary run without
	 * touching the session preference {@link Burst#setSeconds} controls. */
	@Test
	void beginBurstDoesNotChangeTheSessionLengthPreference() {
		Burst.setSeconds(4);

		Burst.beginBurst(2, 1000L);

		assertEquals(4, Burst.getSeconds(), "the running burst's own length must not overwrite the session default");
	}

	/**
	 * {@code frameCompleted} and the state leaving {@code RUNNING} can arrive in either order —
	 * every frame can finish writing before the burst stops issuing, or the burst can stop
	 * issuing before its last frame reports in — and this is what previously kept the "saved"
	 * announcement pinned to whichever ordering frame 0 happened to land in first, rather than
	 * the whole group actually finishing. Neither ordering has an observable effect without a
	 * client ({@code names} stays null outside {@link Burst#request}, which is what keeps {@code
	 * announceCompletion} from ever posting a notice here), so this is a smoke
	 * test for the bookkeeping rather than the message itself — the gametest suite covers the
	 * real timing end to end.
	 */
	@Test
	void completionCanBeReportedBeforeOrAfterTheStateLeavesRunning() {
		Burst.beginBurst(2, 1000L);
		Burst.claimFrame(0);
		Burst.claimFrame(1);
		Burst.frameCompleted(true);
		Burst.frameCompleted(true);
		Burst.finishBurst();
		assertFalse(Burst.isBusy());

		Burst.beginBurst(2, 2000L);
		Burst.claimFrame(0);
		Burst.claimFrame(1);
		Burst.finishBurst();
		Burst.frameCompleted(true);
		Burst.frameCompleted(true);
		assertFalse(Burst.isBusy());
	}

	/** A readback failure mid-burst still has to report a completion of its own — see {@code
	 * ScreenshotHandler.captureBurstFrame}'s catch — or the group never finishes accounting for
	 * every frame it claimed and the completion check waits forever. */
	@Test
	void aFailedFrameStillCountsAsCompleted() {
		Burst.beginBurst(2, 1000L);
		Burst.claimFrame(0);
		Burst.claimFrame(1);
		Burst.frameCompleted(false);
		Burst.frameCompleted(true);
		Burst.finishBurst();
		assertFalse(Burst.isBusy());
	}
}
