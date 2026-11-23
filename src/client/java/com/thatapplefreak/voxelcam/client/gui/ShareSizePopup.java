package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.SmallerShareCopy;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Asked before an animation is sent somewhere when its sharing copy is over Discord's free upload
 * limit: a smaller copy that fits (recommended — it plays right in the chat) or the full
 * resolution anyway (for Nitro, or anywhere else). Not a {@code ConfirmScreen}: that has two
 * buttons, and Escape there answers "no", which here would quietly pick full resolution.
 *
 * <p>Labels are fixed translations, with the size in the message instead, so a test (and {@code
 * clickScreenButton}) can find each button by its key.
 */
public class ShareSizePopup extends Screen {

	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;

	private final Screen parent;
	private final long bytes;
	/** {@code true} for the smaller copy, {@code false} for full resolution. */
	private final BooleanConsumer choice;

	public ShareSizePopup(Screen parent, long bytes, BooleanConsumer choice) {
		super(Component.translatable("voxelcam.share.toobig.title"));
		this.parent = parent;
		this.bytes = bytes;
		this.choice = choice;
	}

	@Override
	protected void init() {
		int x = width / 2 - BUTTON_WIDTH / 2;
		int y = height / 2 - BUTTON_HEIGHT;
		Button smaller = addRenderableWidget(Button.builder(Component.translatable("voxelcam.share.toobig.smaller"),
				b -> choice.accept(true)).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		smaller.setTooltip(Tooltip.create(Component.translatable("voxelcam.tooltip.toobig.smaller")));
		y += BUTTON_HEIGHT + GAP;
		Button full = addRenderableWidget(Button.builder(Component.translatable("voxelcam.share.toobig.full"),
				b -> choice.accept(false)).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		full.setTooltip(Tooltip.create(Component.translatable("voxelcam.tooltip.toobig.full")));
		y += BUTTON_HEIGHT + GAP * 2;
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		int top = height / 2 - BUTTON_HEIGHT - 34;
		context.centeredText(font, title, width / 2, top, 0xFFFFFFFF);
		context.centeredText(font, Component.translatable("voxelcam.share.toobig.message",
				SmallerShareCopy.megabytes(bytes), SmallerShareCopy.DISCORD_LIMIT_BYTES / 1_000_000),
				width / 2, top + 14, 0xFFA0A0A0);
	}

	/** Escape and Cancel both go back without sending anything. */
	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
