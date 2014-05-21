package com.thatapplefreak.voxelcam.upload.imgur;

import net.minecraft.client.gui.GuiScreen;

import com.thatapplefreak.voxelcam.lang.Translator;
import com.thevoxelbox.common.util.gui.GuiDialogBox;


public class ImgurUploadFailedPopup extends GuiDialogBox {
	
	protected String errorMessage;

	public ImgurUploadFailedPopup(GuiScreen parentScreen, String errorMessage) {
		super(parentScreen, 300, 80, Translator.translate("imguruploadfail"));
		this.errorMessage = errorMessage;
	}

	@Override
	protected void onInitDialog() {
		btnOk.displayString = Translator.translate("close");
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
		drawCenteredString(fontRendererObj, Translator.translate("uploadfailed"), dialogX + (dialogWidth / 2), dialogY + 18, 0xFFFF5555);
		drawCenteredString(fontRendererObj, this.errorMessage, dialogX + (dialogWidth / 2), dialogY + 32, 0xFFFFAA00);
	}
}
