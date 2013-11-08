package com.thevoxelbox.voxelcam.popups;

import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.GuiTextField;
import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;

import com.thevoxelbox.common.util.BrowserOpener;
import com.thevoxelbox.common.util.gui.GuiDialogBox;
import com.thevoxelbox.common.util.gui.GuiDialogBox.DialogResult;
import com.thevoxelbox.voxelcam.LiteModVoxelCam;
import com.thevoxelbox.voxelcam.VoxelCamConfig;
import com.thevoxelbox.voxelcam.upload.twitter.TwitterKeys;

public class TwitterPINPopup extends GuiDialogBox {

	private GuiTextField pinBox;

	private boolean b = false;

	public TwitterPINPopup(GuiScreen parentScreen) {
		super(parentScreen, 200, 75, "Please enter the PIN you Recieved");
	}

	@Override
	protected void onInitDialog() {
		pinBox = new GuiTextField(fontRenderer, width / 2 - (150 / 2), height / 2 - (16 / 2) - 8, 150, 16);
		BrowserOpener.openURLstringInBrowser(TwitterKeys.requestToken.getAuthorizationURL());
	}

	@Override
	public void onSubmit() {
		TwitterOauthGrabber grabber = new TwitterOauthGrabber();
		grabber.run();
	}

	@Override
	public boolean validateDialog() {
		return pinBox.getText().length() == 7;
	}

	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		super.drawDialog(mouseX, mouseY, f);
		pinBox.drawTextBox();
	}

	@Override
	protected void mouseClickedEx(int mouseX, int mouseY, int button) {
		super.mouseClickedEx(mouseX, mouseY, button);
		pinBox.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void onKeyTyped(char keyChar, int keyCode) {
		pinBox.textboxKeyTyped(keyChar, keyCode);
	}

	private class TwitterOauthGrabber implements Runnable {
		@Override
		public void run() {
			AccessToken accessToken = null;
			while (accessToken == null || accessToken.getToken() == null) {
				try {
					accessToken = TwitterKeys.twitter.getOAuthAccessToken(TwitterKeys.requestToken, pinBox.getText());
				} catch (TwitterException e) {
				}
			}
			storeAccessToken(accessToken);
			b = true;
		}
	}

	@Override
	protected void actionPerformed(GuiButton guibutton) {
		if (guibutton.id == this.btnCancel.id) {
			this.dialogResult = DialogResult.Cancel;
			this.closeDialog();
		}
		if (guibutton.id == this.btnOk.id) {
			if (this.validateDialog()) {
				this.dialogResult = DialogResult.OK;
				this.onSubmit();
				if (!b) {
					this.closeDialog();
				} else {
					goToPostGUI();
				}
			}
		}
	}

	private void storeAccessToken(AccessToken accessToken) {
		System.out.println("[VoxelCam] Setting Twitter access token");
		LiteModVoxelCam.getConfig().setProperty(VoxelCamConfig.TWITTERAUTHTOKEN, accessToken.getToken());
		LiteModVoxelCam.getConfig().setProperty(VoxelCamConfig.TWITTERAUTHTOKENSECRET, accessToken.getTokenSecret());
		LiteModVoxelCam.getConfig().setProperty(VoxelCamConfig.TWITTERUSERID, String.valueOf(accessToken.getUserId()));
	}

	private void goToPostGUI() {
		mc.displayGuiScreen(new TwitterPostPopup(parentScreen));
	}
}
