package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The capture itself needs a live client — {@code beforeBlit()} reads {@code
 * Minecraft.getInstance()} and {@code tick()} reads a player — and is covered by the gametest
 * suite. What is reachable here is everything that decides <em>whether</em> to capture: the
 * cooldown, the gate, the pending countdown and the visited-dimension rule, none of which touches
 * {@code Minecraft} at all.
 */
class AutoCaptureTest {

	/** Well past any cooldown, so a refusal at this instant can only be the pending state or the
	 * trigger being off — never the window. */
	private static final long LONG_AFTER = 1_000_000L;

	@AfterEach
	void settle() {
		// All four are static and outlive a test: a pending capture would follow the next test in,
		// a config toggle would silence it, and a leaked save slot or an armed capture menu would
		// refuse every arm.
		AutoCapture.forgetAll();
		VoxelCamConfig.forgetCurrent();
		ScreenshotHandler.forgetSavesInFlight();
		CaptureMenu.abort();
		CaptureMenu.discardPendingCapture();
	}

	// --- the cooldown ------------------------------------------------------------------------

	/** The window is global rather than per trigger, and charged at arm time: one automatic capture
	 * per window, whatever caused it. A cascade of advancements is one moment to the player. */
	@Test
	void theFirstTriggerInAWindowWinsAndTheRestAreDropped() {
		assertTrue(AutoCapture.shouldArm(MomentTrigger.ADVANCEMENT, 0L));
		AutoCapture.arm(MomentTrigger.ADVANCEMENT, "Stone Age", 0L);
		AutoCapture.finishPending();

		long window = AutoCapture.cooldownSeconds() * 1000L;
		assertFalse(AutoCapture.shouldArm(MomentTrigger.ADVANCEMENT, window - 1),
				"a second advancement inside the window is the same moment, not a second screenshot");
		assertFalse(AutoCapture.shouldArm(MomentTrigger.BOSS_DEFEATED, window - 1),
				"the window is global: a different trigger inside it is still refused");
		assertTrue(AutoCapture.shouldArm(MomentTrigger.BOSS_DEFEATED, window),
				"once the window has elapsed the next moment is allowed through");
	}

	/** Charging the window at arm time is what makes the first trigger win; a capture that never
	 * actually happened has no business also suppressing the next one. */
	@Test
	void aDroppedCaptureHandsTheCooldownWindowBack() {
		AutoCapture.arm(MomentTrigger.ADVANCEMENT, "Stone Age", 0L);
		AutoCapture.dropPending();

		assertTrue(AutoCapture.shouldArm(MomentTrigger.BOSS_DEFEATED, 1L),
				"a moment that never became a screenshot must not spend the window");
	}

	@Test
	void refusesWhileAnotherCaptureIsAlreadyPending() {
		AutoCapture.arm(MomentTrigger.ADVANCEMENT, "Stone Age", 0L);

		assertFalse(AutoCapture.shouldArm(MomentTrigger.DEATH, LONG_AFTER),
				"one pending capture at a time, however long ago the window opened");
	}

	@Test
	void aDisabledTriggerNeverArmsAndDoesNotSilenceTheOthers() {
		VoxelCamConfig.current().autoCaptureDeath = false;

		assertFalse(AutoCapture.shouldArm(MomentTrigger.DEATH, 0L));
		assertTrue(AutoCapture.shouldArm(MomentTrigger.ADVANCEMENT, 0L),
				"switching one trigger off must leave the rest alone");
	}

	/** A deliberate capture the player asked for outranks an automatic one: the automatic one is
	 * simply dropped rather than queued behind it. */
	@Test
	void refusesWhileADeliberateCaptureIsInFlight() {
		assertTrue(ScreenshotHandler.beginSave(1));

		assertFalse(AutoCapture.shouldArm(MomentTrigger.ADVANCEMENT, 0L));

		ScreenshotHandler.endSave();
		assertTrue(AutoCapture.shouldArm(MomentTrigger.ADVANCEMENT, 0L),
				"once the deliberate capture has finished, automatic ones are allowed again");
	}

	/**
	 * A capture menu the player is holding is a capture they are about to take themselves. A moment
	 * armed under it would be read back with the dial drawn across it, and its save could still be
	 * in flight when the key comes up, refusing the screenshot the player was aiming.
	 */
	@Test
	void refusesWhileTheCaptureMenuIsArmed() {
		CaptureMenu.onKeyDown();

		assertFalse(AutoCapture.shouldArm(MomentTrigger.ADVANCEMENT, 0L),
				"a moment must not arm while the capture key is held");

		CaptureMenu.abort();
		assertTrue(AutoCapture.shouldArm(MomentTrigger.ADVANCEMENT, 0L),
				"once the capture menu is put away, moments are allowed again");
	}

	/** A hand-edited zero would turn an advancement cascade into a screenshot per toast, which is
	 * the failure the window exists to prevent — so the file is not trusted, it is clamped. */
	@Test
	void theCooldownIsClampedOnRead() {
		VoxelCamConfig.current().autoCaptureCooldownSeconds = 0;
		assertEquals(AutoCapture.MIN_COOLDOWN_SECONDS, AutoCapture.cooldownSeconds());

		VoxelCamConfig.current().autoCaptureCooldownSeconds = 9999;
		assertEquals(AutoCapture.MAX_COOLDOWN_SECONDS, AutoCapture.cooldownSeconds());
	}

