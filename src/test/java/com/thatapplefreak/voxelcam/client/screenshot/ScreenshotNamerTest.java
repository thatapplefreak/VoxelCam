package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
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

	private final Locale defaultLocale = Locale.getDefault();

	/**
	 * Locale.setDefault is JVM-global and Gradle runs the whole suite in one JVM, so a leaked
	 * locale would reach every later test — ScreenshotMetadata's "d MMM" formatter among them.
	 */
	@AfterEach
	void restoreDefaultLocale() {
		Locale.setDefault(defaultLocale);
	}

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

	/**
	 * A locale with its own digit set must not reach the filename: the manager gates the
	 * friendly "Today 14:32" label on an ASCII-only \d regex, so Arabic-Indic digits would
	 * leave every capture listed as a raw timestamp.
	 */
	@Test
	void namesUseAsciiDigitsUnderALocaleThatDoesNot() {
		Locale.setDefault(Locale.forLanguageTag("fa-IR"));

		String name = ScreenshotNamer.getScreenshotName(dir.toFile()).getName();

		assertTrue(name.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}\\.png"), name);
	}

	/**
	 * Thai defaults to the Buddhist calendar, whose year is 543 ahead — ASCII, so the regex
	 * above sees nothing wrong, but the stamp sorts and reads wrong against every other machine.
	 */
	@Test
	void namesUseTheIsoYearUnderANonIsoCalendarLocale() {
		Locale.setDefault(Locale.forLanguageTag("th-TH-u-ca-buddhist"));

		String name = ScreenshotNamer.getScreenshotName(dir.toFile()).getName();

		assertTrue(name.startsWith(LocalDate.now().getYear() + "-"), name);
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

	@Test
	void reservesAKeyAndItsFrames() {
		List<File> names = ScreenshotNamer.reserveBurstNames(dir.toFile(), 4);

		assertEquals(4, names.size());
		assertTrue(names.get(0).getName().matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}\\.png"),
				"the key should be an ordinary capture name: " + names.get(0).getName());
		for (int index = 1; index < names.size(); index++) {
			assertEquals(String.format("_burst%02d", index),
					ScreenshotNamer.burstFrameSuffix(names.get(index).getName()),
					names.get(index).getName());
		}
	}

	@Test
	void reservedNamesAreAllDistinctAndNoneExist() throws IOException {
		List<File> names = ScreenshotNamer.reserveBurstNames(dir.toFile(), 6);

		Set<String> distinct = new HashSet<>();
		for (File name : names) {
			assertFalse(name.exists(), name.getName() + " should not have been created by reserving it");
			assertTrue(distinct.add(name.getName()), name.getName() + " was reserved twice");
		}
	}

	/**
	 * A burst that starts inside a second another capture already occupies inherits that
	 * second's collision suffix rather than colliding with it: the key becomes {@code ..._2.png}
	 * and every frame is derived from that name, not from the bare timestamp.
	 */
	@Test
	void aBurstIntoAnOccupiedSecondCarriesTheCollisionSuffixThrough() throws IOException {
		File occupied = ScreenshotNamer.getScreenshotName(dir.toFile());
		assertTrue(occupied.createNewFile());

		List<File> names = ScreenshotNamer.reserveBurstNames(dir.toFile(), 2);

		assertEquals(occupied.getName().replace(".png", "_2.png"), names.get(0).getName());
		assertEquals(occupied.getName().replace(".png", "_2_burst01.png"), names.get(1).getName());
	}

	@Test
	void burstKeyNameRejectsThePlainCollisionSuffix() {
		assertNull(ScreenshotNamer.burstKeyName("2026-09-05_14.30.00_3.png"),
				"the getScreenshotName collision suffix must never be mistaken for a burst frame");
		assertEquals("2026-09-05_14.30.00.png",
				ScreenshotNamer.burstKeyName("2026-09-05_14.30.00_burst01.png"));
	}

	@Test
	void burstFrameSuffixIsNullForAKeyOrAPlainCollisionSuffix() {
		assertNull(ScreenshotNamer.burstFrameSuffix("2026-09-05_14.30.00.png"));
		assertNull(ScreenshotNamer.burstFrameSuffix("2026-09-05_14.30.00_3.png"));
		assertEquals("_burst07", ScreenshotNamer.burstFrameSuffix("2026-09-05_14.30.00_burst07.png"));
	}
}
