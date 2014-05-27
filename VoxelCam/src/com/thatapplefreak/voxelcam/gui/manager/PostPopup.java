package com.thatapplefreak.voxelcam.gui.manager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.thatapplefreak.voxelcam.VoxelCamCore;
import com.thatapplefreak.voxelcam.VoxelCamConfig;
import com.thatapplefreak.voxelcam.imagehandle.ScreenshotIncapable;
import com.thatapplefreak.voxelcam.upload.dropbox.DropboxHandler;
import com.thatapplefreak.voxelcam.upload.googleDrive.GoogleDriveHandler;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurCallback;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurHandler;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurResponse;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUpload;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUploadResponse;
import com.thatapplefreak.voxelcam.upload.reddit.RedditHandler;
import com.thatapplefreak.voxelcam.upload.reddit.RedditLoginPopup;
import com.thatapplefreak.voxelcam.upload.reddit.RedditPostPopup;
import com.thatapplefreak.voxelcam.upload.twitter.TwitterLoginPopup;
import com.thatapplefreak.voxelcam.upload.twitter.TwitterPostPopup;
import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class PostPopup extends GuiDialogBox implements ScreenshotIncapable {

	GuiButton btnImgur, btnFacebook, btnTwitter, btnDropBox, btnGoogleDrive, btnReddit;

	private volatile GuiScreen completeDialog;

	private boolean uploading = false;

	public PostPopup(GuiScreen parentScreen) {
		super(parentScreen, 180, 120, I18n.format("postto") + "...");
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void onInitDialog() {
		buttonList.remove(btnOk);
		btnCancel.xPosition = dialogX + 60;
		btnImgur = new GuiButton(0, dialogX + 15, dialogY + 40, 70, 20, "Imgur");
		btnDropBox = new GuiButton(1, dialogX + 15, dialogY + 70, 70, 20, "Dropbox");
		btnFacebook = new GuiButton(2, dialogX + 95, dialogY + 10, 70, 20, "Facebook");
		btnTwitter = new GuiButton(3, dialogX + 15, dialogY + 10, 70, 20, "Twitter");
		btnGoogleDrive = new GuiButton(4, dialogX + 95, dialogY + 70, 70, 20, "Google Drive");
		btnReddit = new GuiButton(5, dialogX + 95, dialogY + 40, 70, 20, "Reddit");
		if (!(new File(System.getProperty("user.home"), "/dropbox/").exists())) {
			btnDropBox.enabled = false;
		}
		if (!(new File(System.getProperty("user.home"), "/Google Drive/").exists())) {
			btnGoogleDrive.enabled = false;
		}
		btnFacebook.enabled = false;
		buttonList.add(btnImgur);
		buttonList.add(btnDropBox);
		buttonList.add(btnFacebook);
		buttonList.add(btnTwitter);
		buttonList.add(btnGoogleDrive);
		buttonList.add(btnReddit);
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
		super.drawDialog(mouseX, mouseY, f);

		if (uploading) {
			buttonList.clear();
			drawCenteredString(fontRendererObj, I18n.format("uploading") + "...", width / 2, height / 2, 0xffffff);
		}

		if (this.completeDialog != null) {
			this.mc.displayGuiScreen(this.completeDialog);
			this.completeDialog = null;
		}
	}

	@Override
	protected void actionPerformed(GuiButton guibutton) {
		super.actionPerformed(guibutton);
		switch (guibutton.id) {
		case 0: // Imgur
			ImgurHandler.doImgur(this, GuiScreenShotManager.getSelectedPhoto());
			this.uploading = true;
			break;
		case 1: // DropBox
			DropboxHandler.doDropBox(GuiScreenShotManager.getSelectedPhoto(), true);
			mc.displayGuiScreen(getParentScreen());
			break;
		case 2: // Facebook
			// TODO
			break;
		case 3: // Twitter
			if (VoxelCamCore.getConfig().getStringProperty(VoxelCamConfig.TWITTERAUTHTOKEN).equals("needLogin")) {
				mc.displayGuiScreen(new TwitterLoginPopup(getParentScreen()));
			} else {
				mc.displayGuiScreen(new TwitterPostPopup(getParentScreen()));
			}
			break;
		case 4: // Google Drive
			GoogleDriveHandler.doGoogleDrive(GuiScreenShotManager.getSelectedPhoto(), true);
			break;
		case 5:
			if (!RedditHandler.isLoggedIn()) {
				mc.displayGuiScreen(new RedditLoginPopup(getParentScreen()));
			} else {
				mc.displayGuiScreen(new RedditPostPopup(getParentScreen()));
			}
			break;
		}
	}
	
	public void onUploadCompleted(GuiScreen g) {
		completeDialog = g;
	}

}
