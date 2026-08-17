package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.ScreenshotTextureCache;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import com.thatapplefreak.voxelcam.client.upload.AutoUploader;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the LiteLoader-era GuiScreenShotManager. A functional rewrite
 * against current Screen/Widget APIs rather than a pixel-identical port -
 * the old custom scrolling PhotoSelector/ScalePhotoFrame widgets had no
 * direct modern equivalent worth reimplementing from scratch.
 */
public class GuiScreenShotManager extends Screen {

	private static final int ROW_HEIGHT = 14;
	private static final int VISIBLE_ROWS = 12;
	private static final int LIST_X = 10;
	private static final int LIST_WIDTH = 150;
	private static final int LIST_TOP = 44;
	private static final int FRAME_TOP = 44;

	private final File screenshotsDir;
	private final List<ButtonWidget> rowWidgets = new ArrayList<>();

	private TextFieldWidget searchBar;
	private ButtonWidget renameButton;
	private ButtonWidget deleteButton;
	private ButtonWidget editButton;
	private ButtonWidget postButton;

	private String searchText = "";
	private int scrollOffset = 0;

	public GuiScreenShotManager(File screenshotsDir) {
		super(Text.translatable("voxelcam.screenshots"));
		this.screenshotsDir = screenshotsDir;
	}

	@Override
	protected void init() {
		// clearChildren() may have dropped the previous widgets already; drop our
		// stale references to them too before rebuilding.
		rowWidgets.clear();
		VoxelCamIO.updateScreenShotFilesList(screenshotsDir, searchText);

		searchBar = new TextFieldWidget(textRenderer, LIST_X, 24, LIST_WIDTH, 16, Text.translatable("voxelcam.search"));
		searchBar.setText(searchText);
		// Only the list rows are rebuilt on input, so the field keeps focus and cursor
		// position while typing.
		searchBar.setChangedListener(text -> {
			searchText = text;
			VoxelCamIO.updateScreenShotFilesList(screenshotsDir, text);
			scrollOffset = 0;
			refreshRows();
		});
		addDrawableChild(searchBar);

		addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.back"), b -> close())
				.dimensions(10, height - 30, 70, 20).build());

		renameButton = addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.rename"), b -> {
			File photo = VoxelCamIO.getSelectedPhoto();
			if (photo != null) {
				client.setScreen(new RenamePopup(this, screenshotsDir, photo));
			}
		}).dimensions(width - 220, height - 45, 70, 20).build());

		deleteButton = addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.delete"), b ->
				client.setScreen(DeletePopup.create(this)))
				.dimensions(width - 150, height - 45, 70, 20).build());

		editButton = addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.edit"), b -> {
		}).dimensions(width - 80, height - 45, 70, 20).build());
		editButton.active = false;

		addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.openscreenshotsfolder"), b ->
				Util.getOperatingSystem().open(screenshotsDir))
				.dimensions(width - 220, height - 25, 140, 20).build());

		postButton = addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.postto"), b -> {
			File photo = VoxelCamIO.getSelectedPhoto();
			if (photo != null) {
				AutoUploader.postNow(photo);
			}
		}).dimensions(width - 80, height - 25, 70, 20).build());

		refreshRows();
	}

	/** Rebuilds only the file-list rows and scroll arrows, leaving other widgets untouched. */
	private void refreshRows() {
		for (ButtonWidget widget : rowWidgets) {
			remove(widget);
		}
		rowWidgets.clear();

		List<File> files = VoxelCamIO.getScreenShotFiles();
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = scrollOffset + row;
			if (index >= files.size()) {
				break;
			}
			File file = files.get(index);
			int selectionIndex = index;
			int y = LIST_TOP + row * ROW_HEIGHT;
			rowWidgets.add(addDrawableChild(ButtonWidget.builder(Text.literal(file.getName()), b -> {
				VoxelCamIO.selectPhotoIndex(selectionIndex);
				updateButtonStates();
			}).dimensions(LIST_X, y, LIST_WIDTH, ROW_HEIGHT).build()));
		}

		if (scrollOffset > 0) {
			rowWidgets.add(addDrawableChild(ButtonWidget.builder(Text.literal("^"), b -> {
				scrollOffset = Math.max(0, scrollOffset - VISIBLE_ROWS);
				refreshRows();
			}).dimensions(LIST_X + LIST_WIDTH + 4, LIST_TOP, 16, 16).build()));
		}
		if (scrollOffset + VISIBLE_ROWS < files.size()) {
			rowWidgets.add(addDrawableChild(ButtonWidget.builder(Text.literal("v"), b -> {
				scrollOffset += VISIBLE_ROWS;
				refreshRows();
			}).dimensions(LIST_X + LIST_WIDTH + 4, LIST_TOP + (VISIBLE_ROWS - 1) * ROW_HEIGHT, 16, 16).build()));
		}

		updateButtonStates();
	}

	/**
	 * Called on resize and when this screen is re-shown after a popup closes (the
	 * screen is already initialized, so init() would not run again on its own).
	 * A full rebuild both repositions widgets for the new size and picks up files
	 * renamed or deleted while the popup was open.
	 */
	@Override
	protected void refreshWidgetPositions() {
		clearAndInit();
	}

	private void updateButtonStates() {
		boolean hasSelection = VoxelCamIO.getSelectedPhoto() != null;
		renameButton.active = hasSelection;
		deleteButton.active = hasSelection;
		postButton.active = hasSelection;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Do NOT call renderBackground() here: Screen.renderWithTooltip already does,
		// and its blur pass throws "Can only blur once per frame" if repeated.
		super.render(context, mouseX, mouseY, delta);

		int frameX = LIST_X + LIST_WIDTH + 30;
		int frameY = FRAME_TOP;
		int frameWidth = width - frameX - 10;
		int frameHeight = height - FRAME_TOP - 55;
		if (frameWidth <= 0 || frameHeight <= 0) {
			return;
		}
		context.fill(frameX, frameY, frameX + frameWidth, frameY + frameHeight, 0x80000000);

		File selected = VoxelCamIO.getSelectedPhoto();
		ScreenshotTextureCache.Preview preview = selected == null ? null : ScreenshotTextureCache.get(selected);
		if (preview == null) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("voxelcam.noscreenshots"),
					frameX + frameWidth / 2, frameY + frameHeight / 2 - 4, 0xFFFFFFFF);
			return;
		}

		// Letterbox: scale to fit inside the frame preserving aspect ratio, then centre.
		// The old ScalePhotoFrame did the same; drawing at the frame size directly would
		// squash non-matching aspect ratios (screenshots are rarely square).
		float scale = Math.min((float) frameWidth / preview.width(), (float) frameHeight / preview.height());
		int drawWidth = Math.max(1, Math.round(preview.width() * scale));
		int drawHeight = Math.max(1, Math.round(preview.height() * scale));
		int drawX = frameX + (frameWidth - drawWidth) / 2;
		int drawY = frameY + (frameHeight - drawHeight) / 2;

		context.drawTexture(RenderPipelines.GUI_TEXTURED, preview.id(), drawX, drawY, 0F, 0F,
				drawWidth, drawHeight, preview.width(), preview.height(), preview.width(), preview.height());

		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal(selected.getName().replaceFirst("\\.png$", "")),
				frameX + frameWidth / 2, frameY - 12, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		ScreenshotTextureCache.releaseAll();
		client.setScreen(null);
	}
}
