package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatapplefreak.voxelcam.client.screenshot.ScreenshotImageCache.Disposition;
import java.io.File;
import org.junit.jupiter.api.Test;

/**
 * The upload path itself needs a live {@code Minecraft} and is exercised by the gametest
 * suite; what is checked here is the decision it hangs off — whether a decode that finished
 * after the manager closed may be used. It may not, on either of the two threads that ask:
 * the loader thread would queue an image for a sweep that never runs again if the manager
 * stays shut, and the render thread would register a texture that the next open's own decode
 * immediately displaces from the loaded map, which is the only handle on a registered id.
 *
 * <p>A decode can also be orphaned one file at a time, by a delete or rename that lands while
 * it is still running, and that is the same decision asked with the key's own claim rather
 * than the cache-wide generation.
 *
 * <p>{@link ScreenshotImageCache#releaseAll()} and {@link ScreenshotImageCache#release(File)}
 * touch no Minecraft state while nothing is loaded, which is why they can be called from here
 * at all.
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

	@Test
	void aDecodeTheCacheIsStillWaitingOnIsUsed() {
		ScreenshotImageCache.releaseAll();
		int submittedIn = ScreenshotImageCache.generation();

		assertEquals(Disposition.UPLOAD, ScreenshotImageCache.dispositionOf(submittedIn, true, true));
		assertEquals(Disposition.FAIL, ScreenshotImageCache.dispositionOf(submittedIn, true, false));
	}

	@Test
	void aDecodeWhoseFileWasReleasedMidFlightIsDiscardedRatherThanUploaded() {
		ScreenshotImageCache.releaseAll();
		int submittedIn = ScreenshotImageCache.generation();

		assertEquals(Disposition.DISCARD, ScreenshotImageCache.dispositionOf(submittedIn, false, true));
		assertEquals(Disposition.DISCARD, ScreenshotImageCache.dispositionOf(submittedIn, false, false));
	}

	/**
	 * Deleting or renaming the selected screenshot is the one thing that drops a claim, and it
	 * has to leave the key free rather than merely marked: the file can come back under the
	 * same path, and a key that kept a claim nothing will ever resolve would never load again.
	 *
	 * <p>The file need not exist — {@code get} registers the claim on the calling thread and
	 * only the loader thread cares whether there is anything to read.
	 */
	@Test
	void releasingAFileDropsTheClaimOfTheDecodeStillRunningForIt() {
		ScreenshotImageCache.releaseAll();
		File file = new File("voxelcam-never-written.png");

		ScreenshotImageCache.get(file, true);
		assertTrue(ScreenshotImageCache.isLoading(file, true));

		ScreenshotImageCache.release(file);
		assertFalse(ScreenshotImageCache.isLoading(file, true));

		ScreenshotImageCache.get(file, true);
		assertTrue(ScreenshotImageCache.isLoading(file, true));
	}
}
