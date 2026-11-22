package com.thatapplefreak.voxelcam.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The manager's labels against the controls they sit on, at a scaled GUI width of 320 (the
 * narrowest vanilla produces), 427 (a 16:9 window at the scale vanilla picks for 1280x720 and
 * 2560x1440) and 854.
 *
 * <p>Widths are written out rather than measured, so that no font has to load: the labels are
 * what vanilla's default font gives them, and the controls are what the manager's split gives
 * them. Per-file actions are a third of what the favorite star leaves under the preview (33, 68
 * and 156), the filmstrip's column is {@link FilmstripLayout#actionWidth} of the preview (138,
 * 244 and 508 wide), and the search box is the list less the sort and filter buttons (66, 67 and
 * 230).
 */
class LabelFitTest {

	private static final int[] ACTION = { 33, 68, 156 };
	private static final int[] COLUMN = {
			FilmstripLayout.actionWidth(138), FilmstripLayout.actionWidth(244), FilmstripLayout.actionWidth(508) };
	private static final int[] SEARCH = { 66, 67, 230 };

	// Rename, Set as Key, Export Animation, Search; then their short wordings.
	private static final int[] EN_US = { 36, 54, 82, 36 };
	private static final int[] EN_US_SHORT = { 36, 38, 34, 36 };
	// Re-christen, Make th' Key, Make 'Em Dance, Search th' Chest; Dub, Make Key, Dance!, Hunt.
	private static final int[] EN_PT = { 60, 61, 75, 84 };
	private static final int[] EN_PT_SHORT = { 18, 45, 32, 22 };

	/** Vanilla starts scrolling a label wider than the button less 2 on each side. */
	@Test
	void aButtonLabelFitsUpToTwoPixelsShortOfEitherEdge() {
		assertTrue(LabelFit.fitsButton(46, 50));
		assertFalse(LabelFit.fitsButton(47, 50));
	}

	/** An edit box's text area is the box less its 4-pixel border on each side. */
	@Test
	void aHintFitsInsideTheEditBoxBorder() {
		assertTrue(LabelFit.fitsEditBox(58, 66));
		assertFalse(LabelFit.fitsEditBox(59, 66));
	}

	/** At 854 every full label fits in both languages, so nothing there changes. */
	@Test
	void atAWideWindowEveryLabelKeepsItsFullWording() {
		for (int[] full : new int[][] { EN_US, EN_PT }) {
			assertTrue(LabelFit.fitsButton(full[0], ACTION[2]));
			assertTrue(LabelFit.fitsButton(full[1], COLUMN[2]));
			assertTrue(LabelFit.fitsButton(full[2], COLUMN[2]));
			assertTrue(LabelFit.fitsEditBox(full[3], SEARCH[2]));
		}
	}

	/** At 427 the buttons are wide enough for every full label; only the pirate search hint,
	 * which is cut rather than scrolled, needs its short wording. */
	@Test
	void atA427WindowOnlyThePirateSearchHintIsShortened() {
		for (int[] full : new int[][] { EN_US, EN_PT }) {
			assertTrue(LabelFit.fitsButton(full[0], ACTION[1]));
			assertTrue(LabelFit.fitsButton(full[1], COLUMN[1]));
			assertTrue(LabelFit.fitsButton(full[2], COLUMN[1]));
		}
		assertTrue(LabelFit.fitsEditBox(EN_US[3], SEARCH[1]));
		assertFalse(LabelFit.fitsEditBox(EN_PT[3], SEARCH[1]));
		assertTrue(LabelFit.fitsEditBox(EN_PT_SHORT[3], SEARCH[1]));
	}

	/**
	 * At 320 the playtest read "t Animation" and "ristem" off buttons whose labels were caught
	 * mid-scroll. Every wording the manager falls back to there fits outright, except English
	 * Rename: there is no shorter English word for it, and it overflows by seven pixels, which
	 * still reads at every moment of the scroll.
	 */
	@Test
	void atTheNarrowestWindowEveryShortWordingFitsButEnglishRename() {
		assertFalse(LabelFit.fitsButton(EN_US[1], COLUMN[0]));
		assertFalse(LabelFit.fitsButton(EN_US[2], COLUMN[0]));
		assertFalse(LabelFit.fitsButton(EN_PT[0], ACTION[0]));

		assertTrue(LabelFit.fitsButton(EN_US_SHORT[1], COLUMN[0]));
		assertTrue(LabelFit.fitsButton(EN_US_SHORT[2], COLUMN[0]));
		assertTrue(LabelFit.fitsEditBox(EN_US_SHORT[3], SEARCH[0]));
		for (int i = 0; i < 3; i++) {
			assertTrue(LabelFit.fitsButton(EN_PT_SHORT[i], i == 0 ? ACTION[0] : COLUMN[0]));
		}
		assertTrue(LabelFit.fitsEditBox(EN_PT_SHORT[3], SEARCH[0]));

		assertFalse(LabelFit.fitsButton(EN_US_SHORT[0], ACTION[0]));
		assertTrue(LabelFit.fitsButton(EN_US_SHORT[0], ACTION[0] + 7));
	}
}
