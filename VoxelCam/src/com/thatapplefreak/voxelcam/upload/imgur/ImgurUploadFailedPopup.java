package com.thatapplefreak.voxelcam.upload.imgur;

import net.minecraft.client.gui.GuiScreen;

import com.thevoxelbox.common.util.gui.GuiDialogBox;


public class ImgurUploadFailedPopup extends GuiDialogBox {
	
	protected String errorMessage;

	public ImgurUploadFailedPopup(GuiScreen parentScreen, String errorMessage) {
		super(parentScreen, 300, 80, "Upload to Imgur failed");
		this.errorMessage = errorMessage;
	}

	@Override
	protected void onInitDialog() {
		btnOk.displayString = "Close";
		btnCancel.enabled = false;
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
		drawCenteredString(fontRendererObj, "Upload failed", dialogX + (dialogWidth / 2), dialogY + 18, 0xFFFF5555);
		drawCenteredString(fontRendererObj, this.errorMessage, dialogX + (dialogWidth / 2), dialogY + 32, 0xFFFFAA00);
	}
}
