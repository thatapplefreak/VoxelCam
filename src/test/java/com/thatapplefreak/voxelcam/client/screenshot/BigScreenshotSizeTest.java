package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers what /bigscreenshot accepts and how a size is fitted to the GPU. resolve(Window) itself
 * is not tested here, since it reads RenderSystem.getDevice() and needs a live GPU context; the
 * fitting behind it is the Minecraft-free resolve(int, int, int).
 */
class BigScreenshotSizeTest {

	@ParameterizedTest
	@ValueSource(strings = { "2x", "4x", "8x", "hd", "fhd", "4k", "8k", "imax" })
	void everyAdvertisedPresetParses(String token) {
		BigScreenshotSize size = BigScreenshotSize.parse(token);

		assertNotNull(size, token + " is offered as a completion, so it must parse");
		// A preset reports the name it was asked for rather than the pixels behind it.
		assertEquals(token, size.token());
	}

	/** Whatever tokens() advertises for tab-completion has to be accepted by parse(). */
	@Test
	void completionsAndParserAgree() {
		for (String token : BigScreenshotSize.tokens()) {
			assertNotNull(BigScreenshotSize.parse(token), token + " is completed but not parsed");
		}
	}

	@Test
	void defaultIsTheTwoTimesPreset() {
		assertEquals("2x", BigScreenshotSize.DEFAULT.token());
	}

	@ParameterizedTest
	@ValueSource(strings = { "1920x1080", "1920*1080" })
	void absoluteSizesAcceptBothSeparators(String token) {
		assertEquals("1920x1080", BigScreenshotSize.parse(token).token());
	}

	@Test
	void multiplesBeyondThePresetsAreAccepted() {
		assertEquals("3x", BigScreenshotSize.parse("3x").token());
		assertEquals("16x", BigScreenshotSize.parse("16x").token());
	}

	/**
	 * WxH is matched before Nx on purpose, so "2x2" is two pixels square rather than
	 * a mangled multiple. Getting this backwards would silently turn a tiny request
	 * into a screen-multiple one.
	 */
	@Test
	void widthByHeightWinsOverMultiple() {
		assertEquals("20x20", BigScreenshotSize.parse("20x20").token());
	}

	/**
	 * A size below the 16-pixel minimum is refused like any other unusable token rather than
	 * quietly raised: the command would otherwise announce "0x0 (16x16)", and a hand-edited
	 * voxelcam.json holding "0x0" falls back to the default the same way "banana" does.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "0x0", "2x2", "15x15", "0x1080", "1920x0", "1920x15", "15*1080" })
	void sizesBelowTheMinimumAreRejected(String token) {
		assertNull(BigScreenshotSize.parse(token), token + " is smaller than anything that renders");
	}

	@Test
	void theMinimumItselfIsAccepted() {
		assertEquals("16x16", BigScreenshotSize.parse("16x16").token());
	}

	@Test
	void aSizeThatFitsIsUntouched() {
		BigScreenshotSize.Resolved resolved = BigScreenshotSize.resolve(1000, 1000, 16384);

		assertEquals(1000, resolved.width());
		assertEquals(1000, resolved.height());
		assertFalse(resolved.clamped());
	}

	@Test
	void anOversizedRequestShrinksToTheGpuMaximumKeepingItsAspect() {
		BigScreenshotSize.Resolved resolved = BigScreenshotSize.resolve(32768, 16384, 16384);

		assertEquals(16384, resolved.width());
		assertEquals(8192, resolved.height());
		assertTrue(resolved.clamped(), "the command says it was clamped to the maximum");
	}

	/**
	 * "Clamped to this GPU's maximum texture size" is only true when the maximum shrank the size.
	 * A multiple of a tiny window is raised to the floor instead, and must not claim the maximum.
	 */
	@Test
	void raisingToTheFloorIsNotReportedAsClampedToTheMaximum() {
		BigScreenshotSize.Resolved resolved = BigScreenshotSize.resolve(8, 4, 16384);

		assertEquals(16, resolved.width());
		assertEquals(16, resolved.height());
		assertFalse(resolved.clamped());
	}

	/** Too large on one axis and, after the shrink, too small on the other: the maximum did shrink it. */
	@Test
	void shrinkingOneAxisPastTheFloorStillReportsTheMaximum() {
		BigScreenshotSize.Resolved resolved = BigScreenshotSize.resolve(20000, 16, 16384);

		assertEquals(16384, resolved.width());
		assertEquals(16, resolved.height());
		assertTrue(resolved.clamped());
	}

	/**
	 * The preset keys are ASCII literals, so folding under the JVM default locale lets the
	 * player's system decide whether a preset exists: under tr/az 'I' folds to a dotless one
	 * (U+0131), and "IMAX" stops being "imax". The default has to be restored, because every
	 * test class shares one JVM and the date and size formatting elsewhere reads it.
	 */
	@Test
	void presetsParseUnderALocaleWithItsOwnCaseRules() {
		Locale saved = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr"));

			BigScreenshotSize size = BigScreenshotSize.parse("IMAX");

			assertNotNull(size, "IMAX is a preset whatever the system locale is");
			assertEquals("imax", size.token());
		} finally {
			Locale.setDefault(saved);
		}
	}

	@Test
	void inputIsTrimmedAndCaseInsensitive() {
		assertEquals("4k", BigScreenshotSize.parse("  4K  ").token());
		assertEquals("1920x1080", BigScreenshotSize.parse("1920X1080").token());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "x", "0x", "banana", "4kk", "-2x", "1920x", "x1080", "1920x1080x720" })
	void nonsenseIsRejectedRatherThanGuessed(String token) {
		assertNull(BigScreenshotSize.parse(token), token + " should not parse");
	}

	/** The digit bounds in the patterns are the only thing stopping absurd allocations. */
	@Test
	void oversizedNumbersAreRejected() {
		assertNull(BigScreenshotSize.parse("1234567x1080"), "seven digits is beyond the pattern");
		assertNull(BigScreenshotSize.parse("100x"), "three-digit multiples are beyond the pattern");
	}
}
