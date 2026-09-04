package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatapplefreak.voxelcam.client.screenshot.ScreenshotImageCache.Disposition;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
 * <p>The full-size cap is the other decision the upload path hangs off, and it is checked here
 * the same way: which files fall out of the budget is worked out apart from the releasing, so
 * the order can be driven without a texture manager to release into.
 *
 * <p>{@link ScreenshotImageCache#releaseAll()} and {@link ScreenshotImageCache#release(File)}
 * touch no Minecraft state while nothing is loaded, which is why they can be called from here
 * at all — and why every test starts by calling one, since the cache is static and JUnit does
 * not promise an order.
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

	/**
	 * A full-size preview holds its decoded image as well as its texture, so browsing a
	 * folder used to cost tens of MiB of native heap and VRAM per screenshot ever selected,
	 * freed only when the manager closed. What bounds it is that the file falling off the
	 * end is named for the caller to release, rather than merely forgotten — a forgotten one
	 * would sit in {@code TextureManager} for the session, since the cache's map is the only
	 * handle on a registered id.
	 */
	@Test
	void aFullSizePreviewPastTheCapIsNamedForRelease() {
		ScreenshotImageCache.releaseAll();
		List<File> files = files(ScreenshotImageCache.MAX_FULL_SIZE + 1);

		for (int i = 0; i < ScreenshotImageCache.MAX_FULL_SIZE; i++) {
			assertEquals(List.of(), ScreenshotImageCache.retainFullSize(files.get(i)));
		}

		assertEquals(List.of(files.get(0)),
				ScreenshotImageCache.retainFullSize(files.get(ScreenshotImageCache.MAX_FULL_SIZE)));
	}

	/** Re-showing one already resident costs nothing and evicts nothing. */
	@Test
	void reloadingAResidentPreviewDoesNotPushAnyOtherOut() {
		ScreenshotImageCache.releaseAll();
		List<File> files = files(ScreenshotImageCache.MAX_FULL_SIZE);
		files.forEach(ScreenshotImageCache::retainFullSize);

		for (File file : files) {
			assertEquals(List.of(), ScreenshotImageCache.retainFullSize(file));
		}
	}

	/**
	 * The one the preview is drawing is the one being asked for every frame, so it must be
	 * the last to go — otherwise a decode landing out of order could evict what is on screen
	 * and the next frame would decode it all over again.
	 */
	@Test
	void theMostRecentlyDrawnPreviewIsTheLastEvicted() {
		ScreenshotImageCache.releaseAll();
		List<File> files = files(ScreenshotImageCache.MAX_FULL_SIZE + 1);
		for (int i = 0; i < ScreenshotImageCache.MAX_FULL_SIZE; i++) {
			ScreenshotImageCache.retainFullSize(files.get(i));
		}

		ScreenshotImageCache.touchFullSize(files.get(0));

		assertEquals(List.of(files.get(1)),
				ScreenshotImageCache.retainFullSize(files.get(ScreenshotImageCache.MAX_FULL_SIZE)));
	}

	/**
	 * A file deleted or renamed away has already had its texture released by
	 * {@link ScreenshotImageCache#release(File)}, so leaving it holding a slot would both
	 * shrink the cap and name it for a second release later.
	 */
	@Test
	void aReleasedFileGivesUpItsFullSizeSlot() {
		ScreenshotImageCache.releaseAll();
		List<File> files = files(ScreenshotImageCache.MAX_FULL_SIZE + 1);
		for (int i = 0; i < ScreenshotImageCache.MAX_FULL_SIZE; i++) {
			ScreenshotImageCache.retainFullSize(files.get(i));
		}

		ScreenshotImageCache.release(files.get(0));

		assertEquals(List.of(), ScreenshotImageCache.retainFullSize(files.get(ScreenshotImageCache.MAX_FULL_SIZE)));
	}

	/** The files need not exist: nothing here reads them. */
	private static List<File> files(int count) {
		List<File> files = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			files.add(new File("voxelcam-never-written-" + i + ".png"));
		}
		return files;
	}
}
