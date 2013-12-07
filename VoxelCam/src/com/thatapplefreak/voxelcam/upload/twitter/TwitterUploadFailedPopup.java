package com.thatapplefreak.voxelcam.upload.twitter;

import net.minecraft.client.gui.GuiScreen;

import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class TwitterUploadFailedPopup extends GuiDialogBox {
	
	private String errorMessage;

	public TwitterUploadFailedPopup(GuiScreen parentScreen, String errorMessage) {
		super(parentScreen, 320, 80, "Post to Twitter failed");
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
		drawCenteredString(fontRenderer, "Post failed", dialogX + (dialogWidth / 2), dialogY + 18, 0xFFFF5555);
		drawCenteredString(fontRenderer, this.errorMessage, dialogX + (dialogWidth / 2), dialogY + 32, 0xFFFFAA00);
	}
}
