package com.thatapplefreak.voxelcam.upload.twitter;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

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
		drawString(fontRendererObj, I18n.format("twitauthline1"), width / 2 - (150 / 2), height / 2 - 37, 0xFFFFFF);
		drawString(fontRendererObj, I18n.format("twitauthline2"), width / 2 - (150 / 2) - 25, height / 2 - 27, 0xFFFFFF);
		drawString(fontRendererObj, I18n.format("twitauthline3"), width / 2 - (150 / 2) - 25, height / 2 - 17, 0xFFFFFF);
		drawString(fontRendererObj, I18n.format("twitauthline4"), width / 2 - (150 / 2) - 25, height / 2 - 7, 0xFFFFFF);
		drawString(fontRendererObj, I18n.format("twitauthline5"), width / 2 - (150 / 2) - 25, height / 2 + 3, 0xFFFFFF);
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
