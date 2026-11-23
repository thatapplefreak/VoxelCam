package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thatapplefreak.voxelcam.client.screenshot.ArrivalWatch.Progress;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The readings below are the shapes measured in the client: the renderer's visible-section count
 * climbing in bursts after an arrival, pausing between them, and holding once the view is in.
 */
class ArrivalWatchTest {

	/** Feeds one reading per tick from {@code fromTick}, standing still, and returns the last answer. */
	private static Progress feed(ArrivalWatch watch, int fromTick, int... visible) {
		Progress progress = null;
		for (int i = 0; i < visible.length; i++) {
			progress = watch.observe(fromTick + i, visible[i], 0.0F, 0.0, 0.0);
		}
		return progress;
	}

	private static int[] repeat(int value, int times) {
		int[] values = new int[times];
		Arrays.fill(values, value);
		return values;
	}

	@Test
	void aViewThatHasStoppedGrowingIsInView() {
		ArrivalWatch watch = new ArrivalWatch();
		assertEquals(Progress.ARRIVING, feed(watch, 2, 0, 0, 11, 17, 24, 40, 58, 65));
		assertEquals(Progress.ARRIVING, feed(watch, 10, repeat(65, ArrivalWatch.STEADY_TICKS - 1)),
				"one reading short of steady");
		assertEquals(Progress.IN_VIEW, watch.observe(10 + ArrivalWatch.STEADY_TICKS - 1, 65, 0.0F, 0.0, 0.0));
	}

	/** A Nether arrival at render distance 12 paused at 260 sections for five ticks on its way to
	 * 877. A pause shorter than the steady run is not the end. */
	@Test
	void aPauseBetweenBurstsOfChunksIsNotTheEnd() {
		ArrivalWatch watch = new ArrivalWatch();
		feed(watch, 2, 30, 90, 180, 260);
		assertEquals(Progress.ARRIVING, feed(watch, 6, repeat(260, ArrivalWatch.STEADY_TICKS - 2)));
		assertEquals(Progress.ARRIVING, feed(watch, 20, 410, 600, 877),
				"growth starts the count again");
	}

	/**
	 * What the first attempt missed. On the tick the level changes, the renderer can still describe
	 * the level just left: a teleport out of a quiet overworld read 197 sections and nothing pending
	 * on its first tick in the Nether, and 0 a tick later. Nothing read before the second tick after
	 * the loading screen counts, so a stale reading cannot start, let alone finish, a steady run.
	 */
	@Test
	void aReadingFromTheLevelJustLeftCountsForNothing() {
		ArrivalWatch watch = new ArrivalWatch();
		assertEquals(Progress.ARRIVING, watch.observe(0, 197, 0.0F, 0.0, 0.0), "under the loading screen");
		assertEquals(Progress.ARRIVING, watch.observe(1, 197, 0.0F, 0.0, 0.0), "the first open tick");
		for (int tick = 2; tick < 2 + ArrivalWatch.STEADY_TICKS - 1; tick++) {
			assertEquals(Progress.ARRIVING, watch.observe(tick, 197, 0.0F, 0.0, 0.0),
					"steady may only count from the second tick, at tick " + tick);
		}
	}

	@Test
	void nothingOnScreenIsNeverInView() {
		ArrivalWatch watch = new ArrivalWatch();
		assertEquals(Progress.ARRIVING, feed(watch, 2, repeat(0, ArrivalWatch.STEADY_TICKS * 3)),
				"the End's first frames draw no sections at all");
	}

	/** The overlay fades by itself once the player has arrived, standing in the portal or not. */
	@Test
	void aPortalOverlayStillFadingIsNotInView() {
		ArrivalWatch fading = new ArrivalWatch();
		Progress progress = null;
		for (int tick = 2; tick < 2 + ArrivalWatch.STEADY_TICKS + 5; tick++) {
			progress = fading.observe(tick, 305, 0.4F, 0.0, 0.0);
		}
		assertEquals(Progress.ARRIVING, progress);
		assertEquals(Progress.IN_VIEW, fading.observe(30, 305, 0.0F, 0.0, 0.0));
	}

	/**
	 * A player who walks off is somewhere else by the time the view would settle: the arrival is
	 * over, by their own doing. Measured from where they stood when the screen closed, not under
	 * it, and sideways only, since a teleport can leave them falling.
	 */
	@Test
	void walkingAwayEndsTheArrival() {
		ArrivalWatch watch = new ArrivalWatch();
		assertEquals(Progress.ARRIVING, watch.observe(0, 0, 0.0F, -500.0, 0.0), "under the loading screen");
		assertEquals(Progress.ARRIVING, watch.observe(1, 40, 0.0F, 100.0, 100.0));
		assertEquals(Progress.ARRIVING, watch.observe(2, 41, 0.0F, 100.0, 100.0 + ArrivalWatch.WANDER_BLOCKS));
		assertEquals(Progress.LOST, watch.observe(3, 42, 0.0F, 100.0, 100.0 + ArrivalWatch.WANDER_BLOCKS + 0.5));
	}

	@Test
	void aViewThatNeverHoldsStillIsGivenUpOn() {
		ArrivalWatch watch = new ArrivalWatch();
		for (int tick = 0; tick < ArrivalWatch.TIMEOUT_TICKS; tick++) {
			assertEquals(Progress.ARRIVING, watch.observe(tick, tick, 0.0F, 0.0, 0.0));
		}
		assertEquals(Progress.LOST, watch.observe(ArrivalWatch.TIMEOUT_TICKS, 5, 0.0F, 0.0, 0.0));
	}
}
