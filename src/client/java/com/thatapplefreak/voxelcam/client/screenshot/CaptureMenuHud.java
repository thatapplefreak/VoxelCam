package com.thatapplefreak.voxelcam.client.screenshot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Draws the radial capture menu while {@link CaptureMenu#isOpen()}. Kept separate from the state
 * machine so that one stays renderer-free and unit-testable without a GL context.
 *
 * <p>This is the first thing in VoxelCam to draw during gameplay rather than inside a {@code
 * Screen}. It registers as a Fabric {@code HudElement} (see {@code VoxelCamClient}), which fits the
 * same retained-mode extraction model the rest of the GUI uses — the HUD hands out the very same
 * {@code GuiGraphicsExtractor} the manager's screens extract into.
 *
 * <p>The dial is rasterised on a {@link #CELL}-pixel grid rather than drawn as smooth geometry: the
 * extractor fills axis-aligned rectangles and nothing else, and stepping in visible chunks is what
 * makes it read as part of the game rather than as an overlay drawn on top of it. Each row emits
 * one fill per run of like-coloured cells, so a whole dial costs a couple of hundred rectangles.
 */
public final class CaptureMenuHud {

	private static final int RADIUS = 58;
	/** Grid step. Larger reads chunkier; the dial is quantised to this in both axes. */
	private static final int CELL = 3;
	private static final int RING_THICKNESS = 4;
	private static final int LABEL_GAP = 12;
	/** Vanilla's font is 9px tall; kept as a constant so the plate does not depend on a field. */
	private static final int LINE_HEIGHT = 9;

	/** Thinner than the main dial's ring: the outer ring is a second, lesser signal, not
	 * another thing competing for the eye with the mode the player already committed to. */
	private static final int OPTION_RING_THICKNESS = 3;
	private static final int OPTION_LABEL_GAP = 14;

	private static final int TRANSPARENT = 0;
	private static final int SECTOR = 0x88101010;
	private static final int SECTOR_AIMED = 0xAA4A90E2;
	private static final int RING = 0xFFE8E8E8;
	private static final int DIVIDER = 0xBB000000;
	private static final int LABEL_PLATE = 0xCC101010;
	private static final int LABEL_PLATE_AIMED = 0xEE4A90E2;
	private static final int LABEL_TEXT = 0xFFBBBBBB;
	private static final int LABEL_TEXT_AIMED = 0xFFFFFFFF;

	private CaptureMenuHud() {
	}

	public static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!CaptureMenu.isOpen()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		int centerX = graphics.guiWidth() / 2;
		int centerY = graphics.guiHeight() / 2;

		// Scaled positions, so the aim offset lands in the same space the wedges are laid out in.
		double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
		double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
		CaptureMenu.setAimOffset(mouseX - centerX, mouseY - centerY);

		CaptureMenu.Mode[] modes = CaptureMenu.Mode.values();
		CaptureMenu.Mode aimedMode = CaptureMenu.aimedMode();
		int aimed = aimedMode.ordinal();

		extractDial(graphics, centerX, centerY, modes.length, aimed);

		double wedge = Math.PI * 2 / modes.length;
		for (int i = 0; i < modes.length; i++) {
			extractLabel(graphics, client, centerX, centerY, i * wedge,
					Component.translatable(labelKey(modes[i])), i == aimed, RADIUS + LABEL_GAP);
		}

		// Only drawn once the cursor has actually reached CaptureMenu.OPTION_RADIUS — aimedOption
		// returns -1 both short of that and for a mode with no options, so this single check
		// covers both without the HUD needing to know which one applies.
		int aimedOption = CaptureMenu.aimedOption();
		if (aimedOption >= 0) {
			extractOptionRing(graphics, client, centerX, centerY, aimedMode, aimedOption);
		}
	}

	/**
	 * The dial itself: sector fills, the aimed sector picked out, the outer ring and the dividers
	 * between wedges, all decided per cell and emitted as runs. There is deliberately no marker on
	 * the dead zone — the selection is already shown by which sector is lit, and a hub circle in
	 * the middle of a two-wedge dial just reads as a button.
	 */
	private static void extractDial(GuiGraphicsExtractor graphics, int centerX, int centerY,
			int wedges, int aimed) {
		for (int top = -RADIUS; top <= RADIUS; top += CELL) {
			int runStart = 0;
			int runColor = TRANSPARENT;

			for (int left = -RADIUS; left <= RADIUS; left += CELL) {
				int color = cellColor(left + CELL / 2.0, top + CELL / 2.0, wedges, aimed);
				if (color != runColor) {
					emitRun(graphics, centerX, centerY, runStart, left, top, runColor);
					runStart = left;
					runColor = color;
				}
			}
			emitRun(graphics, centerX, centerY, runStart, RADIUS + CELL, top, runColor);
		}
	}

	private static void emitRun(GuiGraphicsExtractor graphics, int centerX, int centerY,
			int from, int to, int top, int color) {
		if (color != TRANSPARENT && to > from) {
			graphics.fill(centerX + from, centerY + top, centerX + to, centerY + top + CELL, color);
		}
	}

	private static int cellColor(double dx, double dy, int wedges, int aimed) {
		double distance = Math.sqrt(dx * dx + dy * dy);
		if (distance > RADIUS) {
			return TRANSPARENT;
		}
		if (distance > RADIUS - RING_THICKNESS) {
			return RING;
		}

		double wedge = Math.PI * 2 / wedges;
		// Same convention CaptureMenu resolves by: 0 points up and wedge 0 is centred on it.
		double angle = Math.atan2(dx, -dy);

		// Boundaries sit half a wedge off the mid-angles. Scaling the angular gap by the radius
		// keeps a divider the same width all the way out instead of fanning open.
		double past = (((angle + wedge / 2) % wedge) + wedge) % wedge;
		if (distance * Math.min(past, wedge - past) < CELL) {
			return DIVIDER;
		}

		return CaptureMenu.wedgeForAngle(angle, wedges) == aimed ? SECTOR_AIMED : SECTOR;
	}

	/**
	 * The aimed mode's own sub-option arc, rasterised the same run-per-row way {@link
	 * #extractDial} is — but only that one wedge's slice of the ring, not the full circle:
	 * showing options at this radius for every mode at once would read as though all three had
	 * them, when only {@code BIG_SCREENSHOT} and {@code BURST} do. Bounded to {@link
	 * CaptureMenu#OPTION_RADIUS} rather than the dial's own {@code RADIUS}, so this is a second,
	 * separate scan rather than widening the main dial's — paid only while actually aimed past
	 * it, not on every open-menu frame.
	 */
	private static void extractOptionRing(GuiGraphicsExtractor graphics, Minecraft client,
			int centerX, int centerY, CaptureMenu.Mode aimedMode, int aimedOption) {
		List<String> options = optionLabelsFor(aimedMode);
		if (options.isEmpty()) {
			return;
		}
		int wedgeCount = CaptureMenu.Mode.values().length;
		int wedgeIndex = aimedMode.ordinal();

		int radius = CaptureMenu.OPTION_RADIUS;
		for (int top = -radius; top <= radius; top += CELL) {
			int runStart = 0;
			int runColor = TRANSPARENT;
			for (int left = -radius; left <= radius; left += CELL) {
				int color = optionCellColor(left + CELL / 2.0, top + CELL / 2.0,
						wedgeCount, wedgeIndex, options.size(), aimedOption);
				if (color != runColor) {
					emitRun(graphics, centerX, centerY, runStart, left, top, runColor);
					runStart = left;
					runColor = color;
				}
			}
			emitRun(graphics, centerX, centerY, runStart, radius + CELL, top, runColor);
		}

		double wedgeWidth = Math.PI * 2 / wedgeCount;
		double wedgeCenter = wedgeIndex * wedgeWidth;
		double optionWidth = wedgeWidth / options.size();
		for (int i = 0; i < options.size(); i++) {
			double optionAngle = wedgeCenter - wedgeWidth / 2 + optionWidth * (i + 0.5);
			extractLabel(graphics, client, centerX, centerY, optionAngle,
					Component.literal(options.get(i)), i == aimedOption, radius + OPTION_LABEL_GAP);
		}
	}

	private static int optionCellColor(double dx, double dy, int wedgeCount, int wedgeIndex,
			int optionCount, int aimedOption) {
		double distance = Math.sqrt(dx * dx + dy * dy);
		int radius = CaptureMenu.OPTION_RADIUS;
		if (distance > radius || distance < radius - OPTION_RING_THICKNESS) {
			return TRANSPARENT;
		}

		double angle = Math.atan2(dx, -dy);
		if (CaptureMenu.wedgeForAngle(angle, wedgeCount) != wedgeIndex) {
			// Only the aimed wedge's own arc is drawn; elsewhere at this radius stays blank
			// rather than implying every mode has options here.
			return TRANSPARENT;
		}

		double wedgeWidth = Math.PI * 2 / wedgeCount;
		double wedgeCenter = wedgeIndex * wedgeWidth;
		double optionWidth = wedgeWidth / optionCount;
		for (int boundary = 1; boundary < optionCount; boundary++) {
			double boundaryAngle = wedgeCenter - wedgeWidth / 2 + optionWidth * boundary;
			double boundaryDiff = angularDifference(angle, boundaryAngle);
			if (distance * Math.abs(boundaryDiff) < CELL) {
				return DIVIDER;
			}
		}

		int option = CaptureMenu.subOptionForAngle(angle, wedgeCount, wedgeIndex, optionCount);
		return option == aimedOption ? SECTOR_AIMED : SECTOR;
	}

	/** The signed angular distance from {@code b} to {@code a}, normalised to {@code (-pi, pi]}
	 * — sin/cos rather than a modulo, so it never has to reason about which side of a
	 * wrap-around point either angle sits on. */
	private static double angularDifference(double a, double b) {
		double diff = a - b;
		return Math.atan2(Math.sin(diff), Math.cos(diff));
	}

	/** The ring's option labels for {@code mode} — empty for {@code SCREENSHOT}, which has
	 * none. Reads {@code Burst}/{@code BigScreenshotSize}'s own {@code DIAL_OPTIONS} directly
	 * rather than a copy, so the ring can never show a value the mode itself would not apply. */
	private static List<String> optionLabelsFor(CaptureMenu.Mode mode) {
		return switch (mode) {
			case SCREENSHOT -> List.of();
			case BIG_SCREENSHOT -> BigScreenshotSize.DIAL_OPTIONS.stream().map(BigScreenshotSize::token).toList();
			case BURST -> Arrays.stream(Burst.DIAL_OPTIONS).mapToObj(String::valueOf).toList();
		};
	}

	/**
	 * Labels float outside the ring on their wedge's mid-angle, on a plate cut to the text rather
	 * than to a fixed box — a box sized in advance is what let longer names spill past their own
	 * edges. The plate is clamped to the screen so a wedge pointing sideways cannot push its label
	 * off it. {@code radius} is the mode dial's own {@code RADIUS + LABEL_GAP} for a mode label,
	 * or the option ring's for one of its sub-option labels.
	 */
	private static void extractLabel(GuiGraphicsExtractor graphics, Minecraft client,
			int centerX, int centerY, double angle, Component label, boolean aimed, int radius) {
		int x = centerX + (int) Math.round(Math.sin(angle) * radius);
		int y = centerY - (int) Math.round(Math.cos(angle) * radius) - LINE_HEIGHT / 2;

		int half = client.font.width(label) / 2 + 4;
		x = Math.max(half + 2, Math.min(graphics.guiWidth() - half - 2, x));
		y = Math.max(2, Math.min(graphics.guiHeight() - LINE_HEIGHT - 4, y));

		graphics.fill(x - half, y - 3, x + half, y + LINE_HEIGHT + 1,
				aimed ? LABEL_PLATE_AIMED : LABEL_PLATE);
		graphics.centeredText(client.font, label, x, y, aimed ? LABEL_TEXT_AIMED : LABEL_TEXT);
	}

	private static String labelKey(CaptureMenu.Mode mode) {
		return switch (mode) {
			case SCREENSHOT -> "voxelcam.capturemenu.screenshot";
			case BIG_SCREENSHOT -> "voxelcam.capturemenu.bigscreenshot";
			case BURST -> "voxelcam.capturemenu.burst";
		};
	}
}
