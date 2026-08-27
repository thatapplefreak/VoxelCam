package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class RenamePopup extends Screen {

	private static final int FIELD_WIDTH = 220;

	private final GuiScreenShotManager parent;
	private final File screenshotsDir;
	private final String originalName;

	private EditBox nameField;
	private Button confirmButton;

	public RenamePopup(GuiScreenShotManager parent, File screenshotsDir, File currentFile) {
		super(Component.translatable("voxelcam.rename"));
		this.parent = parent;
		this.screenshotsDir = screenshotsDir;
		this.originalName = currentFile.getName().replaceFirst("(?i)\\.png$", "");
	}

	@Override
	protected void init() {
		int fieldX = width / 2 - FIELD_WIDTH / 2;
		int fieldY = height / 2 - 10;

		nameField = new EditBox(font, fieldX, fieldY, FIELD_WIDTH, 20, Component.translatable("voxelcam.rename"));
		nameField.setMaxLength(128);
		nameField.setValue(originalName);
		nameField.setResponder(text -> updateConfirmState());
		addRenderableWidget(nameField);
		setInitialFocus(nameField);

		int buttonWidth = (FIELD_WIDTH - 6) / 2;
		confirmButton = addRenderableWidget(Button.builder(Component.translatable("voxelcam.ok"), b -> confirm())
				.bounds(fieldX, fieldY + 28, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(fieldX + buttonWidth + 6, fieldY + 28, buttonWidth, 20).build());

		updateConfirmState();
	}

	/** Blocks names that are empty, unchanged, or would collide with an existing file. */
	private void updateConfirmState() {
		confirmButton.active = canConfirm();
	}

	private String validationError() {
		String name = nameField.getValue().trim();
		if (name.isEmpty()) {
			return null; // Nothing typed yet: just disabled, no scolding.
		}
		if (name.equals(originalName)) {
			return null;
		}
		if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
			return "voxelcam.rename.invalid";
		}
		if (new File(screenshotsDir, name + ".png").exists()) {
			return "voxelcam.rename.exists";
		}
		return null;
	}

	private boolean canConfirm() {
		String name = nameField.getValue().trim();
		return !name.isEmpty() && !name.equals(originalName) && validationError() == null;
	}

	private void confirm() {
		if (!canConfirm()) {
			return;
		}
		VoxelCamIO.rename(screenshotsDir, nameField.getValue().trim());
		onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.input() == GLFW.GLFW_KEY_ENTER || input.input() == GLFW.GLFW_KEY_KP_ENTER) {
			confirm();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		int fieldY = height / 2 - 10;
		context.centeredText(font, title, width / 2, fieldY - 28, 0xFFFFFFFF);

		String error = validationError();
		if (error != null) {
			context.centeredText(font,
					Component.translatable(error).withStyle(ChatFormatting.RED), width / 2, fieldY - 14, 0xFFFF5555);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
