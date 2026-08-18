package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.ScreenshotImageCache;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;

/**
 * Screenshot browser: searchable list on the left, large preview and details
 * on the right, actions along the bottom.
 */
public class GuiScreenShotManager extends Screen {

	private static final int MARGIN = 10;
	private static final int GAP = 6;
	private static final int BUTTON_HEIGHT = 20;
	private static final int CONTENT_TOP = 52;
	/** Preview : list width ratio. */
	private static final float GOLDEN_RATIO = 1.618034F;
	/** Below this a row cannot fit its thumbnail and a usable slice of the name. */
	private static final int MIN_LIST_WIDTH = 150;

	private final File screenshotsDir;

	private ScreenshotListWidget list;
	private TextFieldWidget searchBar;
	private ButtonWidget renameButton;
	private ButtonWidget deleteButton;
	private ButtonWidget shareButton;

	private String searchText = "";
	private File selected;

	private int previewX;
	private int previewY;
	private int previewWidth;
	private int previewHeight;

	public GuiScreenShotManager(File screenshotsDir) {
		super(Text.translatable("voxelcam.screenshots"));
		this.screenshotsDir = screenshotsDir;
	}

	@Override
	protected void init() {
		VoxelCamIO.updateScreenShotFilesList(screenshotsDir, searchText);
		List<File> files = VoxelCamIO.getScreenShotFiles();
		if (selected == null || !files.contains(selected)) {
			selected = files.isEmpty() ? null : files.get(0);
		}

		// Split the content area so preview : list == phi : 1, which holds the two
		// panes in proportion at every GUI scale instead of the preview swallowing
		// all the extra room once a capped list stops growing.
		int contentWidth = width - MARGIN * 2 - GAP * 2;
		int listWidth = Math.max(MIN_LIST_WIDTH, Math.round(contentWidth / (1F + GOLDEN_RATIO)));
		int actionsY = height - MARGIN - BUTTON_HEIGHT;
		int listBottom = actionsY - GAP;

		searchBar = new TextFieldWidget(textRenderer, MARGIN, 26, listWidth, 18, Text.translatable("voxelcam.search"));
		searchBar.setPlaceholder(Text.translatable("voxelcam.search").formatted(Formatting.DARK_GRAY));
		searchBar.setText(searchText);
		searchBar.setChangedListener(this::onSearchChanged);
		addDrawableChild(searchBar);

		list = new ScreenshotListWidget(client, listWidth, listBottom - CONTENT_TOP, CONTENT_TOP, this::onSelected);
		list.setX(MARGIN);
		addDrawableChild(list);

		previewX = MARGIN + listWidth + GAP * 2;
		previewY = CONTENT_TOP;
		previewWidth = width - previewX - MARGIN;
		// Leave a line under the preview for the resolution/size/date details.
		previewHeight = listBottom - CONTENT_TOP - 14;

		// Item actions sit under the preview they act on; folder/close are global and
		// sit under the list, so destructive per-file actions are not next to "Done".
		// Widths divide the preview column rather than taking a floor, so a narrow
		// preview shrinks these buttons instead of pushing them over the list's.
		int actionWidth = (previewWidth - GAP * 2) / 3;
		int deleteX = previewX + actionWidth + GAP;
		int postX = previewX + (actionWidth + GAP) * 2;

		renameButton = addDrawableChild(button("voxelcam.rename", "voxelcam.tooltip.rename",
				b -> renameSelected(), previewX, actionsY, actionWidth));
		deleteButton = addDrawableChild(button("voxelcam.delete", "voxelcam.tooltip.delete",
				b -> client.setScreen(DeletePopup.create(this)), deleteX, actionsY, actionWidth));
		// Absorbs the rounding remainder so the row ends flush with the preview.
		shareButton = addDrawableChild(button("voxelcam.share", "voxelcam.tooltip.share",
				b -> shareSelected(), postX, actionsY, previewX + previewWidth - postX));

		int leftWidth = Math.round((listWidth - GAP) / 2F);
		addDrawableChild(button("voxelcam.openscreenshotsfolder.short", "voxelcam.tooltip.openfolder",
				b -> Util.getOperatingSystem().open(screenshotsDir), MARGIN, actionsY, leftWidth));
		addDrawableChild(button("voxelcam.done", null, b -> close(),
				MARGIN + leftWidth + GAP, actionsY, listWidth - leftWidth - GAP));

		// Populated only once the action buttons exist, since selecting an entry
		// immediately calls back into updateButtonStates().
		list.setScreenshots(files, selected);
		updateButtonStates();
	}

	private ButtonWidget button(String labelKey, String tooltipKey, ButtonWidget.PressAction action,
			int x, int y, int buttonWidth) {
		ButtonWidget widget = ButtonWidget.builder(Text.translatable(labelKey), action)
				.dimensions(x, y, buttonWidth, BUTTON_HEIGHT).build();
		if (tooltipKey != null) {
			widget.setTooltip(Tooltip.of(Text.translatable(tooltipKey)));
		}
		return widget;
	}

