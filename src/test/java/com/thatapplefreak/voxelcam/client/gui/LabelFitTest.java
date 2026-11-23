package com.thatapplefreak.voxelcam.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * Which wording the manager gives its four shortenable labels, in both shipped languages, at a
 * scaled GUI width of 320 (the narrowest vanilla produces), 427 (1280x720 and an 854x480 window
 * at scale 2) and 480 (1920x1080 at the scale vanilla picks).
 *
 * <p>The wordings are read from the real lang files and measured with the vanilla font's
 * advances, the table {@code CaptionFitTest} uses. The control widths are written out rather
 * than read from the manager, the way {@code FilmstripLayoutTest} writes out its row: the
 * per-file actions are a third of what the favorite star leaves under the preview, the filmstrip's
 * column is {@link FilmstripLayout#actionWidth} of the preview (138, 244 and 277 wide), and the
 * search box is the list less the sort and filter buttons, with the star 6 to its right.
 */
class LabelFitTest {

	/** 320, 427 and 480 wide, in that order. */
	private static final int[] ACTION = { 33, 68, 79 };
	private static final int[] COLUMN = {
			FilmstripLayout.actionWidth(138), FilmstripLayout.actionWidth(244), FilmstripLayout.actionWidth(277) };
	private static final int[] SEARCH = { 66, 67, 87 };
	private static final int STAR_GAP = 6;

	/** The default font's advance per character: most glyphs are 5 pixels and a gap. */
	private static final ToIntFunction<String> FONT = text -> text.codePoints().map(c -> switch (c) {
		case ' ', 't', 'I', '(', ')', '[', ']' -> 4;
		case 'l', '`' -> 3;
		case ':', '.', ',', 'i', '!', '|', '\'', '·' -> 2;
		case 'k', 'f', '<', '>' -> 5;
		default -> 6;
	}).sum();

	private static final JsonObject EN_US = lang("en_us");
	private static final JsonObject EN_PT = lang("en_pt");

	private static JsonObject lang(String code) {
		try (Reader reader = new InputStreamReader(
				LabelFitTest.class.getResourceAsStream("/assets/voxelcam/lang/" + code + ".json"),
				StandardCharsets.UTF_8)) {
			return new Gson().fromJson(reader, JsonObject.class);
		} catch (Exception e) {
			throw new AssertionError("could not read " + code + ".json", e);
		}
	}

	private static String wording(JsonObject lang, String key) {
		assertNotNull(lang.get(key), key + " has no wording");
		return lang.get(key).getAsString();
	}

	/** What the manager's {@code fittingKey} picks, with this font. */
	private static String chosen(JsonObject lang, String key, int room) {
		return LabelFit.keyFor(key, room, FONT.applyAsInt(wording(lang, key)),
				FONT.applyAsInt(wording(lang, key + ".short")));
	}

	private static String[] rename(JsonObject lang) {
		return new String[] {
				chosen(lang, "voxelcam.rename", LabelFit.buttonRoom(ACTION[0])),
				chosen(lang, "voxelcam.rename", LabelFit.buttonRoom(ACTION[1])),
				chosen(lang, "voxelcam.rename", LabelFit.buttonRoom(ACTION[2])) };
	}

	private static String[] column(JsonObject lang, String key) {
		return new String[] {
				chosen(lang, key, LabelFit.buttonRoom(COLUMN[0])),
				chosen(lang, key, LabelFit.buttonRoom(COLUMN[1])),
				chosen(lang, key, LabelFit.buttonRoom(COLUMN[2])) };
	}

	private static String[] hint(JsonObject lang) {
		return new String[] {
				chosen(lang, "voxelcam.search", LabelFit.hintRoom(SEARCH[0], STAR_GAP)),
				chosen(lang, "voxelcam.search", LabelFit.hintRoom(SEARCH[1], STAR_GAP)),
				chosen(lang, "voxelcam.search", LabelFit.hintRoom(SEARCH[2], STAR_GAP)) };
	}

	private static void assertKeys(String key, String[] expected, String[] actual) {
		for (int i = 0; i < expected.length; i++) {
			String want = expected[i].isEmpty() ? key : key + expected[i];
			assertEquals(want, actual[i], key + " at " + new int[] { 320, 427, 480 }[i]);
		}
	}

	/** The table agrees with what the 26.3 font measures for the wordings this turns on. */
	@Test
	void theTableMeasuresTheseWordingsAsTheGameDoes() {
		assertEquals(36, FONT.applyAsInt("Rename"));
		assertEquals(60, FONT.applyAsInt("Re-christen"));
		assertEquals(54, FONT.applyAsInt("Set as Key"));
		assertEquals(82, FONT.applyAsInt("Export Animation"));
		assertEquals(61, FONT.applyAsInt("Make th' Key"));
		assertEquals(75, FONT.applyAsInt("Make 'Em Dance"));
		assertEquals(84, FONT.applyAsInt("Search th' Chest"));
	}

	/** Vanilla starts scrolling a button label wider than the button less 2 on each side. */
	@Test
	void aButtonLabelHasTheButtonLessTwoOnEachSide() {
		assertEquals(50, LabelFit.buttonRoom(54));
		assertEquals("k", LabelFit.keyFor("k", 50, 50, 10));
		assertEquals("k.short", LabelFit.keyFor("k", 50, 51, 10));
	}

	/** {@code EditBox} draws its hint from 4 in, unclipped; the star 6 past the box covers it. */
	@Test
	void aHintRunsOnUntilTheStarCoversIt() {
		assertEquals(89, LabelFit.hintRoom(87, STAR_GAP));
	}

	/** A language with no shorter word keeps its full key, rather than one that reads the same. */
	@Test
	void aShortWordingNoShorterThanTheFullOneIsNeverChosen() {
		assertEquals("k", LabelFit.keyFor("k", 29, 36, 36));
	}

	@Test
	void englishShortensOnlyTheFilmstripColumnAndOnlyAt320() {
		assertKeys("voxelcam.burst.setaskey", new String[] { ".short", "", "" },
				column(EN_US, "voxelcam.burst.setaskey"));
		assertKeys("voxelcam.burst.exportanimation", new String[] { ".short", "", "" },
				column(EN_US, "voxelcam.burst.exportanimation"));
		// No shorter English word; at 320 it scrolls by seven pixels, which still reads.
		assertKeys("voxelcam.rename", new String[] { "", "", "" }, rename(EN_US));
		assertKeys("voxelcam.search", new String[] { "", "", "" }, hint(EN_US));
	}

	/** The playtest's "ristem", "Make th' Ke", "'Em Dance" and "Search th' Ch". At 480 the full
	 * hint ends flush with the box's border and the star leaves it whole. */
	@Test
	void pirateShortensWhatWouldNotReadAndNothingElse() {
		assertKeys("voxelcam.rename", new String[] { ".short", "", "" }, rename(EN_PT));
		assertKeys("voxelcam.burst.setaskey", new String[] { ".short", "", "" },
				column(EN_PT, "voxelcam.burst.setaskey"));
		assertKeys("voxelcam.burst.exportanimation", new String[] { ".short", "", "" },
				column(EN_PT, "voxelcam.burst.exportanimation"));
		assertKeys("voxelcam.search", new String[] { ".short", ".short", "" }, hint(EN_PT));
	}

	/** Wherever a short wording is chosen, it fits outright. */
	@Test
	void everyShortWordingFitsWhereItIsUsed() {
		for (JsonObject lang : new JsonObject[] { EN_US, EN_PT }) {
			for (int i = 0; i < 3; i++) {
				assertFits(lang, "voxelcam.rename", LabelFit.buttonRoom(ACTION[i]));
				assertFits(lang, "voxelcam.burst.setaskey", LabelFit.buttonRoom(COLUMN[i]));
				assertFits(lang, "voxelcam.burst.exportanimation", LabelFit.buttonRoom(COLUMN[i]));
				assertFits(lang, "voxelcam.search", LabelFit.hintRoom(SEARCH[i], STAR_GAP));
			}
		}
	}

	private static void assertFits(JsonObject lang, String key, int room) {
		String chosen = chosen(lang, key, room);
		if (chosen.endsWith(".short")) {
			int width = FONT.applyAsInt(wording(lang, chosen));
			assertTrue(width <= room, wording(lang, chosen) + " is " + width + " in " + room);
		}
	}
}
