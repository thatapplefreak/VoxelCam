package com.thevoxelbox.voxelcam.popups;

import java.io.File;

import net.minecraft.src.GuiScreen;
import net.minecraft.src.GuiTextField;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;

import com.thevoxelbox.common.util.gui.GuiDialogBox;
import com.thevoxelbox.voxelcam.LiteModVoxelCam;
import com.thevoxelbox.voxelcam.VoxelCamConfig;
import com.thevoxelbox.voxelcam.gui.GuiScreenShotManager;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurCallback;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurResponse;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurUpload;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurUploadResponse;
import com.thevoxelbox.voxelcam.upload.twitter.TwitterHandler;
import com.thevoxelbox.voxelcam.upload.twitter.TwitterKeys;

public class TwitterPostPopup extends GuiDialogBox {

	private boolean uploading = false;

	private volatile GuiScreen completeDialog;

	private GuiTextField textbox;
	
	private int tweetLengh = 100;

	public TwitterPostPopup(GuiScreen parentScreen) {
		super(parentScreen, 210, 90, "Post To Twitter");
	}

	@Override
	protected void onInitDialog() {
		btnOk.displayString = "Post";
		textbox = new GuiTextField(fontRenderer, width / 2 - (200 / 2), height / 2 - (16 / 2) - 8, 200, 16);
		textbox.setMaxStringLength(tweetLengh);
		textbox.setFocused(true);
	}

	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		super.drawDialog(mouseX, mouseY, f);

		if (uploading) {
			buttonList.clear();
			drawCenteredString(fontRenderer, "Uploading...", width / 2, height / 2, 0xFFFFFF);
		} else {
			textbox.drawTextBox();
			drawString(fontRenderer, "Compose Tweet:", dialogX + 5, height / 2 - 28, 0xFFFFFF);
			drawString(fontRenderer, "Remaining letters:", width / 2 - 5, height / 2 + 5, 0xFFFFFF);
			drawString(fontRenderer, Integer.toString(tweetLengh - textbox.getText().length()), width / 2 + 84, height / 2 + 5, 0xFFFFFF);
		}

		if (this.completeDialog != null) {
			this.mc.displayGuiScreen(this.completeDialog);
			this.completeDialog = null;
		}
	}

	@Override
	protected void mouseClickedEx(int mouseX, int mouseY, int button) {
		super.mouseClickedEx(mouseX, mouseY, button);
		textbox.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void onKeyTyped(char keyChar, int keyCode) {
		textbox.textboxKeyTyped(keyChar, keyCode);
	}

	@Override
	public void onSubmit() {
	}

	@Override
	public boolean validateDialog() {
		TwitterHandler.doTwitter(this, GuiScreenShotManager.getSelectedPhoto(), textbox.getText());
		uploading = true;
		return false;
	}
	
	public void onUploadComplete(GuiScreen result) {
		this.completeDialog = result;
	}

}
