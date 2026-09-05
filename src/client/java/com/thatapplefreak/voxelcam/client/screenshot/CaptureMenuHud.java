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
 * {@code GuiGraphicsExtractor} the manager's screens extract into, so {@code fill}/{@code
 * centeredText} work here exactly as they do there.
 */
public final class CaptureMenuHud {

	private static final int RADIUS = 60;
	private static final int MARKER_HALF_SIZE = 14;
	private static final int WEDGE_COLOR = 0x88000000;
	private static final int WEDGE_COLOR_HIGHLIGHT = 0xAA3377FF;
	private static final int LABEL_COLOR = 0xFFFFFFFF;

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
		double wedgeWidth = Math.PI * 2 / modes.length;

		for (int i = 0; i < modes.length; i++) {
			// Wedge 0 sits straight up and the rest run clockwise, matching
			// CaptureMenu.wedgeForAngle's convention — the two have to agree or the highlight
			// lands on a different mode than the release fires.
			double midAngle = i * wedgeWidth;
			int color = modes[i] == aimed ? WEDGE_COLOR_HIGHLIGHT : WEDGE_COLOR;
			int x = centerX + (int) Math.round(Math.sin(midAngle) * RADIUS);
			int y = centerY - (int) Math.round(Math.cos(midAngle) * RADIUS);

			// A filled square at the wedge's mid-angle rather than a true pie slice: the extractor
			// fills rectangles, and a real wedge would need a hand-rolled triangle fan. Enough to
			// show which mode is aimed at; a nicer shape is cosmetic follow-up.
			graphics.fill(x - MARKER_HALF_SIZE, y - MARKER_HALF_SIZE,
					x + MARKER_HALF_SIZE, y + MARKER_HALF_SIZE, color);
			graphics.centeredText(client.font, Component.translatable(labelKey(modes[i])),
					x, y - 4, LABEL_COLOR);
		}
	}

	private static String labelKey(CaptureMenu.Mode mode) {
		return switch (mode) {
			case SCREENSHOT -> "voxelcam.capturemenu.screenshot";
			case BIG_SCREENSHOT -> "voxelcam.capturemenu.bigscreenshot";
		};
	}
}
