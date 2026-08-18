package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.io.File;

public class RenamePopup extends Screen {

	private static final int FIELD_WIDTH = 220;

	private final GuiScreenShotManager parent;
	private final File screenshotsDir;
	private final String originalName;

	private TextFieldWidget nameField;
	private ButtonWidget confirmButton;

	public RenamePopup(GuiScreenShotManager parent, File screenshotsDir, File currentFile) {
		super(Text.translatable("voxelcam.rename"));
		this.parent = parent;
		this.screenshotsDir = screenshotsDir;
		this.originalName = currentFile.getName().replaceFirst("(?i)\\.png$", "");
	}

	@Override
	protected void init() {
		int fieldX = width / 2 - FIELD_WIDTH / 2;
		int fieldY = height / 2 - 10;

		nameField = new TextFieldWidget(textRenderer, fieldX, fieldY, FIELD_WIDTH, 20, Text.translatable("voxelcam.rename"));
		nameField.setMaxLength(128);
		nameField.setText(originalName);
		nameField.setChangedListener(text -> updateConfirmState());
		addDrawableChild(nameField);
		setInitialFocus(nameField);

		int buttonWidth = (FIELD_WIDTH - 6) / 2;
		confirmButton = addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.ok"), b -> confirm())
				.dimensions(fieldX, fieldY + 28, buttonWidth, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
				.dimensions(fieldX + buttonWidth + 6, fieldY + 28, buttonWidth, 20).build());

		updateConfirmState();
	}

	/** Blocks names that are empty, unchanged, or would collide with an existing file. */
	private void updateConfirmState() {
		confirmButton.active = canConfirm();
	}

	private String validationError() {
		String name = nameField.getText().trim();
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
		String name = nameField.getText().trim();
		return !name.isEmpty() && !name.equals(originalName) && validationError() == null;
	}

	private void confirm() {
		if (!canConfirm()) {
			return;
		}
		VoxelCamIO.rename(screenshotsDir, nameField.getText().trim());
		close();
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.getKeycode() == GLFW.GLFW_KEY_ENTER || input.getKeycode() == GLFW.GLFW_KEY_KP_ENTER) {
			confirm();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int fieldY = height / 2 - 10;
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, fieldY - 28, 0xFFFFFFFF);

		String error = validationError();
		if (error != null) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable(error).formatted(Formatting.RED), width / 2, fieldY - 14, 0xFFFF5555);
		}
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
