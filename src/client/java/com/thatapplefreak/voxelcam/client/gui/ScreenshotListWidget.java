package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.ScreenshotImageCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Scrolling screenshot list. Extends the vanilla entry list so scrollbar,
 * mouse wheel, and keyboard navigation come for free rather than being
 * hand-rolled with paging buttons.
 */
public class ScreenshotListWidget extends AlwaysSelectedEntryListWidget<ScreenshotListWidget.ScreenshotEntry> {

	public static final int ROW_HEIGHT = 32;
	private static final int THUMBNAIL_WIDTH = 44;
	private static final int PADDING = 4;

	private final Consumer<File> onSelected;

	public ScreenshotListWidget(MinecraftClient client, int width, int height, int y, Consumer<File> onSelected) {
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

	public class ScreenshotEntry extends AlwaysSelectedEntryListWidget.Entry<ScreenshotEntry> {

		private final File file;
		private final String displayName;

		ScreenshotEntry(File file) {
			this.file = file;
			this.displayName = file.getName().replaceFirst("\\.png$", "");
		}

		@Override
		public Text getNarration() {
			return Text.literal(displayName);
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
			ScreenshotListWidget.this.setSelected(this);
			return true;
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
			int x = getContentX();
			int y = getContentY();
			int rowWidth = getContentWidth();

			int thumbHeight = ROW_HEIGHT - PADDING * 2;
			int thumbX = x + PADDING;
			int thumbY = y + PADDING;

			ScreenshotImageCache.Loaded thumb = ScreenshotImageCache.get(file, true);
			// Dark plate behind every thumbnail so loading, failed, and letterboxed
			// images all occupy the same footprint instead of the row jumping around.
			context.fill(thumbX, thumbY, thumbX + THUMBNAIL_WIDTH, thumbY + thumbHeight, 0xFF1A1A1A);
			if (thumb != null) {
				float scale = Math.min((float) THUMBNAIL_WIDTH / thumb.width(), (float) thumbHeight / thumb.height());
				int drawWidth = Math.max(1, Math.round(thumb.width() * scale));
				int drawHeight = Math.max(1, Math.round(thumb.height() * scale));
				context.drawTexture(RenderPipelines.GUI_TEXTURED, thumb.id(),
						thumbX + (THUMBNAIL_WIDTH - drawWidth) / 2, thumbY + (thumbHeight - drawHeight) / 2,
						0F, 0F, drawWidth, drawHeight, thumb.width(), thumb.height(), thumb.width(), thumb.height());
			}

			int textX = thumbX + THUMBNAIL_WIDTH + 6;
			int textWidth = Math.max(0, x + rowWidth - textX - PADDING);
			var textRenderer = MinecraftClient.getInstance().textRenderer;

			String name = textRenderer.trimToWidth(ScreenshotMetadata.displayName(file), textWidth);
			context.drawTextWithShadow(textRenderer, Text.literal(name), textX, y + 6, 0xFFFFFFFF);

			ScreenshotMetadata.Dimensions size = ScreenshotMetadata.dimensions(file);
			String subtitle = size == null
					? ScreenshotMetadata.fileSize(file)
					: size.width() + "×" + size.height() + "  ·  " + ScreenshotMetadata.fileSize(file);
			context.drawTextWithShadow(textRenderer,
					Text.literal(textRenderer.trimToWidth(subtitle, textWidth)).formatted(Formatting.GRAY),
					textX, y + 18, 0xFFA0A0A0);
		}
	}
}
