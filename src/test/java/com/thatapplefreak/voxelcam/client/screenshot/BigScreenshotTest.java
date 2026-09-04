package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The capture itself needs a live client and is covered by the gametest suite. What is reachable
 * here is when the window is allowed to be put back: a readback that throws must leave the state
 * machine owing a restore rather than performing one, because at the blit injection vanilla is
 * already holding the main render target's colour texture view and resizing would close it
 * mid-present; and a readback still in flight must not be restored out from under either.
 *
 * <p>These run without a client because nothing on this path touches {@code Minecraft} — which is
 * the property under test as much as the states are; the moment the failure path resizes again,
 * these fail rather than merely disagreeing about an enum.
 */
class BigScreenshotTest {

	/**
	 * The state machine is static, so a test that leaves it armed would follow the next one in.
	 * A pending restore is the one state a single frame always clears, whatever preceded it.
	 */
	@AfterEach
	void settle() {
		BigScreenshot.deferRestore();
		BigScreenshot.beforeFrame();
	}

	@Test
	void aFailedReadbackOwesTheWindowARestoreInsteadOfResizingOnTheSpot() {
		BigScreenshot.deferRestore();

		assertTrue(BigScreenshot.isBusy(), "the window is still oversized until the restore has run");
	}

	@Test
	void theDeferredRestoreRunsAtTheHeadOfTheVeryNextFrame() {
		BigScreenshot.deferRestore();

		BigScreenshot.beforeFrame();

		assertFalse(BigScreenshot.isBusy(),
				"a pending restore must not wait out the stale-frame counter");
	}

	/**
	 * The stale-frame watchdog exists for a capture that never reached its blit. Applying it to an
	 * outstanding readback would resize the render target out from under the copy still sourcing
	 * from its colour texture, so the wait here is unbounded on purpose — a slow readback is
	 * ordinary on a large capture, not a stall.
	 */
	@Test
	void anOutstandingReadbackIsWaitedOutHoweverManyFramesItTakes() {
		BigScreenshot.beginReadback();

		for (int frame = 0; frame < 200; frame++) {
			BigScreenshot.beforeFrame();
		}

		assertTrue(BigScreenshot.isBusy(), "a readback in flight must never be force-restored");
	}

	@Test
	void theReadbackConsumerEndsTheCaptureItWasIssuedFor() {
		int issued = BigScreenshot.beginReadback();

		BigScreenshot.completeReadback(issued);

		assertFalse(BigScreenshot.isBusy(), "the consumer is what puts the window back");
	}

	/** A consumer that arrives after its own capture ended must not end whatever replaced it. */
	@Test
	void aLateReadbackConsumerLeavesANewerCaptureAlone() {
		int abandoned = BigScreenshot.beginReadback();
		int current = BigScreenshot.beginReadback();

		BigScreenshot.completeReadback(abandoned);

		assertTrue(BigScreenshot.isBusy(), "a stale consumer must not restore over a live capture");

		BigScreenshot.completeReadback(current);

		assertFalse(BigScreenshot.isBusy(), "the live capture's own consumer still ends it");
	}
}
