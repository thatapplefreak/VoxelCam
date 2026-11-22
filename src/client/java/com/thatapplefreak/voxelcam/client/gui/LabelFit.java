package com.thatapplefreak.voxelcam.client.gui;

/**
 * Whether a label fits the control it is on, by vanilla's own measure, so that the manager can
 * swap a few labels for a shorter wording at the narrowest windows.
 *
 * <p>A button label that does not fit is not clipped: vanilla scrolls it back and forth inside
 * the button. Overflowing by a few pixels the label still reads at every moment ("Renam",
 * "ename"), and those are left to scroll. Overflowing by a third or more, what shows at any one
 * moment is a fragment ("t Animation"), and those buttons have a {@code .short} label instead.
 * An edit box's hint does not scroll at all; it is cut at the box's edge.
 *
 * <p>Beside {@link GuiScreenShotManager} and free of Minecraft types, for the reason
 * {@link FilmstripLayout} is: the test passes the font's widths in rather than loading one.
 */
final class LabelFit {

	/** {@code AbstractWidget.extractScrollingStringOverContents} keeps this clear on each side
	 * of a button's label, and starts scrolling a label wider than what is left. */
	static final int BUTTON_INSET = 2;
	/** {@code EditBox.getInnerWidth} for a bordered box: what its text and hint get. */
	static final int EDIT_BOX_INSET = 4;

	private LabelFit() {
	}

	static boolean fitsButton(int labelWidth, int buttonWidth) {
		return labelWidth <= buttonWidth - BUTTON_INSET * 2;
	}

	static boolean fitsEditBox(int hintWidth, int boxWidth) {
		return hintWidth <= boxWidth - EDIT_BOX_INSET * 2;
	}
}
