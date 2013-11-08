package com.thevoxelbox.voxelcam.popups;

import net.minecraft.src.GuiScreen;

import com.thevoxelbox.common.util.gui.GuiDialogBox;


public class TwitterLoginPopup extends GuiDialogBox {

	public TwitterLoginPopup(GuiScreen parentScreen) {
		super(parentScreen, 210, 90, "Log in to Twitter");
	}

	@Override
	protected void onInitDialog() {
		btnOk.displayString = "Ok";
	}

	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		super.drawDialog(mouseX, mouseY, f);
		drawString(fontRenderer, "To post to Twitter VoxelCam must", width / 2 - (150 / 2), height / 2 - 37, 0xFFFFFF);
		drawString(fontRenderer, "first recieve authorization from Twitter", width / 2 - (150 / 2) - 25, height / 2 - 27, 0xFFFFFF);
		drawString(fontRenderer, "to use your account. If you are not ok", width / 2 - (150 / 2) - 25, height / 2 - 17, 0xFFFFFF);
		drawString(fontRenderer, "with this click \"Cancel\" otherwise click", width / 2 - (150 / 2) - 25, height / 2 - 7, 0xFFFFFF);
		drawString(fontRenderer, "\"Ok\" to continue.", width / 2 - (150 / 2) - 25, height / 2 + 3, 0xFFFFFF);
	}

	@Override
	public boolean validateDialog() {
		mc.displayGuiScreen(new TwitterPINPopup(getParentScreen()));
		return false;
	}

	@Override
	public void onSubmit() {
	}

}
