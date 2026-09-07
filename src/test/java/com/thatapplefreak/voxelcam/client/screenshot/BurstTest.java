package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The capture itself needs a live client — {@code beforeBlit()} reads {@code
 * Minecraft.getInstance()} on its very first line — and is covered by the gametest suite. What is
 * reachable here is the pure bookkeeping {@code beforeBlit()} is built on: how many frames a burst
 * hands out and when it considers itself done, none of which touches {@code Minecraft} at all.
 */
class BurstTest {

	private static final int ORIGINAL_LENGTH = Burst.getLength();

	/** The state machine is static, so a test that leaves one running would follow the next one
	 * in; {@link Burst#setLength} is restored for the same reason a leaked locale is in {@code
	 * ScreenshotNamerTest} — a later test asking for the session default should get it. */
	@AfterEach
	void settle() {
		Burst.forgetBurst();
		Burst.setLength(ORIGINAL_LENGTH);
	}

	@Test
	void aBurstIssuesExactlyItsLength() {
		Burst.beginBurst(5, 1000L);

		for (int expected = 0; expected < 5; expected++) {
			assertEquals(expected, Burst.claimFrame(), "frame " + expected + " should have been the next one issued");
		}
		assertEquals(-1, Burst.claimFrame(), "a sixth claim past a five-frame burst must signal there is nothing left");
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
	 * second burst in the same session must start counting frames from zero again. */
	@Test
	void aFreshBurstStartsItsIndexAtZero() {
		Burst.beginBurst(4, 500L);
		Burst.claimFrame();
		Burst.claimFrame();

		Burst.beginBurst(4, 900L);

		assertEquals(0, Burst.claimFrame(), "re-arming must reset the index, not continue the last burst's count");
	}

	@Test
	void setLengthClampsToTheValidRange() {
		Burst.setLength(0);
		assertEquals(1, Burst.getLength(), "a burst of zero frames makes no sense; one is the floor");

		Burst.setLength(Burst.MAX_LENGTH + 50);
		assertEquals(Burst.MAX_LENGTH, Burst.getLength());
	}

	/** {@code beginBurst} takes an explicit length so a test can drive an arbitrary run without
	 * touching the session preference {@link Burst#setLength} controls. */
	@Test
	void beginBurstDoesNotChangeTheSessionLengthPreference() {
		Burst.setLength(6);

		Burst.beginBurst(2, 1000L);

		assertEquals(6, Burst.getLength(), "the running burst's own length must not overwrite the session default");
	}

	/**
	 * {@code frameCompleted} and the state leaving {@code RUNNING} can arrive in either order —
	 * every frame can finish writing before the burst stops issuing, or the burst can stop
	 * issuing before its last frame reports in — and this is what previously kept the "saved"
	 * announcement pinned to whichever ordering frame 0 happened to land in first, rather than
	 * the whole group actually finishing. Neither ordering has an observable effect without a
	 * client ({@code names} stays null outside {@link Burst#request}, which is what keeps {@code
	 * announceCompletion} from ever reaching {@code ChatMessages.send} here), so this is a smoke
	 * test for the bookkeeping rather than the message itself — the gametest suite covers the
	 * real timing end to end.
	 */
	@Test
	void completionCanBeReportedBeforeOrAfterTheStateLeavesRunning() {
		Burst.beginBurst(2, 1000L);
		Burst.claimFrame();
		Burst.claimFrame();
		Burst.frameCompleted(true);
		Burst.frameCompleted(true);
		Burst.finishBurst();
		assertFalse(Burst.isBusy());

		Burst.beginBurst(2, 2000L);
		Burst.claimFrame();
		Burst.claimFrame();
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
		Burst.claimFrame();
		Burst.claimFrame();
		Burst.frameCompleted(false);
		Burst.frameCompleted(true);
		Burst.finishBurst();
		assertFalse(Burst.isBusy());
	}
}
