package com.thatapplefreak.voxelcam.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * The rows and the details line at the widths the default 854x480 window gives them (GUI scale 2:
 * a row's text gets about 80 pixels, the details line the preview's 244), measured with the vanilla
 * font's advances for the characters these lines use.
 */
class CaptionFitTest {

	private static final int ROW = 80;
	private static final int DETAILS = 244;
	private static final int WIDE_DETAILS = 600;

	/** The default font's advance per character: most glyphs are 5 pixels and a gap. */
	private static final ToIntFunction<String> FONT = text -> text.codePoints().map(c -> switch (c) {
		case ' ', 't', 'I', '(', ')', '[', ']' -> 4;
		case 'l', '`' -> 3;
		case ':', '.', ',', 'i', '!', '|', '\'', '·' -> 2;
		case 'k', 'f', '<', '>' -> 5;
		default -> 6;
	}).sum();

	private static String row(String dimensions, String size) {
		return CaptionFit.fit(CaptionFit.rowFacts(dimensions, size), CaptionFit.ROW_SEPARATOR, ROW, FONT);
	}

	private static String name(String day, String name) {
		return CaptionFit.fit(CaptionFit.rowName(day, name), CaptionFit.NAME_SEPARATOR, ROW, FONT);
	}

	@Test
	void aRowNeverShowsASizeWithoutItsUnit() {
		assertEquals("854×480 · 95 KB", row("854×480", "95 KB"));
		// The playtest's "7680×4320 · 4." and "· 7.4": the size goes whole, and says it went.
		assertEquals("7680×4320 · …", row("7680×4320", "4.2 MB"));
		assertEquals("1920×1080 · …", row("1920×1080", "7.4 MB"));
		assertEquals("7.4 MB", row(null, "7.4 MB"));
	}

	@Test
	void aNameThePlayerGaveIsCutWithAnEllipsis() {
		String cut = name(null, "0001_empty-manager-after-delete");
		assertTrue(cut.startsWith("0001_empty") && cut.endsWith("…"), cut);
		assertTrue(FONT.applyAsInt(cut) <= ROW, cut);
		assertEquals("castle", name(null, "castle"));
	}

	@Test
	void screenshotsFromTheSameMinuteKeepTheSecondsThatTellThemApart() {
		assertEquals("Today 19:01:15", name("Today", "19:01:15"));
		assertEquals("Today 19:01:42", name("Today", "19:01:42"));
		// Too wide with the day: the day goes, since date-sorted neighbours share it anyway.
		assertEquals("… 19:01:15", name("Yesterday", "19:01:15"));
		assertEquals("21 Nov, 19:01:15", name("21 Nov,", "19:01:15"));
	}

	@Test
	void theDetailsLineShowsWhyAnAutomaticCaptureExistsAtTheDefaultWindow() {
		String line = CaptionFit.fit(CaptionFit.details(null, "Advancement: We Need to Go Deeper", "12, 64, -45",
				"854×480", "95 KB", "2026-11-21_19.01.15.png"), CaptionFit.DETAILS_SEPARATOR, DETAILS, FONT);
		assertEquals("Advancement: We Need to Go Deeper  ·  …", line);
	}

	@Test
	void theDetailsLineDropsTheFileNameBeforeAnyFact() {
		String line = CaptionFit.fit(CaptionFit.details(null, null, "12, 64, -45",
				"854×480", "95 KB", "2026-11-21_19.01.15.png"), CaptionFit.DETAILS_SEPARATOR, DETAILS, FONT);
		assertEquals("12, 64, -45  ·  854×480  ·  95 KB  ·  …", line);
	}

	@Test
	void everythingFitsOnAWideWindowAndNothingIsElided() {
		String line = CaptionFit.fit(CaptionFit.details(null, "Advancement: We Need to Go Deeper", "12, 64, -45",
				"854×480", "95 KB", "2026-11-21_19.01.15.png"), CaptionFit.DETAILS_SEPARATOR, WIDE_DETAILS, FONT);
		assertEquals("Advancement: We Need to Go Deeper  ·  12, 64, -45  ·  854×480  ·  95 KB  ·  "
				+ "2026-11-21_19.01.15.png", line);
	}

	@Test
	void aLongTitleAloneIsCutAndAFactThatCannotBeCutLeavesOnlyTheEllipsis() {
		String cut = CaptionFit.fit(CaptionFit.details(null, "Advancement: We Need to Go Deeper", null,
				"854×480", "95 KB", "x.png"), CaptionFit.DETAILS_SEPARATOR, 100, FONT);
		assertTrue(cut.startsWith("Advancement") && cut.endsWith("…") && FONT.applyAsInt(cut) <= 100, cut);
		assertEquals("…", CaptionFit.fit(CaptionFit.rowFacts("7680×4320", "4.2 MB"), CaptionFit.ROW_SEPARATOR, 20, FONT));
		assertEquals("", CaptionFit.fit(CaptionFit.rowFacts("7680×4320", "4.2 MB"), CaptionFit.ROW_SEPARATOR, 3, FONT));
	}

	@Test
	void everyResultFitsAtEveryWidth() {
		var facts = CaptionFit.details("Animation, 30 frames", "Boss defeated: Wither", "the_nether -32, 70, 12",
				"7680×4320", "41.3 MB", "2026-11-21_19.01.15_animation.png");
		for (int width = 0; width <= 700; width++) {
			String line = CaptionFit.fit(facts, CaptionFit.DETAILS_SEPARATOR, width, FONT);
			assertTrue(FONT.applyAsInt(line) <= width, width + ": " + line);
		}
	}
}
