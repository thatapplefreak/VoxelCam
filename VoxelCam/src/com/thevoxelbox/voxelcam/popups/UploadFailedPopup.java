package com.thevoxelbox.voxelcam.popups;

import net.minecraft.src.GuiScreen;

import com.thevoxelbox.common.util.gui.GuiDialogBox;


public class UploadFailedPopup extends GuiDialogBox {
	private String errorMessage;

	public UploadFailedPopup(GuiScreen parentScreen, String windowTitle, String errorMessage) {
		super(parentScreen, 300, 80, windowTitle);
		this.errorMessage = errorMessage;
	}

	@Override
	protected void onInitDialog() {
		btnOk.displayString = "Close";
		btnCancel.drawButton = false;
	}

	@Override
	public void onSubmit() {
	}

	@Override
	public boolean validateDialog() {
		return true;
	}

	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		drawCenteredString(fontRenderer, "Upload failed", dialogX + (dialogWidth / 2), dialogY + 18, 0xFFFF5555);
		drawCenteredString(fontRenderer, this.errorMessage, dialogX + (dialogWidth / 2), dialogY + 32, 0xFFFFAA00);
	}
}
