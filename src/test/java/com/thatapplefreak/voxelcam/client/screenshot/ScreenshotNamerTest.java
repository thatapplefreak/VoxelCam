package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Capture names are timestamped to the second, so a burst of screenshots inside one
 * second is the normal case rather than an edge case — the suffix is what stops the
 * second one overwriting the first.
 */
class ScreenshotNamerTest {

	@TempDir
	Path dir;

	@Test
	void picksAnUnusedNameInTheGivenDirectory() {
		File named = ScreenshotNamer.getScreenshotName(dir.toFile());

		assertEquals(dir.toFile(), named.getParentFile());
		assertTrue(named.getName().endsWith(".png"), named.getName());
		assertFalse(named.exists());
	}

	/** yyyy-MM-dd_HH.mm.ss, which is what the manager recognises as a capture name. */
	@Test
	void firstNameOfTheSecondCarriesNoSuffix() {
		String name = ScreenshotNamer.getScreenshotName(dir.toFile()).getName();

		assertTrue(name.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}\\.png"), name);
	}

	@Test
	void collidingNamesGainAnIncrementingSuffix() throws IOException {
		File first = ScreenshotNamer.getScreenshotName(dir.toFile());
		assertTrue(first.createNewFile());

		File second = ScreenshotNamer.getScreenshotName(dir.toFile());
		assertEquals(first.getName().replace(".png", "_2.png"), second.getName());
		assertTrue(second.createNewFile());

		File third = ScreenshotNamer.getScreenshotName(dir.toFile());
		assertEquals(first.getName().replace(".png", "_3.png"), third.getName());
	}

	@Test
	void neverReturnsAPathThatAlreadyExists() throws IOException {
		for (int i = 0; i < 5; i++) {
			File next = ScreenshotNamer.getScreenshotName(dir.toFile());
			assertFalse(next.exists(), next.getName() + " already exists");
			assertTrue(next.createNewFile());
		}
	}
}
