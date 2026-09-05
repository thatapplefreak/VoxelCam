package com.thatapplefreak.voxelcam.client.screenshot;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws the radial capture menu while {@link CaptureMenu#isOpen()}. Kept separate from the state
 * machine so that one stays renderer-free and unit-testable without a GL context.
 *
 * <p>This is the first use of Fabric API's {@code HudRenderCallback} in this codebase — everything
 * else here draws through the retained-mode {@code Screen}/{@code AbstractWidget} extraction API
 * (see {@code GuiScreenShotManager} and friends), which only runs while a {@code Screen} is open.
 * Whether 26.x's HUD layer still hands out a plain {@code GuiGraphics} for immediate drawing the
 * way it always has, or has moved onto an extraction surface of its own the way {@code Screen}
 * rendering has, could not be confirmed from this environment — no network access to fetch the
 * Fabric API jar. Check the real {@code HudRenderCallback} interface signature first if this does
 * not compile as written.
 */
public final class CaptureMenuHud {

	private static final int RADIUS = 60;
	private static final int MARKER_HALF_SIZE = 14;
	private static final int WEDGE_COLOR = 0x88000000;
	private static final int WEDGE_COLOR_HIGHLIGHT = 0xAA3377FF;
	private static final int LABEL_COLOR = 0xFFFFFFFF;

	private CaptureMenuHud() {
	}

	public static void render(GuiGraphics guiGraphics, DeltaTracker tickCounter) {
		if (!CaptureMenu.isOpen()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Window window = client.getWindow();
		int centerX = guiGraphics.guiWidth() / 2;
		int centerY = guiGraphics.guiHeight() / 2;

		// mouseHandler positions are in screen-pixel space; guiWidth/guiHeight are GUI-scaled, so
		// the offset has to be brought into the same space before it means anything as an angle.
		double scale = window.getGuiScale();
		double dx = client.mouseHandler.xpos() / scale - centerX;
		double dy = client.mouseHandler.ypos() / scale - centerY;
		CaptureMenu.setAimOffset(dx, dy);

		CaptureMenu.Mode aimed = CaptureMenu.aimedMode();
		CaptureMenu.Mode[] modes = CaptureMenu.Mode.values();
		double wedgeWidth = Math.PI * 2 / modes.length;
		Font font = client.font;

		for (int i = 0; i < modes.length; i++) {
			double midAngle = i * wedgeWidth;
			int color = modes[i] == aimed ? WEDGE_COLOR_HIGHLIGHT : WEDGE_COLOR;
			int x = centerX + (int) Math.round(Math.sin(midAngle) * RADIUS);
			int y = centerY - (int) Math.round(Math.cos(midAngle) * RADIUS);

			// A small filled square at the wedge's mid-angle rather than a true pie slice —
			// GuiGraphics has no polygon fill, and a real wedge would need a hand-rolled triangle
			// fan. Good enough to show which mode is aimed at; a nicer shape is a cosmetic
			// follow-up, not something this feature depends on.
			guiGraphics.fill(x - MARKER_HALF_SIZE, y - MARKER_HALF_SIZE,
					x + MARKER_HALF_SIZE, y + MARKER_HALF_SIZE, color);
			guiGraphics.drawCenteredString(font, Component.translatable(labelKey(modes[i])), x, y - 4, LABEL_COLOR);
		}
	}

	private static String labelKey(CaptureMenu.Mode mode) {
		return switch (mode) {
			case SCREENSHOT -> "voxelcam.capturemenu.screenshot";
			case BIG_SCREENSHOT -> "voxelcam.capturemenu.bigscreenshot";
		};
	}
}
