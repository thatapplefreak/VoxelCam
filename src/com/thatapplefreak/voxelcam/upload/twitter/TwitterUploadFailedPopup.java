package com.thatapplefreak.voxelcam.upload.twitter;

import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.TwitterException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class TwitterUploadFailedPopup extends GuiDialogBox {
	
	private String errorMessage;
	
	protected StatusUpdate failedUpdate;
	
	private boolean retrying = false;

	public TwitterUploadFailedPopup(TwitterPostPopup parentScreen, StatusUpdate failedUpdate, String errorMessage) {
		super(parentScreen, 320, 80, I18n.format("twitterpostfailed"));
		this.failedUpdate = failedUpdate;
		this.errorMessage = errorMessage;
	}

	@Override
	protected void onInitDialog() {
		btnOk.displayString = "Retry";
	}

	@Override
	public void onSubmit() {
		new Thread("Twitter_Post_Thread") {
			@Override
			public void run() {
				try {
					Status status = TwitterHandler.twitter.updateStatus(failedUpdate);
					String address = "http://twitter.com/" + status.getUser().getScreenName() + "/status/" + status.getId();
					((TwitterPostPopup)getParentScreen()).onUploadComplete(new TwitterUploadSuccessPopup(((TwitterPostPopup)getParentScreen()).getParentScreen(), status.getId(), address));
				} catch (TwitterException e) {
					((TwitterPostPopup)getParentScreen()).onUploadComplete(new TwitterUploadFailedPopup(((TwitterPostPopup)getParentScreen()), failedUpdate, I18n.format("errorcode") + ": " + Integer.toString(e.getErrorCode())));
				}
			}
		}.start();	
	}

	@Override
	public boolean validateDialog() {
		return true;
	}

	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		drawCenteredString(fontRendererObj, I18n.format("postfailed"), dialogX + (dialogWidth / 2), dialogY + 18, 0xFFFF5555);
		drawCenteredString(fontRendererObj, this.errorMessage, dialogX + (dialogWidth / 2), dialogY + 32, 0xFFFFAA00);
	}
	
	
	
	@Override
	protected void actionPerformed(GuiButton guibutton) {
		if (guibutton.equals(btnOk)) {
			retrying = true;
		}
		super.actionPerformed(guibutton);
	}
	
	@Override
	protected void closeDialog() {
		if (retrying) {
			super.closeDialog();
		} else {
			mc.displayGuiScreen(((TwitterPostPopup) getParentScreen()).getParentScreen());			
		}
	}
}
