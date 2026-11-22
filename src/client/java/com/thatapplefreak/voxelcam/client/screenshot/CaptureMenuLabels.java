package com.thatapplefreak.voxelcam.client.screenshot;

/**
 * Where {@link CaptureMenuHud} puts a label's plate. Renderer-free so the placement can be
 * checked without a client; the HUD measures the text and draws, this only does the arithmetic.
 */
final class CaptureMenuLabels {

	/** Vanilla's font is 9px tall; kept as a constant so the plate does not depend on a field. */
	static final int LINE_HEIGHT = 9;

	/** How far out the outer ring's labels sit: just past {@link CaptureMenu#OPTION_RADIUS}. */
	static final int OPTION_LABEL_RADIUS = CaptureMenu.OPTION_RADIUS + 14;

	private CaptureMenuLabels() {
	}

	/** The mid-angle of option {@code option} on wedge {@code wedgeIndex}'s arc, in the convention
	 * {@link CaptureMenu#subOptionForAngle} resolves by. */
	static double optionAngle(int wedgeCount, int wedgeIndex, int optionCount, int option) {
		double wedgeWidth = Math.PI * 2 / wedgeCount;
		double optionWidth = wedgeWidth / optionCount;
		return wedgeIndex * wedgeWidth - wedgeWidth / 2 + optionWidth * (option + 0.5);
	}

	/**
	 * The plate for a label of {@code textWidth} on {@code angle}'s ray, {@code radius} out from the
	 * centre of a {@code guiWidth} by {@code guiHeight} screen, as {@code {left, top, right,
	 * bottom}}; the text is centred in it with its top at {@code top + 3}.
	 *
	 * <p>A plate that would land on vanilla's hotbar or the health and food row above it is pulled
	 * in along its own ray until it clears them. Below about 290 GUI pixels of height the outer
	 * ring's lowest labels reach that band, and 854x480 at its automatic scale is 240. Along the
	 * ray rather than straight up or sideways, so the label still points at the middle of its own
	 * arc segment; only the label moves, so what an aim selects is untouched. The armour row above
	 * the hearts is not kept clear: at 240 high a label pulled that far would sit on the dial.
	 */
	static int[] plate(double angle, int radius, int textWidth, int guiWidth, int guiHeight) {
		int[] plate = placeOnRay(angle, radius, textWidth, guiWidth, guiHeight);
		for (int pulled = radius - 1; pulled > 0 && onHotbar(plate, guiWidth, guiHeight); pulled--) {
			plate = placeOnRay(angle, pulled, textWidth, guiWidth, guiHeight);
		}
		return plate;
	}

	private static int[] placeOnRay(double angle, int radius, int textWidth, int guiWidth, int guiHeight) {
		int half = textWidth / 2 + 4;
		int x = guiWidth / 2 + (int) Math.round(Math.sin(angle) * radius);
		int y = guiHeight / 2 - (int) Math.round(Math.cos(angle) * radius) - LINE_HEIGHT / 2;
		// Clamped to the screen so a wedge pointing sideways cannot push its label off it.
		x = Math.max(half + 2, Math.min(guiWidth - half - 2, x));
		y = Math.max(2, Math.min(guiHeight - LINE_HEIGHT - 4, y));
		return new int[] {x - half, y - 3, x + half, y + LINE_HEIGHT + 1};
	}

	/** Vanilla's {@code Hud}: a 182-wide hotbar centred at the bottom, hearts and food from
	 * {@code guiHeight - 39} down to it. */
	private static boolean onHotbar(int[] plate, int guiWidth, int guiHeight) {
		return plate[0] < guiWidth / 2 + 91 && plate[2] > guiWidth / 2 - 91 && plate[3] > guiHeight - 39;
	}
}
