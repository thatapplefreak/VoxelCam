package com.thatapplefreak.voxelcam.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The filmstrip's row under a burst's preview, left to right: a scroll button, the cells, the
 * other scroll button, then the column holding Set as key and Export animation. The cells are
 * drawn after the widgets, so a cell that does not fit is painted over a button whose clicks
 * still go to the button.
 *
 * <p>The sizes below are written out again here, not read from the manager, so that this
 * checks the arithmetic against the layout and not against itself: scroll buttons 16 wide,
 * cells 34 wide and 3 apart, and 6 between everything else.
 *
 * <p>Only the two static functions are touched. {@code GuiScreenShotManager} is a {@code
 * Screen}, and its class does initialise without a client, which is all calling them needs.
 */
class FilmstripLayoutTest {

	/** The preview at a scaled GUI width of 320, the narrowest vanilla produces: the list keeps
	 * its 150 floor, and 320 less the margins, the list and the gap between leaves 138. */
	private static final int NARROWEST_PREVIEW = 138;

	private static int lastCellRight(int cells) {
		return 16 + 6 + cells * 34 + (cells - 1) * 3;
	}

	private static int rightScrollButtonLeft(int previewWidth) {
		return previewWidth - GuiScreenShotManager.filmstripActionWidth(previewWidth) - 6 - 16;
	}

	@Test
	void atTheNarrowestWindowTheActionColumnGivesWayToOneCell() {
		assertEquals(54, GuiScreenShotManager.filmstripActionWidth(NARROWEST_PREVIEW));
		assertEquals(1, GuiScreenShotManager.filmstripCellCapacity(NARROWEST_PREVIEW));
	}

	/** At 320 wide a 96-wide column left minus eight pixels for cells, and the one cell drawn
	 * anyway covered the right scroll button and the edge of both action buttons. */
	@Test
	void atEveryWidthThereIsACellAndItClearsTheButtonsToItsRight() {
		for (int previewWidth = NARROWEST_PREVIEW; previewWidth <= 400; previewWidth++) {
			int cells = GuiScreenShotManager.filmstripCellCapacity(previewWidth);

			assertTrue(cells >= 1, "no frame can be picked at a preview " + previewWidth + " wide");
			assertTrue(lastCellRight(cells) + 6 <= rightScrollButtonLeft(previewWidth),
					cells + " cells run into the right scroll button at a preview " + previewWidth + " wide");
		}
	}

	/** Where the full column already fits beside a cell, nothing about the row changes. */
	@Test
	void theActionColumnKeepsItsFullWidthWhereverACellFitsBesideIt() {
		assertEquals(96, GuiScreenShotManager.filmstripActionWidth(180));
		assertEquals(1, GuiScreenShotManager.filmstripCellCapacity(180));
		assertEquals(96, GuiScreenShotManager.filmstripActionWidth(244));
		assertEquals(2, GuiScreenShotManager.filmstripCellCapacity(244));
	}

	/** Narrower than vanilla can make it, the answer is no cells, not one drawn over a button. */
	@Test
	void aPreviewTooNarrowForAnyCellDrawsNone() {
		assertEquals(0, GuiScreenShotManager.filmstripCellCapacity(60));
		assertEquals(0, GuiScreenShotManager.filmstripActionWidth(60));
	}
}
