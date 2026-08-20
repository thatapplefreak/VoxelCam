package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

/**
 * Square camera button added to the title screen. Carries no label of its own,
 * the same way vanilla's icon-only buttons pass an empty message.
 */
public class PhotoButton extends ButtonWidget {

	public static final int SIZE = 20;

	private static final Identifier TEXTURE = Identifier.of(VoxelCamClient.MOD_ID, "textures/photo.png");
	// The camera occupies only a small patch of the top-left corner of the sheet;
	// the rest of the 200x200 image is empty, so blit that patch at native size
	// rather than scaling the whole texture down to a speck.
	private static final int TEXTURE_SIZE = 200;
	private static final float ICON_U = 0F;
	private static final float ICON_V = 2F;
	private static final int ICON_WIDTH = 13;
	private static final int ICON_HEIGHT = 9;

	public PhotoButton(int x, int y, PressAction onPress) {
		super(x, y, SIZE, SIZE, ScreenTexts.EMPTY, onPress, DEFAULT_NARRATION_SUPPLIER);
		// Doubles as the narration, since the button has no message to read out.
		// Text is spelled out in full because ButtonWidget has a nested class of
		// that name, and an inherited member type shadows a single-type import.
		setTooltip(Tooltip.of(net.minecraft.text.Text.translatable("voxelcam.tooltip.openmanager")));
	}

	@Override
	protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
		// renderWidget() calls straight through to here without painting anything,
		// so the button plate is this method's job, exactly as vanilla's own
		// ButtonWidget.Text does before it draws its label.
		drawButton(context);
		// Tinted by the widget's own alpha, the way drawButton tints the plate. The shorter
		// drawTexture overloads hardcode an opaque white, which left the icon fully visible
		// through the title screen's fade-in while everything around it was still fading up.
		context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE,
				getX() + (SIZE - ICON_WIDTH) / 2, getY() + (SIZE - ICON_HEIGHT) / 2,
				ICON_U, ICON_V, ICON_WIDTH, ICON_HEIGHT, ICON_WIDTH, ICON_HEIGHT,
				TEXTURE_SIZE, TEXTURE_SIZE, ColorHelper.getWhite(alpha));
	}
}
