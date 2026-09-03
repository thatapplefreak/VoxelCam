package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers what /bigscreenshot accepts. resolve() is deliberately not tested here:
 * it clamps against RenderSystem.getDevice(), which needs a live GPU context.
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
		assertEquals("2x2", BigScreenshotSize.parse("2x2").token());
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
