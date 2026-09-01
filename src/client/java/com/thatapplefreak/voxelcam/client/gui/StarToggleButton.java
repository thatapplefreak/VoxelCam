package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Square icon-only button used both for the "favorite this shot" action and the "favorites
 * only" list filter — the same star glyph, tinted gold when its state is on and dim grey
 * otherwise, rather than shipping a second outline texture for the off state.
 */
public class StarToggleButton extends Button {

	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(VoxelCamClient.MOD_ID, "textures/star.png");
	private static final int ICON_SIZE = 16;
	private static final int GOLD = 0xFFD700;
	private static final int DIM_GREY = 0x606060;

	// Named "starred" rather than "active" — AbstractWidget already has a public boolean
	// field of that name for enabled/disabled, and shadowing it here would be a trap.
	private final BooleanSupplier starred;

	/** Square icon button; {@code size} matches whatever row it sits in (18 next to the
	 * search bar, {@code BUTTON_HEIGHT} next to the other bottom-row action buttons). */
	public StarToggleButton(int x, int y, int size, BooleanSupplier starred, OnPress onPress, Component tooltip) {
		super(x, y, size, size, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
		this.starred = starred;
		setTooltip(Tooltip.create(tooltip));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		extractDefaultSprite(context);
		int tint = ARGB.color((int) (alpha * 255F), starred.getAsBoolean() ? GOLD : DIM_GREY);
		context.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
				getX() + (getWidth() - ICON_SIZE) / 2, getY() + (getHeight() - ICON_SIZE) / 2,
				0F, 0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, tint);
	}
}
