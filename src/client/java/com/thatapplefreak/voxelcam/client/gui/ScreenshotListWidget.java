package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.screenshot.ScreenshotImageCache;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Scrolling screenshot list. Extends the vanilla entry list so scrollbar,
 * mouse wheel, and keyboard navigation come for free rather than being
 * hand-rolled with paging buttons.
 */
public class ScreenshotListWidget extends ObjectSelectionList<ScreenshotListWidget.ScreenshotEntry> {

	public static final int ROW_HEIGHT = 32;
	private static final int THUMBNAIL_WIDTH = 44;
	private static final int PADDING = 4;
	private static final Identifier STAR_TEXTURE = Identifier.fromNamespaceAndPath(VoxelCamClient.MOD_ID, "textures/star.png");
	private static final int BADGE_SIZE = 10;
	private static final int BADGE_TEXTURE_SIZE = 16;
	private static final int BADGE_GOLD = 0xFFFFD700;

	private final Consumer<File> onSelected;

	public ScreenshotListWidget(Minecraft client, int width, int height, int y, Consumer<File> onSelected) {
		super(client, width, height, y, ROW_HEIGHT);
		this.onSelected = onSelected;
	}

	public void setScreenshots(List<File> files, File selected) {
		clearEntries();
		ScreenshotEntry toSelect = null;
		for (File file : files) {
			ScreenshotEntry entry = new ScreenshotEntry(file);
			addEntry(entry);
			if (file.equals(selected)) {
				toSelect = entry;
			}
		}
		if (toSelect != null) {
			setSelected(toSelect);
		}
	}

	@Override
	public void setSelected(ScreenshotEntry entry) {
		super.setSelected(entry);
		onSelected.accept(entry == null ? null : entry.file);
	}

	@Override
	public int getRowWidth() {
		return getWidth() - 12;
	}

	public class ScreenshotEntry extends ObjectSelectionList.Entry<ScreenshotEntry> {

		private final File file;
		private final String displayName;

		ScreenshotEntry(File file) {
			this.file = file;
			this.displayName = file.getName().replaceFirst("\\.png$", "");
		}

		@Override
		public Component getNarration() {
			return Component.literal(displayName);
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
			ScreenshotListWidget.this.setSelected(this);
			return true;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
			int x = getContentX();
			int y = getContentY();
			int rowWidth = getContentWidth();

			int thumbHeight = ROW_HEIGHT - CONTENT_PADDING * 2;
			int thumbX = x + CONTENT_PADDING;
			int thumbY = y + CONTENT_PADDING;

			ScreenshotImageCache.Loaded thumb = ScreenshotImageCache.get(file, true);
			// Dark plate behind every thumbnail so loading, failed, and letterboxed
			// images all occupy the same footprint instead of the row jumping around.
			context.fill(thumbX, thumbY, thumbX + THUMBNAIL_WIDTH, thumbY + thumbHeight, 0xFF1A1A1A);
			if (thumb != null) {
				float scale = Math.min((float) THUMBNAIL_WIDTH / thumb.width(), (float) thumbHeight / thumb.height());
				int drawWidth = Math.max(1, Math.round(thumb.width() * scale));
				int drawHeight = Math.max(1, Math.round(thumb.height() * scale));
				context.blit(RenderPipelines.GUI_TEXTURED, thumb.id(),
						thumbX + (THUMBNAIL_WIDTH - drawWidth) / 2, thumbY + (thumbHeight - drawHeight) / 2,
						0F, 0F, drawWidth, drawHeight, thumb.width(), thumb.height(), thumb.width(), thumb.height());
			}
			// Badge only appears when starred — an empty corner already reads as "not
			// starred" without a dim placeholder cluttering every other row.
			if (ScreenshotMetadata.isStarred(file)) {
				context.blit(RenderPipelines.GUI_TEXTURED, STAR_TEXTURE,
						thumbX + THUMBNAIL_WIDTH - BADGE_SIZE - 1, thumbY + 1,
						0F, 0F, BADGE_TEXTURE_SIZE, BADGE_TEXTURE_SIZE, BADGE_SIZE, BADGE_SIZE,
						BADGE_TEXTURE_SIZE, BADGE_TEXTURE_SIZE, BADGE_GOLD);
			}

			int textX = thumbX + THUMBNAIL_WIDTH + 6;
			int textWidth = Math.max(0, x + rowWidth - textX - CONTENT_PADDING);
			var textRenderer = Minecraft.getInstance().font;

			String name = textRenderer.plainSubstrByWidth(ScreenshotMetadata.displayName(file), textWidth);
			context.text(textRenderer, Component.literal(name), textX, y + 6, 0xFFFFFFFF);

			ScreenshotMetadata.Dimensions size = ScreenshotMetadata.dimensions(file);
			String subtitle = size == null
					? ScreenshotMetadata.fileSize(file)
					: size.width() + "×" + size.height() + "  ·  " + ScreenshotMetadata.fileSize(file);
			context.text(textRenderer,
					Component.literal(textRenderer.plainSubstrByWidth(subtitle, textWidth)).withStyle(ChatFormatting.GRAY),
					textX, y + 18, 0xFFA0A0A0);
		}
	}
}
