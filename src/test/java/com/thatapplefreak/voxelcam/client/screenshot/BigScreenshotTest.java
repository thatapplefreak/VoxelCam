package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The capture itself needs a live client and is covered by the gametest suite. What is reachable
 * here is the failure hand-off: a readback that throws must leave the state machine owing a
 * restore rather than performing one, because at the blit injection vanilla is already holding the
 * main render target's colour texture view and resizing would close it mid-present.
 *
 * <p>These run without a client because nothing on this path touches {@code Minecraft} — which is
 * the property under test as much as the states are; the moment the failure path resizes again,
 * these fail rather than merely disagreeing about an enum.
 */
class BigScreenshotTest {

	/** The state machine is static, so a test that leaves it armed would follow the next one in. */
	@AfterEach
	void settle() {
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
}
