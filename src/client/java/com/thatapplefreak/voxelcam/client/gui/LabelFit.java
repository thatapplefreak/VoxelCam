package com.thatapplefreak.voxelcam.client.gui;

/**
 * Which wording a few of the manager's labels take: the full one, or the {@code .short} one
 * where the full one would not read on the control it is on.
 *
 * <p>A button label that does not fit is not clipped: vanilla scrolls it back and forth inside
 * the button. Overflowing by a few pixels the label still reads at every moment ("Renam",
 * "ename"), and those are left to scroll. Overflowing by a third or more, what shows at any one
 * moment is a fragment ("t Animation"), and those labels have a {@code .short} wording.
 *
 * <p>An edit box's hint neither scrolls nor is clipped: {@code EditBox} draws it whole from 4
 * pixels in, across its own border if need be, and it is cut only where the next control along
 * paints over it.
 *
 * <p>Beside {@link GuiScreenShotManager} and free of Minecraft types, for the reason
 * {@link FilmstripLayout} is: the test passes the font's widths in rather than loading one.
 */
final class LabelFit {

	/** {@code AbstractWidget.extractScrollingStringOverContents} keeps this clear on each side
	 * of a button's label, and starts scrolling a label wider than what is left. */
	static final int BUTTON_INSET = 2;
	/** Where {@code EditBox.updateTextPosition} starts a bordered box's text and hint. */
	static final int EDIT_BOX_TEXT_X = 4;

	private LabelFit() {
	}

	/** What a button {@code buttonWidth} wide shows of its label without scrolling it. */
	static int buttonRoom(int buttonWidth) {
		return buttonWidth - BUTTON_INSET * 2;
	}

	/**
	 * What an edit box's hint shows before the control {@code gapToNext} to the right of the box
	 * covers it. A font width counts the pixel after each glyph, which the shadow fills, so a hint
	 * whose width reaches exactly to that control has nothing under it.
	 */
	static int hintRoom(int boxWidth, int gapToNext) {
		return boxWidth + gapToNext - EDIT_BOX_TEXT_X;
	}

	/**
	 * {@code key}, or {@code key + ".short"} where the full wording is wider than {@code room} and
	 * the short one is narrower than it. A language with no shorter word gives both the same
	 * wording, and keeps the full key at every width.
	 */
	static String keyFor(String key, int room, int fullWidth, int shortWidth) {
		return fullWidth <= room || shortWidth >= fullWidth ? key : key + ".short";
	}
}
