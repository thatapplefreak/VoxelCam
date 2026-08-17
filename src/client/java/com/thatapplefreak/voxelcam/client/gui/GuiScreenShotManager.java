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
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.File;
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

	private final File screenshotsDir;
	private TextFieldWidget searchBar;
	private int scrollOffset = 0;
	private ButtonWidget renameButton;
	private ButtonWidget deleteButton;
	private ButtonWidget openFolderButton;
	private ButtonWidget editButton;
	private ButtonWidget postButton;

	public GuiScreenShotManager(File screenshotsDir) {
		super(Text.translatable("voxelcam.screenshots"));
		this.screenshotsDir = screenshotsDir;
	}

	@Override
	protected void init() {
		VoxelCamIO.updateScreenShotFilesList(screenshotsDir, "");
		rebuild();
	}

	private void rebuild() {
		clearChildren();

		searchBar = new TextFieldWidget(textRenderer, LIST_X, 24, LIST_WIDTH, 16, Text.translatable("voxelcam.search"));
		searchBar.setChangedListener(text -> {
			VoxelCamIO.updateScreenShotFilesList(screenshotsDir, text);
			scrollOffset = 0;
			rebuild();
		});
		addDrawableChild(searchBar);

		List<File> files = VoxelCamIO.getScreenShotFiles();
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = scrollOffset + row;
			if (index >= files.size()) {
				break;
			}
			File file = files.get(index);
			int rowIndex = index;
			int y = 44 + row * ROW_HEIGHT;
			addDrawableChild(ButtonWidget.builder(Text.literal(file.getName()), b -> {
				VoxelCamIO.selectPhotoIndex(rowIndex);
				rebuild();
			}).dimensions(LIST_X, y, LIST_WIDTH, ROW_HEIGHT).build());
		}

		if (scrollOffset > 0) {
			addDrawableChild(ButtonWidget.builder(Text.literal("^"), b -> {
				scrollOffset = Math.max(0, scrollOffset - VISIBLE_ROWS);
				rebuild();
			}).dimensions(LIST_X + LIST_WIDTH + 4, 44, 16, 16).build());
		}
		if (scrollOffset + VISIBLE_ROWS < files.size()) {
			addDrawableChild(ButtonWidget.builder(Text.literal("v"), b -> {
				scrollOffset += VISIBLE_ROWS;
				rebuild();
			}).dimensions(LIST_X + LIST_WIDTH + 4, 44 + (VISIBLE_ROWS - 1) * ROW_HEIGHT, 16, 16).build());
		}

		boolean hasSelection = VoxelCamIO.getSelectedPhoto() != null;

		addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.back"), b -> close())
				.dimensions(10, height - 30, 70, 20).build());

		renameButton = addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.rename"), b ->
				client.setScreen(new RenamePopup(this, screenshotsDir, VoxelCamIO.getSelectedPhoto())))
				.dimensions(width - 220, height - 45, 70, 20).build());
		renameButton.active = hasSelection;

		deleteButton = addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.delete"), b ->
				client.setScreen(DeletePopup.create(this)))
				.dimensions(width - 150, height - 45, 70, 20).build());
		deleteButton.active = hasSelection;

		editButton = addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.edit"), b -> {
		}).dimensions(width - 80, height - 45, 70, 20).build());
		editButton.active = false;

		openFolderButton = addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.openscreenshotsfolder"), b ->
				Util.getOperatingSystem().open(screenshotsDir))
				.dimensions(width - 220, height - 25, 140, 20).build());

		postButton = addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.postto"), b -> {
			File photo = VoxelCamIO.getSelectedPhoto();
			if (photo != null) {
				AutoUploader.postNow(photo);
			}
		}).dimensions(width - 80, height - 25, 70, 20).build());
		postButton.active = hasSelection;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);

		File selected = VoxelCamIO.getSelectedPhoto();
		if (selected != null) {
			Identifier texture = ScreenshotTextureCache.get(selected);
			int previewX = LIST_X + LIST_WIDTH + 30;
			int previewSize = width - previewX - 10;
			if (texture != null && previewSize > 0) {
				context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, previewX, 24, 0F, 0F,
						previewSize, previewSize, previewSize, previewSize);
			}
		}
	}

	@Override
	public void close() {
		ScreenshotTextureCache.releaseAll();
		client.setScreen(null);
	}
}