	private void onSearchChanged(String text) {
		if (text.equals(searchText)) {
			return;
		}
		searchText = text;
		VoxelCamIO.updateScreenShotFilesList(screenshotsDir, text);
		List<File> files = VoxelCamIO.getScreenShotFiles();
		if (selected == null || !files.contains(selected)) {
			selected = files.isEmpty() ? null : files.get(0);
		}
		// Only the list contents change while typing, so the search field itself is
		// left alone and keeps focus and cursor position.
		list.setScreenshots(files, selected);
		updateButtonStates();
	}

	private void onSelected(File file) {
		selected = file;
		VoxelCamIO.selectPhoto(file);
		updateButtonStates();
	}

	private void updateButtonStates() {
		// The list can report a selection before init() has built the buttons.
		if (renameButton == null) {
			return;
		}
		boolean hasSelection = selected != null;
		renameButton.active = hasSelection;
		deleteButton.active = hasSelection;
		shareButton.active = hasSelection;
	}

	private void renameSelected() {
		if (selected != null) {
			client.setScreen(new RenamePopup(this, screenshotsDir, selected));
		}
	}

	private void shareSelected() {
		if (selected != null) {
			client.setScreen(new SharePopup(this, selected));
		}
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		// Shortcuts only make sense when not typing a search query.
		if (!searchBar.isFocused() && selected != null) {
			switch (input.getKeycode()) {
				case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
					client.setScreen(DeletePopup.create(this));
					return true;
				}
				case GLFW.GLFW_KEY_F2 -> {
					renameSelected();
					return true;
				}
				default -> {
				}
			}
		}
		return super.keyPressed(input);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Textures decoded on the loader threads can only be uploaded here, on the
		// render thread.
		ScreenshotImageCache.uploadPending();

		// Do NOT call renderBackground() here: Screen.renderWithTooltip already does,
		// and its blur pass throws "Can only blur once per frame" if repeated.
		super.render(context, mouseX, mouseY, delta);

		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);

		int count = VoxelCamIO.getScreenShotFiles().size();
		Text countText = Text.translatable(count == 1 ? "voxelcam.count.one" : "voxelcam.count.many", count);
		context.drawTextWithShadow(textRenderer, countText.copy().formatted(Formatting.GRAY),
				width - MARGIN - textRenderer.getWidth(countText), 31, 0xFFA0A0A0);

		renderPreview(context);
	}

	private void renderPreview(DrawContext context) {
		if (previewWidth <= 0 || previewHeight <= 0) {
			return;
		}
		context.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0x66000000);

		if (selected == null) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("voxelcam.noscreenshots"),
					previewX + previewWidth / 2, previewY + previewHeight / 2 - 4, 0xFF808080);
			return;
		}

		ScreenshotImageCache.Loaded image = ScreenshotImageCache.get(selected, false);
		if (image == null) {
			Text status = ScreenshotImageCache.hasFailed(selected, false)
					? Text.translatable("voxelcam.loadfailed")
					: Text.translatable("voxelcam.loading");
			context.drawCenteredTextWithShadow(textRenderer, status,
					previewX + previewWidth / 2, previewY + previewHeight / 2 - 4, 0xFF808080);
		} else {
			// Letterbox: scale to fit, preserving aspect ratio, then centre.
			float scale = Math.min((float) previewWidth / image.width(), (float) previewHeight / image.height());
			int drawWidth = Math.max(1, Math.round(image.width() * scale));
			int drawHeight = Math.max(1, Math.round(image.height() * scale));
			context.drawTexture(RenderPipelines.GUI_TEXTURED, image.id(),
					previewX + (previewWidth - drawWidth) / 2, previewY + (previewHeight - drawHeight) / 2,
					0F, 0F, drawWidth, drawHeight, image.width(), image.height(), image.width(), image.height());
		}

		// The list row already carries the capture time, so this line stays short
		// enough not to be trimmed: the filename (which the row may show as a
		// friendly time instead) plus the facts the row has no space for.
		StringBuilder details = new StringBuilder(selected.getName());
		ScreenshotMetadata.Dimensions size = ScreenshotMetadata.dimensions(selected);
		if (size != null) {
			details.append("  ·  ").append(size.width()).append('×').append(size.height());
		}
		details.append("  ·  ").append(ScreenshotMetadata.fileSize(selected));
		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal(textRenderer.trimToWidth(details.toString(), previewWidth)).formatted(Formatting.GRAY),
				previewX + previewWidth / 2, previewY + previewHeight + 4, 0xFFA0A0A0);
	}

	/**
	 * Called on resize and when this screen is re-shown after a popup closes (the
	 * screen is already initialized, so init() would not run again on its own).
	 * A full rebuild both repositions widgets and picks up files renamed or
	 * deleted while the popup was open.
	 */
	@Override
	protected void refreshWidgetPositions() {
		clearAndInit();
	}

	@Override
	public void close() {
		ScreenshotImageCache.releaseAll();
		ScreenshotMetadata.forgetAll();
		client.setScreen(null);
	}
}