	// --- the gate ----------------------------------------------------------------------------

	@Test
	void anOpenFrameIsTheMomentForEveryTriggerButDeath() {
		assertEquals(AutoCapture.Gate.CAPTURE,
				AutoCapture.gateFor(MomentTrigger.ADVANCEMENT, true, true, false, AutoCapture.ScreenKind.NONE));
		assertEquals(AutoCapture.Gate.CAPTURE,
				AutoCapture.gateFor(MomentTrigger.NEW_DIMENSION, true, true, false, AutoCapture.ScreenKind.NONE));
	}

	/**
	 * The one row that inverts, and the reason it does: health crossing zero arrives a variable
	 * number of ticks before {@code DeathScreen}, which comes from a server-driven packet. Shooting
	 * on the first open frame would give a clean shot or the red-overlay shot depending on latency —
	 * the same trigger, two different screenshots. Waiting for the screen makes it one.
	 */
	@Test
	void deathWaitsForItsScreenAndThenCaptures() {
		assertEquals(AutoCapture.Gate.WAIT,
				AutoCapture.gateFor(MomentTrigger.DEATH, true, true, false, AutoCapture.ScreenKind.NONE),
				"an open frame is not yet the death shot — the screen has not arrived");
		assertEquals(AutoCapture.Gate.CAPTURE,
				AutoCapture.gateFor(MomentTrigger.DEATH, true, true, false, AutoCapture.ScreenKind.DEATH));
	}

	@Test
	void aDeathScreenIsNotAnyOtherTriggersMoment() {
		assertEquals(AutoCapture.Gate.DROP,
				AutoCapture.gateFor(MomentTrigger.ADVANCEMENT, true, true, false, AutoCapture.ScreenKind.DEATH));
	}

	/** Held rather than dropped, this would shoot whatever the player was looking at when they
	 * closed the screen — long after the moment. */
	@Test
	void aScreenThePlayerOpenedDropsTheMoment() {
		for (MomentTrigger trigger : MomentTrigger.values()) {
			assertEquals(AutoCapture.Gate.DROP,
					AutoCapture.gateFor(trigger, true, true, false, AutoCapture.ScreenKind.OTHER),
					trigger + " must not wait out a screen the player opened");
		}
	}

	/**
	 * The capture menu is not a screen, so the screen rows alone read a held menu as a clear view.
	 * It is the player's own doing all the same, and it can be armed after the moment was: a moment
	 * waiting out a level load, or a death waiting for its screen. Dropped, not held, in every row
	 * that would otherwise capture or wait.
	 */
	@Test
	void anArmedCaptureMenuDropsTheMoment() {
		for (MomentTrigger trigger : MomentTrigger.values()) {
			for (AutoCapture.ScreenKind screen : AutoCapture.ScreenKind.values()) {
				assertEquals(AutoCapture.Gate.DROP,
						AutoCapture.gateFor(trigger, true, true, true, screen),
						trigger + " must not be captured or held under an armed capture menu (" + screen + ")");
			}
		}
	}

	/** The opposite case: this one closes on its own, and the moment is on the other side of it. */
	@Test
	void aLevelLoadIsWaitedOutRatherThanDropped() {
		assertEquals(AutoCapture.Gate.WAIT,
				AutoCapture.gateFor(MomentTrigger.NEW_DIMENSION, true, true, false, AutoCapture.ScreenKind.LEVEL_LOADING));
	}

	/**
	 * The first frame after the loading screen closes is fog, or the portal overlay, because vanilla
	 * only waits for the player's own section. That clears on its own too, so whatever trigger was
	 * armed for the arrival waits: the advancement for entering a dimension arrives with it and
	 * usually wins the cooldown, and is as much a picture of the arrival as the new-dimension moment.
	 */
	@Test
	void aWorldStillComingIntoViewIsWaitedOutByEveryTrigger() {
		for (MomentTrigger trigger : MomentTrigger.values()) {
			assertEquals(AutoCapture.Gate.WAIT,
					AutoCapture.gateFor(trigger, true, true, false, AutoCapture.ScreenKind.ARRIVING),
					trigger + " must not shoot an arrival before the world is drawn");
		}
	}

	/** Scoped to the arrival: a moment armed outside one, or once it is in view, sees the open
	 * frame it always did. Joining a world is not an arrival either, which is the caller's side. */
	@Test
	void onlyAMomentArmedForAnArrivalWaitsForIt() {
		assertEquals(AutoCapture.ScreenKind.ARRIVING,
				AutoCapture.arrivingKind(AutoCapture.ScreenKind.NONE, true, true));
		assertEquals(AutoCapture.ScreenKind.NONE,
				AutoCapture.arrivingKind(AutoCapture.ScreenKind.NONE, false, true),
				"a moment armed after the arrival came into view, or outside one, is taken as before");
		assertEquals(AutoCapture.ScreenKind.NONE,
				AutoCapture.arrivingKind(AutoCapture.ScreenKind.NONE, true, false),
				"once the arrival is in view, its moment is taken");
		for (AutoCapture.ScreenKind screen : AutoCapture.ScreenKind.values()) {
			if (screen != AutoCapture.ScreenKind.NONE) {
				assertEquals(screen, AutoCapture.arrivingKind(screen, true, true),
						"a screen still decides for itself: " + screen);
			}
		}
	}

