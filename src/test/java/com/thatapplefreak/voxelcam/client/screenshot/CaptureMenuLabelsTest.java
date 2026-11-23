package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Where the outer ring's labels land, and what aiming at them selects, at the screens a player is
 * most likely to have: 854x480 (and 1280x720) at automatic GUI scale is 427x240, vanilla's
 * smallest scaled height, and 320x240 is the narrowest. Heights up to about 290 still pull some
 * labels in, so two of those are checked too. The playtest that found this saw "4k" on the hunger
 * bar and "8k" on the hotbar; the first fix moved them off it but inside the option radius, so
 * steering onto "8k" or "2s" released with the previous size or length.
 *
 * <p>Text widths are vanilla's font advances, as the real client measured them: 5 for {@code f}
 * and {@code k}, 6 for the other characters these labels use.
 */
class CaptureMenuLabelsTest {

	private static final int[][] SMALL_SCREENS = { { 427, 240 }, { 320, 240 }, { 427, 260 }, { 427, 280 } };
	private static final int MODES = CaptureMenu.Mode.values().length;
	private static final Map<String, Integer> MODE_LABEL_WIDTHS = Map.of("Screenshot", 58, "Big Screenshot", 76,
			"Burst", 28);

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

	/** Through the same call a release commits by, from every pixel the label's plate covers. */
	@Test
	void aimingAnywhereOnALabelSelectsThatOption() {
		for (int[] screen : SMALL_SCREENS) {
			int[][] radii = radii(screen);
			for (CaptureMenu.Mode mode : ringModes()) {
				List<int[]> plates = ringPlates(mode, screen);
				for (int i = 0; i < plates.size(); i++) {
					int[] plate = plates.get(i);
					for (int x = plate[0]; x < plate[2]; x++) {
						for (int y = plate[1]; y < plate[3]; y++) {
							double dx = x + 0.5 - screen[0] / 2;
							double dy = y + 0.5 - screen[1] / 2;
							String where = mode + " option " + i + " at " + Arrays.toString(screen) + ", pixel (" + x + ", "
									+ y + ")";
							assertEquals(mode, CaptureMenu.modeForOffset(dx, dy), where);
							assertEquals(i, CaptureMenu.optionForOffset(dx, dy, radii), where);
						}
					}
				}
			}
		}
	}

	/**
	 * Every aim that picked an option before still picks the same one, and anything newly picked
	 * belongs to an option whose label was pulled — so the only change is that option engaging
	 * closer in, never further out and never for a neighbour.
	 */
	@Test
	void loweringAnOptionsRadiusOnlyWidensThatOption() {
		for (int[] screen : SMALL_SCREENS) {
			int[][] radii = radii(screen);
			for (double dx = -130; dx <= 130; dx += 0.5) {
				for (double dy = -130; dy <= 130; dy += 0.5) {
					int before = CaptureMenu.optionForOffset(dx, dy);
					int after = CaptureMenu.optionForOffset(dx, dy, radii);
					String where = "(" + dx + ", " + dy + ") at " + Arrays.toString(screen);
					if (before >= 0) {
						assertEquals(before, after, where);
					} else if (after >= 0) {
						int mode = CaptureMenu.modeForOffset(dx, dy).ordinal();
						assertTrue(radii[mode][after] < CaptureMenu.OPTION_RADIUS, where + " newly selects " + after);
					}
				}
			}
			for (int[] modeRadii : radii) {
				for (int radius : modeRadii) {
					// The arc segment is drawn 3 pixels thick inside its radius, and must not touch the dial.
					assertTrue(radius - 3 > CaptureMenuHud.RADIUS,
							"an option radius of " + radius + " reaches into the dial at " + Arrays.toString(screen));
				}
			}
		}
	}

	/** {@code CaptureMenuTest}'s own vectors, in GUI pixels at 427x240 (its raw moves at scale 2). */
	@Test
	void theCaptureMenuTestsAimsStillSelectWhatTheyDid() {
		int[][] radii = radii(new int[] { 427, 240 });
		assertEquals(-1, CaptureMenu.optionForOffset(65, 37.5, radii));
		assertEquals(-1, CaptureMenu.optionForOffset(-65, 37.5, radii));
		assertEquals(2, CaptureMenu.optionForOffset(195, 112.5, radii));
		assertEquals(-1, CaptureMenu.optionForOffset(0, 50, radii));
		assertEquals(-1, CaptureMenu.optionForOffset(80, 0, radii));
		assertEquals(2, CaptureMenu.optionForOffset(130, 75, radii));
		assertEquals(2, CaptureMenu.optionForOffset(-130, 75, radii));
	}

	@Test
	void ringLabelsClearTheDialEachOtherAndTheModeLabels() {
		// en_us only: the pirate "Flurry o' Maps" already meets "4s" on the Burst ring, a clash in
		// the mode label's own width that is older than this placement and not one it can fix.
		String[] modeNames = { "Screenshot", "Big Screenshot", "Burst" };
		for (int[] screen : SMALL_SCREENS) {
			for (CaptureMenu.Mode mode : ringModes()) {
				List<int[]> plates = ringPlates(mode, screen);
				for (int i = 0; i < plates.size(); i++) {
					int[] plate = plates.get(i);
					assertTrue(CaptureMenuLabels.nearestDistance(plate, screen[0], screen[1]) > CaptureMenuHud.RADIUS,
							mode + " option " + i + " covers the dial at " + Arrays.toString(screen));
					for (int j = i + 1; j < plates.size(); j++) {
						assertFalse(intersects(plate, plates.get(j)), mode + " options " + i + " and " + j);
					}
					for (int m = 0; m < MODES; m++) {
						int[] modePlate = CaptureMenuLabels.plate(m * Math.PI * 2 / MODES,
								CaptureMenuHud.RADIUS + CaptureMenuHud.LABEL_GAP, MODE_LABEL_WIDTHS.get(modeNames[m]),
								screen[0], screen[1]);
						assertFalse(intersects(plate, modePlate),
								mode + " option " + i + " overlaps \"" + modeNames[m] + "\"");
					}
				}
			}
		}
	}

	@Test
	void aTallEnoughScreenKeepsEveryLabelAndRadiusWhereTheyWere() {
		int[] screen = { 960, 540 };
		int[][] radii = radii(screen);
		for (CaptureMenu.Mode mode : ringModes()) {
			List<int[]> plates = ringPlates(mode, screen);
			int[] unchanged = new int[plates.size()];
			Arrays.fill(unchanged, CaptureMenu.OPTION_RADIUS);
			assertArrayEquals(unchanged, radii[mode.ordinal()], mode.toString());
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

	/** What the HUD hands {@code CaptureMenu.setAimOffset} each frame. */
	private static int[][] radii(int[] screen) {
		int[][] radii = new int[MODES][];
		for (CaptureMenu.Mode mode : CaptureMenu.Mode.values()) {
			int[] widths = optionLabels(mode).stream().mapToInt(CaptureMenuLabelsTest::width).toArray();
			radii[mode.ordinal()] = CaptureMenuLabels.optionRadii(MODES, mode.ordinal(), widths, screen[0], screen[1]);
		}
		return radii;
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
		return text.chars().map(c -> c == 'f' || c == 'k' ? 5 : 6).sum();
	}

	private static boolean intersects(int[] a, int[] b) {
		return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
	}
}
