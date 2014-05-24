package com.thatapplefreak.voxelcam.upload.reddit;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class RedditPostPopup extends GuiDialogBox {

	public RedditPostPopup(GuiScreen parentScreen) {
		super(parentScreen, 300, 200, I18n.format("postto") + " Reddit"); //TODO Translate
	}
	
	@Override
	protected void onInitDialog() {
		super.onInitDialog();
		btnOk.displayString = I18n.format("post");
	}

	@Override
	public void onSubmit() {
	}

	@Override
	public boolean validateDialog() {
		return false;
	}

}
