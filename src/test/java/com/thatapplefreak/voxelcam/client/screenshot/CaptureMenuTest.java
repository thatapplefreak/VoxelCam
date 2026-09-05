package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What's reachable here without a live client is the pure wedge/mode geometry, and the corners of
 * the state machine that never touch {@code Minecraft}: arming from idle, a duplicate key-down not
 * resetting an already-armed hold, and a sub-threshold tick never opening the menu. The busy-guard
 * refusal (it sends a chat message), crossing into {@code OPEN} (which checks the current screen
 * and grabs the cursor), and firing either mode (which resolves a render target or touches the
 * window) all need a real client — that's the gametest suite's job, the same split
 * {@code BigScreenshotTest} draws around {@code request()}/{@code beginCapture()}/{@code finish()}.
 */
class CaptureMenuTest {

	/** The state machine is static, so a test that leaves it armed would follow the next one in. */
	@AfterEach
	void settle() {
		CaptureMenu.abort();
		CaptureMenu.discardPendingCapture();
	}

	// --- wedgeForAngle -------------------------------------------------------------------------

	@Test
	void straightUpSelectsWedgeZero() {
		assertEquals(0, CaptureMenu.wedgeForAngle(0, 2));
		assertEquals(0, CaptureMenu.wedgeForAngle(0, 3));
	}

	@Test
	void angleWrapsAroundAtTwoPi() {
		double twoPi = Math.PI * 2;
		assertEquals(CaptureMenu.wedgeForAngle(0.01, 4), CaptureMenu.wedgeForAngle(twoPi + 0.01, 4));
		assertEquals(CaptureMenu.wedgeForAngle(-0.01, 4), CaptureMenu.wedgeForAngle(twoPi - 0.01, 4));
	}

	@Test
	void twoWedgesSplitAtTheHorizon() {
		// Wedge 0 centred on "up" (0), wedge 1 centred on "down" (pi): the boundary sits at ±pi/2.
		assertEquals(0, CaptureMenu.wedgeForAngle(Math.PI / 2 - 0.01, 2));
		assertEquals(1, CaptureMenu.wedgeForAngle(Math.PI / 2 + 0.01, 2));
	}

	@Test
	void threeWedgesDivideEvenly() {
		double third = Math.PI * 2 / 3;
		assertEquals(0, CaptureMenu.wedgeForAngle(0, 3));
		assertEquals(1, CaptureMenu.wedgeForAngle(third, 3));
		assertEquals(2, CaptureMenu.wedgeForAngle(2 * third, 3));
	}

	// --- modeForOffset ---------------------------------------------------------------------------

	@Test
	void deadCentreDefaultsToTheFirstModeRatherThanThrowing() {
		assertEquals(CaptureMenu.Mode.SCREENSHOT, CaptureMenu.modeForOffset(0, 0));
	}

	@Test
	void offsetsResolveToTheGeometricallyCorrectWedgeForATwoModeMenu() {
		// Mouse above the anchor (screen-space y decreases upward) selects the first mode...
		assertEquals(CaptureMenu.Mode.SCREENSHOT, CaptureMenu.modeForOffset(0, -50));
		// ...below it selects the second.
		assertEquals(CaptureMenu.Mode.BIG_SCREENSHOT, CaptureMenu.modeForOffset(0, 50));
	}

	/**
	 * Freeing the cursor leaves it wherever it happens to sit. Without a dead zone a pixel of
	 * drift below the anchor already selects the wedge below, so a hold the player never aimed
	 * would fire whichever mode the cursor was nearest instead of an ordinary screenshot.
	 */
	@Test
	void aCursorThatBarelyDriftedOffCentreStillSelectsTheFirstMode() {
		assertEquals(CaptureMenu.Mode.SCREENSHOT, CaptureMenu.modeForOffset(0, 1));
		assertEquals(CaptureMenu.Mode.SCREENSHOT, CaptureMenu.modeForOffset(0, 12));
		assertEquals(CaptureMenu.Mode.SCREENSHOT, CaptureMenu.modeForOffset(-8, 8));
	}

	// --- state machine, no Minecraft ------------------------------------------------------------

	@Test
	void keyDownArmsFromIdle() {
		CaptureMenu.onKeyDown();

		assertTrue(CaptureMenu.isArmed(), "a fresh key-down should arm the menu");
		assertFalse(CaptureMenu.isOpen(), "arming alone must not open the menu yet");
	}

	// keyDown's busy-guard (BigScreenshot.isBusy() / ScreenshotHandler.isSaving()) sends a chat
	// message on refusal, which touches Minecraft.getInstance() — untestable here for the same
	// reason BigScreenshotTest never calls request() itself; covered by the gametest instead.

	@Test
	void aDuplicateKeyDownWhileAlreadyArmedIsANoOp() {
		CaptureMenu.onKeyDown();
		CaptureMenu.onKeyDown();

		assertTrue(CaptureMenu.isArmed(), "the menu should still be armed, not disarmed by the duplicate");
		assertFalse(CaptureMenu.isOpen());
	}

	@Test
	void subThresholdTicksNeverOpenTheMenu() {
		CaptureMenu.onKeyDown();
		for (int i = 0; i < 3; i++) {
			CaptureMenu.tick();
		}

		assertTrue(CaptureMenu.isArmed(), "still holding, under the threshold");
		assertFalse(CaptureMenu.isOpen(), "3 ticks must stay under the 4-tick hold threshold");
	}

	/**
	 * A tap has to reach the capture path at all: sampling the key's down-state once a tick misses
	 * a press that began and ended between two ticks, and since the binding shares F2 with
	 * vanilla's screenshot key, nothing else picks that press up either — it took the key.
	 */
	@Test
	void aTapOwesACaptureRatherThanTakingNone() {
		CaptureMenu.onKeyDown();
		CaptureMenu.onKeyUp();

		assertFalse(CaptureMenu.isArmed(), "the tap is over");
		assertTrue(CaptureMenu.isCapturePending(), "and it should have asked for a screenshot");
	}

	/**
	 * The frame still in the framebuffer when the key comes up is the one the menu was drawn on.
	 * Capturing it is how the menu ends up in the screenshot, so a queued capture has to sit out
	 * every blit until a frame has begun with the menu already gone.
	 */
	@Test
	void aQueuedCaptureSitsOutTheFrameTheMenuWasStillIn() {
		CaptureMenu.onKeyDown();
		CaptureMenu.onKeyUp();

		CaptureMenu.beforeBlit();

		assertTrue(CaptureMenu.isCapturePending(),
				"the blit of the frame the menu was drawn on must not be the one captured");
	}

	@Test
	void abortFromArmedReturnsToIdleWithoutTouchingTheClient() {
		CaptureMenu.onKeyDown();

		CaptureMenu.abort();

		assertFalse(CaptureMenu.isArmed());
		assertFalse(CaptureMenu.isOpen());
	}

	@Test
	void abortFromIdleIsANoOp() {
		CaptureMenu.abort();

		assertFalse(CaptureMenu.isArmed());
	}

	@Test
	void aStrayKeyUpWithNothingArmedDoesNotThrow() {
		CaptureMenu.onKeyUp();

		assertFalse(CaptureMenu.isArmed());
	}
}
