package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.util.Util;
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
		ScreenshotHandler.forgetSavesInFlight();
		CaptureStatus.forgetAll();
	}

	/**
	 * {@code beforeBlit} joins the save counter without a cap, on the strength of nothing else being
	 * in flight. A burst and a plain screenshot refuse while a save is running. A big screenshot has
	 * to as well, or it resizes the window alongside a save that is still encoding.
	 */
	@Test
	void aRequestIsRefusedWhileAnotherSaveIsInFlight() {
		assertTrue(ScreenshotHandler.beginSave(1));

		BigScreenshot.request();

		assertFalse(BigScreenshot.isBusy(), "a big screenshot must not start alongside a save in flight");
		List<CaptureStatus.Notice> notices = CaptureStatus.visibleAt(Util.getMillis());
		assertEquals(1, notices.size(), "the refusal should say why nothing happened");
		assertEquals(CaptureStatus.Kind.BUSY, notices.get(0).kind());
		assertEquals("voxelcam.savingpleasewait", notices.get(0).key());
	}

	// --- what the restore puts back ---------------------------------------------------------------

	/** The ordinary end of a capture: the window still has the size the capture gave it. */
	@Test
	void aWindowStillAtTheBigScreenshotSizeIsPutBack() {
		assertTrue(BigScreenshot.owesRestore(3840, 2160, 3840, 2160, 854, 480));
	}

	/**
	 * The player pressed F11 or dragged the window's edge while the capture was in progress. The
	 * platform has since written the real new size, and that is neither the size the capture
	 * applied nor the one it saved. Putting the saved size back over it leaves a fullscreen game
	 * rendering at the old windowed size until the next real resize.
	 */
	@Test
	void aWindowThePlatformResizedMeanwhileIsLeftAlone() {
		assertFalse(BigScreenshot.owesRestore(1920, 1080, 3840, 2160, 854, 480));
	}

	@Test
	void aWindowAlreadyAtItsSavedSizeNeedsNothing() {
		assertFalse(BigScreenshot.owesRestore(854, 480, 3840, 2160, 854, 480));
		assertFalse(BigScreenshot.owesRestore(854, 480, 854, 480, 854, 480),
				"a big screenshot size equal to the window resized nothing to begin with");
	}

	/** One matching dimension is not the applied size: a window the same height as the big
	 * screenshot but a different width was resized by something else. */
	@Test
	void bothDimensionsHaveToMatchTheAppliedSize() {
		assertFalse(BigScreenshot.owesRestore(1920, 2160, 3840, 2160, 854, 480));
		assertFalse(BigScreenshot.owesRestore(3840, 1080, 3840, 2160, 854, 480));
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
