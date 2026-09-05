package com.thatapplefreak.voxelcam.client.screenshot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

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
		int aimed = CaptureMenu.aimedMode().ordinal();

		extractDial(graphics, centerX, centerY, modes.length, aimed);

		double wedge = Math.PI * 2 / modes.length;
		for (int i = 0; i < modes.length; i++) {
			extractLabel(graphics, client, centerX, centerY, i * wedge,
					Component.translatable(labelKey(modes[i])), i == aimed);
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
	 * Labels float outside the ring on their wedge's mid-angle, on a plate cut to the text rather
	 * than to a fixed box — a box sized in advance is what let longer names spill past their own
	 * edges. The plate is clamped to the screen so a wedge pointing sideways cannot push its label
	 * off it.
	 */
	private static void extractLabel(GuiGraphicsExtractor graphics, Minecraft client,
			int centerX, int centerY, double angle, Component label, boolean aimed) {
		int x = centerX + (int) Math.round(Math.sin(angle) * (RADIUS + LABEL_GAP));
		int y = centerY - (int) Math.round(Math.cos(angle) * (RADIUS + LABEL_GAP)) - LINE_HEIGHT / 2;

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
		};
	}
}
