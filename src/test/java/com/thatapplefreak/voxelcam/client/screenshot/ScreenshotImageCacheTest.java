package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thatapplefreak.voxelcam.client.screenshot.ScreenshotImageCache.Disposition;
import org.junit.jupiter.api.Test;

/**
 * The upload path itself needs a live {@code Minecraft} and is exercised by the gametest
 * suite; what is checked here is the decision it hangs off — whether a decode that finished
 * after the manager closed may be used. It may not, on either of the two threads that ask:
 * the loader thread would queue an image for a sweep that never runs again if the manager
 * stays shut, and the render thread would register a texture that the next open's own decode
 * immediately displaces from the loaded map, which is the only handle on a registered id.
 *
 * <p>{@link ScreenshotImageCache#releaseAll()} touches no Minecraft state while nothing is
 * loaded, which is why it can be called from here at all.
 */
class ScreenshotImageCacheTest {

	@Test
	void decodesSubmittedSinceTheLastReleaseAreUsed() {
		ScreenshotImageCache.releaseAll();
		int submittedIn = ScreenshotImageCache.generation();

		assertEquals(Disposition.UPLOAD, ScreenshotImageCache.dispositionOf(submittedIn, true));
		assertEquals(Disposition.FAIL, ScreenshotImageCache.dispositionOf(submittedIn, false));
	}

	@Test
	void decodesLandingAfterAReleaseAreDiscardedRatherThanUploaded() {
		int submittedIn = ScreenshotImageCache.generation();

		ScreenshotImageCache.releaseAll();

		assertEquals(Disposition.DISCARD, ScreenshotImageCache.dispositionOf(submittedIn, true));
	}

	@Test
	void aStaleFailureDoesNotPoisonTheReopenedCache() {
		int submittedIn = ScreenshotImageCache.generation();

		ScreenshotImageCache.releaseAll();

		assertEquals(Disposition.DISCARD, ScreenshotImageCache.dispositionOf(submittedIn, false));
	}

	@Test
	void eachReleaseInvalidatesOnlyTheDecodesThatPrecededIt() {
		ScreenshotImageCache.releaseAll();
		int first = ScreenshotImageCache.generation();
		ScreenshotImageCache.releaseAll();
		int second = ScreenshotImageCache.generation();

		assertEquals(Disposition.DISCARD, ScreenshotImageCache.dispositionOf(first, true));
		assertEquals(Disposition.UPLOAD, ScreenshotImageCache.dispositionOf(second, true));
	}
}
