package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Which frame the manager's preview shows when: each for exactly its own delay, then round again. */
class AnimationPreviewTest {

	private static final int[] DELAYS = { 40, 20, 60 };
	private static final int TOTAL = 120;

	@Test
	void eachFrameShowsForExactlyItsDelay() {
		assertEquals(0, AnimationPreview.frameIndexAt(DELAYS, TOTAL, 0));
		assertEquals(0, AnimationPreview.frameIndexAt(DELAYS, TOTAL, 39));
		assertEquals(1, AnimationPreview.frameIndexAt(DELAYS, TOTAL, 40));
		assertEquals(1, AnimationPreview.frameIndexAt(DELAYS, TOTAL, 59));
		assertEquals(2, AnimationPreview.frameIndexAt(DELAYS, TOTAL, 60));
		assertEquals(2, AnimationPreview.frameIndexAt(DELAYS, TOTAL, 119));
	}

	@Test
	void theLoopStartsOverAfterTheLastFrame() {
		assertEquals(0, AnimationPreview.frameIndexAt(DELAYS, TOTAL, 120));
		assertEquals(1, AnimationPreview.frameIndexAt(DELAYS, TOTAL, 120 * 1000 + 45));
	}

	@Test
	void aClockThatStepsBackwardsStillLandsOnAFrame() {
		assertEquals(2, AnimationPreview.frameIndexAt(DELAYS, TOTAL, -1));
		assertEquals(0, AnimationPreview.frameIndexAt(new int[0], 0, 500));
	}

	@Test
	void previewFramesAreScaledToTheLongerSideCap() {
		assertEquals(java.util.List.of(854, 480), boxed(AnimationPreview.previewSize(854, 480)), "small enough already");
		int[] hd = AnimationPreview.previewSize(1920, 1080);
		assertEquals(AnimationPreview.MAX_DIMENSION, hd[0]);
		assertEquals(576, hd[1]);
		int[] tall = AnimationPreview.previewSize(1080, 1920);
		assertEquals(AnimationPreview.MAX_DIMENSION, tall[1]);
	}

	private static java.util.List<Integer> boxed(int[] size) {
		return java.util.List.of(size[0], size[1]);
	}
}
