package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Square camera button added to the title screen. Carries no label of its own,
 * the same way vanilla's icon-only buttons pass an empty message.
 */
public class PhotoButton extends Button {

	public static final int SIZE = 20;

	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(VoxelCamClient.MOD_ID, "textures/photo.png");
	// The camera occupies only a small patch of the top-left corner of the sheet;
	// the rest of the 200x200 image is empty, so blit that patch at native size
	// rather than scaling the whole texture down to a speck.
	private static final int TEXTURE_SIZE = 200;
	private static final float ICON_U = 0F;
	private static final float ICON_V = 2F;
	private static final int ICON_WIDTH = 13;
	private static final int ICON_HEIGHT = 9;

	public PhotoButton(int x, int y, OnPress onPress) {
		super(x, y, SIZE, SIZE, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
		// Doubles as the narration, since the button has no message to read out.
		// Text is spelled out in full because ButtonWidget has a nested class of
		// that name, and an inherited member type shadows a single-type import.
		setTooltip(Tooltip.create(net.minecraft.network.chat.Component.translatable("voxelcam.tooltip.openmanager")));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		// extractWidgetRenderState() is final and calls straight through to here
		// without painting anything, so the button plate is this method's job,
		// exactly as vanilla's own Button does before it draws its label.
		extractDefaultSprite(context);
		// Tinted by the widget's own alpha, the way the default sprite is tinted. The shorter
		// blit overloads hardcode an opaque white, which left the icon fully visible
		// through the title screen's fade-in while everything around it was still fading up.
		context.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
				getX() + (SIZE - ICON_WIDTH) / 2, getY() + (SIZE - ICON_HEIGHT) / 2,
				ICON_U, ICON_V, ICON_WIDTH, ICON_HEIGHT, ICON_WIDTH, ICON_HEIGHT,
				TEXTURE_SIZE, TEXTURE_SIZE, ARGB.white(alpha));
	}
}