	@Test
	void nothingSurvivesLosingTheWorldOrThePlayer() {
		assertEquals(AutoCapture.Gate.DROP,
				AutoCapture.gateFor(MomentTrigger.ADVANCEMENT, false, true, false, AutoCapture.ScreenKind.NONE));
		assertEquals(AutoCapture.Gate.DROP,
				AutoCapture.gateFor(MomentTrigger.ADVANCEMENT, true, false, false, AutoCapture.ScreenKind.NONE));
	}

	// --- the pending countdown ----------------------------------------------------------------

	/**
	 * A loading screen is already bounded by its own completion, so counting frames against it only
	 * races chunk-load speed — and losing that race fails silently: a cold Nether portal produces no
	 * screenshot and says nothing about why.
	 */
	@Test
	void theCountdownDoesNotRunUnderALevelLoadingScreen() {
		AutoCapture.arm(MomentTrigger.NEW_DIMENSION, "minecraft:the_nether", 0L);

		for (int frame = 0; frame < AutoCapture.PENDING_TIMEOUT_FRAMES * 3; frame++) {
			assertFalse(AutoCapture.advancePending(AutoCapture.ScreenKind.LEVEL_LOADING),
					"a slow chunk load must not time the capture out at frame " + frame);
		}
	}

	/** An arrival has its own bound, counted in ticks by ArrivalWatch; the frame countdown must not
	 * cut it short. */
	@Test
	void theCountdownDoesNotRunWhileAnArrivalComesIntoView() {
		AutoCapture.arm(MomentTrigger.NEW_DIMENSION, "minecraft:the_nether", 0L);

		for (int frame = 0; frame < AutoCapture.PENDING_TIMEOUT_FRAMES * 3; frame++) {
			assertFalse(AutoCapture.advancePending(AutoCapture.ScreenKind.ARRIVING),
					"a slow arrival must not drop the capture at frame " + frame);
		}
	}

	@Test
	void aPendingCaptureEventuallyGivesUp() {
		AutoCapture.arm(MomentTrigger.ADVANCEMENT, "Stone Age", 0L);

		for (int frame = 0; frame < AutoCapture.PENDING_TIMEOUT_FRAMES; frame++) {
			assertFalse(AutoCapture.advancePending(AutoCapture.ScreenKind.NONE),
					"still within budget at frame " + frame);
		}
		assertTrue(AutoCapture.advancePending(AutoCapture.ScreenKind.NONE),
				"one frame past the budget the capture is abandoned rather than left armed");
	}

	/** Death is not waiting for an obstruction to clear but for a packet that either comes promptly
	 * or is not coming, so its budget is the shorter of the two. */
	@Test
	void deathGivesUpSoonerThanTheOthers() {
		assertTrue(AutoCapture.DEATH_PENDING_TIMEOUT_FRAMES < AutoCapture.PENDING_TIMEOUT_FRAMES);

		AutoCapture.arm(MomentTrigger.DEATH, null, 0L);
		for (int frame = 0; frame < AutoCapture.DEATH_PENDING_TIMEOUT_FRAMES; frame++) {
			assertFalse(AutoCapture.advancePending(AutoCapture.ScreenKind.NONE));
		}
		assertTrue(AutoCapture.advancePending(AutoCapture.ScreenKind.NONE));
	}

	// --- the visited-dimension rule -----------------------------------------------------------

	/**
	 * This is what "first entry per dimension" buys over "once per world join": opening the game is
	 * not a moment, arriving in the Nether is, and coming home again is not.
	 */
	@Test
	void theDimensionYouLogInToIsRecordedButNeverFires() {
		assertFalse(AutoCapture.markVisited("minecraft:overworld"),
				"the first dimension of a session is where the player logged in, not somewhere they went");
		assertTrue(AutoCapture.markVisited("minecraft:the_nether"));
		assertFalse(AutoCapture.markVisited("minecraft:overworld"),
				"going back somewhere already visited is not a new arrival");
		assertFalse(AutoCapture.markVisited("minecraft:the_nether"));
		assertTrue(AutoCapture.markVisited("modded:some_other_place"),
				"nothing here is specific to vanilla's own dimensions");
	}

	/** Everything is scoped to one connection: the next world starts over, so its own first
	 * dimension is a login again rather than an arrival. */
	@Test
	void disconnectingStartsTheDimensionsOver() {
		AutoCapture.markVisited("minecraft:overworld");
		AutoCapture.markVisited("minecraft:the_nether");

		AutoCapture.forgetSession();

		assertFalse(AutoCapture.markVisited("minecraft:the_nether"),
				"the first dimension of the next session is a login, wherever it happens to be");
	}
}
