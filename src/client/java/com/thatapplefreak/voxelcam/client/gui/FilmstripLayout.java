package com.thatapplefreak.voxelcam.client.gui;

/**
 * How the filmstrip's row under a burst's preview is shared out, left to right: a scroll
 * button, the cells, the other scroll button, then the column holding Set as key and Export
 * animation.
 *
 * <p>Beside {@link GuiScreenShotManager} rather than in it, and with no Minecraft type in
 * sight, so that a plain JUnit test can sweep every width without loading a {@code Screen}:
 * the reason {@code VoxelCamIO.nameCollides} is not in the popup that asks it. The manager
 * places its buttons and draws its cells from these same numbers, so the widgets and the cells
 * drawn between them cannot disagree about where anything starts.
 */
final class FilmstripLayout {

	static final int CELL_SIZE = 34;
	static final int CELL_GAP = 3;
	static final int SCROLL_BUTTON_WIDTH = 16;
	/** What the row keeps between a scroll button and whatever is next to it. */
	static final int ROW_GAP = 6;
	/** Set as key and Export animation share one column at the strip's right end, stacked, so a
	 * second burst action costs the strip no cells; wide enough for the longer of the two labels.
	 * The widest it gets: {@link #actionWidth} narrows it where a cell would not fit. */
	static final int MAX_ACTION_WIDTH = 96;

	private FilmstripLayout() {
	}

	/**
	 * How wide the Set as key / Export animation column is: {@link #MAX_ACTION_WIDTH} wherever
	 * a cell still fits beside it, and less where one would not. Below a scaled width of about
	 * 362 the full column leaves no room between the scroll buttons, and at 320, the narrowest
	 * vanilla produces, it leaves minus eight pixels. The column is what gives way because its
	 * buttons can take a shorter wording ({@link LabelFit}), while a strip with no cell leaves no
	 * frame to pick and so nothing for Set as key to act on.
	 */
	static int actionWidth(int previewWidth) {
		int scrollButtons = (SCROLL_BUTTON_WIDTH + ROW_GAP) * 2;
		int besideOneCell = previewWidth - scrollButtons - CELL_SIZE - ROW_GAP;
		return Math.max(0, Math.min(MAX_ACTION_WIDTH, besideOneCell));
	}

	/**
	 * How many cells fit between the scroll buttons. Never forced up to one: the cells are
	 * drawn after the widgets, so a cell the row has no room for is painted over the right
	 * scroll button and the action column, whose clicks still go to them. {@link #actionWidth}
	 * is what keeps this at one or more at every width vanilla allows; narrower than that, no
	 * cell is the honest answer.
	 */
	static int cellCapacity(int previewWidth) {
		int scrollButtons = (SCROLL_BUTTON_WIDTH + ROW_GAP) * 2;
		int available = previewWidth - actionWidth(previewWidth) - ROW_GAP - scrollButtons;
		return Math.max(0, (available + CELL_GAP) / (CELL_SIZE + CELL_GAP));
	}
}
