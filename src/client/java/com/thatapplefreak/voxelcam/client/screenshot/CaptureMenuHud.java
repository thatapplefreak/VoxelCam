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
 * <p>The extractor fills axis-aligned rectangles and nothing else, so the ring and its dividers are
 * plotted a pixel at a time and the disc is filled a row at a time. That is not a workaround for a
 * missing rounded-shape call: stepping in whole pixels is what gives the menu the chunky edge the
 * rest of the game's UI has, and it is only ever drawn while the key is held.
 */
public final class CaptureMenuHud {

	private static final int RADIUS = 54;
	/** Matches {@code CaptureMenu.DEAD_ZONE} closely enough to read as the same circle. */
	private static final int HUB_RADIUS = 18;
	private static final int LABEL_GAP = 12;
	private static final int PIXEL = 2;
	/** Vanilla's font is 9px tall; kept as a constant so the plate does not depend on a field. */
	private static final int LINE_HEIGHT = 9;

	private static final int BACKDROP = 0x99101010;
	private static final int RING = 0xFFE0E0E0;
	private static final int DIVIDER = 0xAAE0E0E0;
	private static final int LABEL_PLATE = 0xCC101010;
	private static final int LABEL_PLATE_AIMED = 0xEE3A6FF0;
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

		CaptureMenu.Mode aimed = CaptureMenu.aimedMode();
		CaptureMenu.Mode[] modes = CaptureMenu.Mode.values();
		double wedge = Math.PI * 2 / modes.length;

		fillDisc(graphics, centerX, centerY, RADIUS, BACKDROP);
		strokeCircle(graphics, centerX, centerY, RADIUS, RING);
		strokeCircle(graphics, centerX, centerY, HUB_RADIUS, RING);

		for (int i = 0; i < modes.length; i++) {
			// Boundaries sit halfway between neighbouring mid-angles, since wedge 0 is centred on
			// straight up rather than starting there — the same convention CaptureMenu resolves by.
			strokeSpoke(graphics, centerX, centerY, i * wedge + wedge / 2, HUB_RADIUS, RADIUS);
		}

		for (int i = 0; i < modes.length; i++) {
			extractLabel(graphics, client, centerX, centerY, i * wedge,
					Component.translatable(labelKey(modes[i])), modes[i] == aimed);
		}
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

	/** One filled row per scanline, which is how a circle gets filled out of rectangles. */
	private static void fillDisc(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			int half = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
			if (half > 0) {
				graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
			}
		}
	}

	/** Steps around the circumference in roughly whole pixels, plotting a square at each point. */
	private static void strokeCircle(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int color) {
		int steps = Math.max(48, (int) Math.round(Math.PI * 2 * radius));
		for (int i = 0; i < steps; i++) {
			double angle = (Math.PI * 2 * i) / steps;
			plot(graphics, cx, cy, angle, radius, color);
		}
	}

	private static void strokeSpoke(GuiGraphicsExtractor graphics, int cx, int cy, double angle,
			int from, int to) {
		for (int radius = from; radius <= to; radius++) {
			plot(graphics, cx, cy, angle, radius, DIVIDER);
		}
	}

	private static void plot(GuiGraphicsExtractor graphics, int cx, int cy, double angle, int radius, int color) {
		int x = cx + (int) Math.round(Math.sin(angle) * radius);
		int y = cy - (int) Math.round(Math.cos(angle) * radius);
		graphics.fill(x, y, x + PIXEL, y + PIXEL, color);
	}

	private static String labelKey(CaptureMenu.Mode mode) {
		return switch (mode) {
			case SCREENSHOT -> "voxelcam.capturemenu.screenshot";
			case BIG_SCREENSHOT -> "voxelcam.capturemenu.bigscreenshot";
		};
	}
}
