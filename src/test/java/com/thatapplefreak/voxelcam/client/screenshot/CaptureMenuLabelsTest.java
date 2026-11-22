package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Where the outer ring's labels land, at the screens a player is most likely to have: 854x480 at
 * its automatic GUI scale is 427x240, which is also vanilla's smallest scaled height, and 320x240
 * is the narrowest. The playtest that found this saw "4k" on the hunger bar and "8k" on the hotbar.
 *
 * <p>Text widths are estimated at vanilla's 6px advance per character, which is what its font
 * gives these digits and lower-case letters; the HUD measures the real width.
 */
class CaptureMenuLabelsTest {

	private static final int[][] SMALL_SCREENS = { { 427, 240 }, { 320, 240 } };
	private static final int MODES = CaptureMenu.Mode.values().length;

	@Test
	void noRingLabelLandsOnTheHotbarOrTheBarsAboveIt() {
		for (int[] screen : SMALL_SCREENS) {
			for (CaptureMenu.Mode mode : ringModes()) {
				for (int[] plate : ringPlates(mode, screen)) {
					int bandLeft = screen[0] / 2 - 91;
					int bandRight = screen[0] / 2 + 91;
					int bandTop = screen[1] - 39;
					boolean overlaps = plate[0] < bandRight && plate[2] > bandLeft && plate[3] > bandTop;
					assertFalse(overlaps, mode + " at " + Arrays.toString(screen) + ": plate "
							+ Arrays.toString(plate) + " is in the hotbar band");
				}
			}
		}
	}

	@Test
	void eachLabelStillSitsOnItsOwnOptionsRay() {
		for (int[] screen : SMALL_SCREENS) {
			for (CaptureMenu.Mode mode : ringModes()) {
				List<int[]> plates = ringPlates(mode, screen);
				for (int i = 0; i < plates.size(); i++) {
					int[] plate = plates.get(i);
					double dx = (plate[0] + plate[2]) / 2.0 - screen[0] / 2;
					double dy = (plate[1] + plate[3]) / 2.0 - screen[1] / 2;
					double angle = Math.atan2(dx, -dy);
					assertEquals(mode.ordinal(), CaptureMenu.wedgeForAngle(angle, MODES), mode + " option " + i);
					assertEquals(i, CaptureMenu.subOptionForAngle(angle, MODES, mode.ordinal(), plates.size()),
							mode + " option " + i + " at " + Arrays.toString(screen));
				}
			}
		}
	}

	@Test
	void ringLabelsClearTheDialEachOtherAndTheModeLabels() {
		// en_us only: the pirate "Flurry o' Maps" already meets "4s" on the Burst ring, a clash in
		// the mode label's own width that is older than this placement and not one it can fix.
		String[][] modeNames = { { "Screenshot", "Big Screenshot", "Burst" } };
		for (int[] screen : SMALL_SCREENS) {
			for (CaptureMenu.Mode mode : ringModes()) {
				List<int[]> plates = ringPlates(mode, screen);
				for (int i = 0; i < plates.size(); i++) {
					int[] plate = plates.get(i);
					assertTrue(distanceToCentre(plate, screen) > CaptureMenuHud.RADIUS,
							mode + " option " + i + " covers the dial at " + Arrays.toString(screen));
					for (int j = i + 1; j < plates.size(); j++) {
						assertFalse(intersects(plate, plates.get(j)), mode + " options " + i + " and " + j);
					}
					for (String[] names : modeNames) {
						for (int m = 0; m < MODES; m++) {
							int[] modePlate = CaptureMenuLabels.plate(m * Math.PI * 2 / MODES,
									CaptureMenuHud.RADIUS + CaptureMenuHud.LABEL_GAP, width(names[m]),
									screen[0], screen[1]);
							assertFalse(intersects(plate, modePlate),
									mode + " option " + i + " overlaps \"" + names[m] + "\"");
						}
					}
				}
			}
		}
	}

	@Test
	void aTallEnoughScreenKeepsEveryLabelOnTheRingsOwnRadius() {
		int[] screen = { 960, 540 };
		for (CaptureMenu.Mode mode : ringModes()) {
			List<int[]> plates = ringPlates(mode, screen);
			for (int i = 0; i < plates.size(); i++) {
				double angle = CaptureMenuLabels.optionAngle(MODES, mode.ordinal(), plates.size(), i);
				int[] expected = { screen[0] / 2 + (int) Math.round(Math.sin(angle) * CaptureMenuLabels.OPTION_LABEL_RADIUS),
						screen[1] / 2 - (int) Math.round(Math.cos(angle) * CaptureMenuLabels.OPTION_LABEL_RADIUS) };
				int[] plate = plates.get(i);
				assertEquals(expected[0], (plate[0] + plate[2]) / 2, mode + " option " + i + " x");
				assertEquals(expected[1], plate[1] + 3 + CaptureMenuLabels.LINE_HEIGHT / 2, mode + " option " + i + " y");
			}
		}
	}

	private static List<CaptureMenu.Mode> ringModes() {
		return List.of(CaptureMenu.Mode.BIG_SCREENSHOT, CaptureMenu.Mode.BURST);
	}

	private static List<String> optionLabels(CaptureMenu.Mode mode) {
		return switch (mode) {
			case BIG_SCREENSHOT -> BigScreenshotSize.DIAL_OPTIONS.stream().map(BigScreenshotSize::token).toList();
			case BURST -> Arrays.stream(Burst.DIAL_OPTIONS).mapToObj(seconds -> seconds + "s").toList();
			case SCREENSHOT -> List.of();
		};
	}

	private static List<int[]> ringPlates(CaptureMenu.Mode mode, int[] screen) {
		List<String> labels = optionLabels(mode);
		List<int[]> plates = new ArrayList<>();
		for (int i = 0; i < labels.size(); i++) {
			double angle = CaptureMenuLabels.optionAngle(MODES, mode.ordinal(), labels.size(), i);
			plates.add(CaptureMenuLabels.plate(angle, CaptureMenuLabels.OPTION_LABEL_RADIUS,
					width(labels.get(i)), screen[0], screen[1]));
		}
		return plates;
	}

	private static int width(String text) {
		return text.length() * 6;
	}

	private static boolean intersects(int[] a, int[] b) {
		return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
	}

	private static double distanceToCentre(int[] plate, int[] screen) {
		double cx = screen[0] / 2;
		double cy = screen[1] / 2;
		double nearestX = Math.max(plate[0], Math.min(plate[2], cx));
		double nearestY = Math.max(plate[1], Math.min(plate[3], cy));
		return Math.hypot(nearestX - cx, nearestY - cy);
	}
}
