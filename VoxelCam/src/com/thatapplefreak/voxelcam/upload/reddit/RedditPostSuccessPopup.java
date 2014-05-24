package com.thatapplefreak.voxelcam.upload.reddit;

import java.net.URL;

import net.minecraft.client.gui.GuiScreen;

import com.thatapplefreak.voxelcam.imagehandle.ScreenshotIncapable;
import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class RedditPostSuccessPopup extends GuiDialogBox implements ScreenshotIncapable {
	
	private URL post;

	public RedditPostSuccessPopup(GuiScreen parentScreen, URL postUrl) {
		super(parentScreen, 320, 80, "Post to Reddit succeeded");
		this.post = postUrl;
	}

	@Override
	public void onSubmit() {
	}

	@Override
	public boolean validateDialog() {
		return false;
	}

}
