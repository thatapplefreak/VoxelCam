package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.File;

public class RenamePopup extends Screen {

	private final GuiScreenShotManager parent;
	private final File screenshotsDir;
	private final String originalName;
	private TextFieldWidget nameField;

	public RenamePopup(GuiScreenShotManager parent, File screenshotsDir, File currentFile) {
		super(Text.translatable("voxelcam.rename"));
		this.parent = parent;
		this.screenshotsDir = screenshotsDir;
		String name = currentFile.getName();
		this.originalName = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
	}

	@Override
	protected void init() {
		nameField = new TextFieldWidget(textRenderer, width / 2 - 100, height / 2 - 10, 200, 20, Text.translatable("voxelcam.rename"));
		nameField.setText(originalName);
		addDrawableChild(nameField);
		setInitialFocus(nameField);

		addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.ok"), b -> confirm())
				.dimensions(width / 2 - 100, height / 2 + 15, 95, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.back"), b -> close())
				.dimensions(width / 2 + 5, height / 2 + 15, 95, 20).build());
	}

	private void confirm() {
		String newName = nameField.getText().trim();
		if (!newName.isEmpty()) {
			VoxelCamIO.rename(screenshotsDir, newName);
		}
		close();
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
