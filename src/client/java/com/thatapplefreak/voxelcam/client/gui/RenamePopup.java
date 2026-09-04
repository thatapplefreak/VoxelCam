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
	private final File currentFile;
	private final String originalName;

	private EditBox nameField;
	private Button confirmButton;
	private boolean renameFailed;

	public RenamePopup(GuiScreenShotManager parent, File screenshotsDir, File currentFile) {
		super(Component.translatable("voxelcam.rename"));
		this.parent = parent;
		this.screenshotsDir = screenshotsDir;
		this.currentFile = currentFile;
		this.originalName = currentFile.getName().replaceFirst("(?i)\\.png$", "");
	}

	@Override
	protected void init() {
		int fieldX = width / 2 - FIELD_WIDTH / 2;
		int fieldY = height / 2 - 10;

		nameField = new EditBox(font, fieldX, fieldY, FIELD_WIDTH, 20, Component.translatable("voxelcam.rename"));
		nameField.setMaxLength(128);
		nameField.setValue(originalName);
		nameField.setResponder(text -> {
			// Typing is the retry, so a "could not rename" must not outlive the name it
			// was about. A rebuild is not: init() runs again on every resize.
			renameFailed = false;
			updateConfirmState();
		});
		addRenderableWidget(nameField);

		int buttonWidth = (FIELD_WIDTH - 6) / 2;
		confirmButton = addRenderableWidget(Button.builder(Component.translatable("voxelcam.ok"), b -> confirm())
				.bounds(fieldX, fieldY + 28, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(fieldX + buttonWidth + 6, fieldY + 28, buttonWidth, 20).build());

		updateConfirmState();
	}

	/**
	 * Vanilla's idiom (DirectJoinServerScreen, AnvilScreen) for a screen whose text field
	 * should start focused. Both {@code Screen.init(int,int)} and {@code rebuildWidgets()}
	 * call this hook <em>after</em> {@code init()} returns, so calling
	 * {@code setInitialFocus(nameField)} from {@code init()} instead is undone: when the last
	 * input was a keyboard one the hook's forward tab navigation starts past the already
	 * focused field, skips the still-inactive OK button and lands on Cancel — leaving the
	 * dialog inert, since typing then goes to a button and Enter confirms nothing.
	 */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(nameField);
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
		// Not a bare exists() probe: on a case-insensitive filesystem the candidate for a
		// case-only rename is the file being renamed, and reporting that as a collision
		// leaves the player unable to ever recapitalise a name.
		if (VoxelCamIO.nameCollides(screenshotsDir, name, currentFile)) {
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
		if (VoxelCamIO.rename(screenshotsDir, nameField.getValue().trim()) == null) {
			// The file kept its old name and the manager would re-list it with nothing to
			// say why, so the dialog stays up instead — holding the typed name for a retry,
			// which is all a Windows-forbidden character or a moved file needs. The reason
			// is only in the log: renameTo has none to give.
			renameFailed = true;
			return;
		}
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

		// A rename the filesystem refused outranks the validation line: the name in the
		// field passed every check this screen can make, so there is nothing else to say.
		String error = renameFailed ? "voxelcam.rename.failed" : validationError();
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
