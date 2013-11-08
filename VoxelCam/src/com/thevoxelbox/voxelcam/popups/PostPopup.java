package com.thevoxelbox.voxelcam.popups;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import net.minecraft.src.EnumOS;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;

import com.thevoxelbox.common.util.gui.GuiDialogBox;
import com.thevoxelbox.voxelcam.LiteModVoxelCam;
import com.thevoxelbox.voxelcam.VoxelCamConfig;
import com.thevoxelbox.voxelcam.gui.GuiScreenShotManager;
import com.thevoxelbox.voxelcam.upload.dropbox.DropboxHandler;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurCallback;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurHandler;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurResponse;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurUpload;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurUploadResponse;

public class PostPopup extends GuiDialogBox {

	GuiButton btnImgur, btnFacebook, btnTwitter, btnDropBox;

	private volatile GuiScreen completeDialog;

	private boolean uploading = false;

	public PostPopup(GuiScreen parentScreen) {
		super(parentScreen, 120, 150, "Post to...");
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void onInitDialog() {
		buttonList.remove(btnOk);
		btnCancel.xPosition = dialogX + 30;
		btnImgur = new GuiButton(0, dialogX + (dialogWidth / 2) - 35, dialogY + 10, 70, 20, "Imgur");
		btnDropBox = new GuiButton(1, dialogX + (dialogWidth / 2) - 35, dialogY + 40, 70, 20, "Dropbox");
		btnFacebook = new GuiButton(2, dialogX + (dialogWidth / 2) - 35, dialogY + 70, 70, 20, "Facebook");
		btnTwitter = new GuiButton(3, dialogX + (dialogWidth / 2) - 35, dialogY + 100, 70, 20, "Twitter");
		if (!(new File(System.getProperty("user.home"), "/dropbox/").exists())) {
			btnDropBox.enabled = false;
		}
		btnFacebook.enabled = false;
		buttonList.add(btnImgur);
		buttonList.add(btnDropBox);
		buttonList.add(btnFacebook);
		buttonList.add(btnTwitter);
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
			drawCenteredString(fontRenderer, "Uploading...", width / 2, height / 2, 0xffffff);
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
			break;
		case 1: // DropBox
			DropboxHandler.doDropBox(GuiScreenShotManager.getSelectedPhoto());
			mc.displayGuiScreen(getParentScreen());
			break;
		case 2: // Facebook
			// TODO
			break;
		case 3: // Twitter
			if (LiteModVoxelCam.getConfig().getStringProperty(VoxelCamConfig.TWITTERAUTHTOKEN).equals("needLogin")) {
				mc.displayGuiScreen(new TwitterLoginPopup(getParentScreen()));
			} else {
				mc.displayGuiScreen(new TwitterPostPopup(getParentScreen()));
			}
			break;
		}
	}
	
	public void onUploadCompleted(GuiScreen g) {
		completeDialog = g;
	}

}
