package com.thevoxelbox.voxelcam.popups;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import org.lwjgl.input.Keyboard;

import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;
import net.minecraft.src.EnumOS;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.I18n;

import com.thevoxelbox.common.util.gui.GuiDialogBox;
import com.thevoxelbox.voxelcam.LiteModVoxelCam;
import com.thevoxelbox.voxelcam.VoxelCamConfig;
import com.thevoxelbox.voxelcam.gui.GuiScreenShotManager;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurCallback;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurResponse;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurUpload;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurUploadResponse;
import com.thevoxelbox.voxelcam.upload.twitter.TwitterKeys;

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
		btnImgur = new GuiButton(0, dialogX + (dialogWidth / 2) - 35,
				dialogY + 10, 70, 20, "Imgur");
		btnDropBox = new GuiButton(1, dialogX + (dialogWidth / 2) - 35,
				dialogY + 40, 70, 20, "Dropbox");
		btnFacebook = new GuiButton(2, dialogX + (dialogWidth / 2) - 35,
				dialogY + 70, 70, 20, "Facebook");
		btnTwitter = new GuiButton(3, dialogX + (dialogWidth / 2) - 35,
				dialogY + 100, 70, 20, "Twitter");
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
			drawCenteredString(fontRenderer, "Uploading...", width / 2,
					height / 2, 0xffffff);
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
			doImgur();
			break;
		case 1: // DropBox
			makeDropboxDir();
			doDropBox();
			mc.displayGuiScreen(parentScreen);
			break;
		case 2: // Facebook
			// TODO
			break;
		case 3: // Twitter
			if (LiteModVoxelCam.getConfig()
					.getStringProperty(VoxelCamConfig.TWITTERAUTHTOKEN)
					.equals("needLogin")) {
				mc.displayGuiScreen(new TwitterLoginPopup(parentScreen));
			} else {
				mc.displayGuiScreen(new TwitterPostPopup(parentScreen));
			}
			break;
		}
	}

	private void doImgur() {
		File imageToUpload = ((GuiScreenShotManager) parentScreen)
				.getSelectedPhoto();
		final ImgurUpload poster = new ImgurUpload(imageToUpload,
				imageToUpload.getName(), "");
		poster.start(new ImgurCallback() {

			@Override
			public void onHTTPFailure(int responseCode, String responseMessage) {
				PostPopup.this.completeDialog = new UploadFailedPopup(
						PostPopup.this.parentScreen, "Upload to imgur failed",
						String.format("HTTP Error: %d %s", responseCode,
								responseMessage));
			}

			@Override
			public void onCompleted(ImgurResponse response) {

				ImgurUploadResponse uploadResponse = (ImgurUploadResponse) poster
						.getResponse();
				if (uploadResponse.isSuccessful()) {
					PostPopup.this.completeDialog = new UploadSuccessPopup(
							PostPopup.this.parentScreen,
							"Upload to imgur succeeded", uploadResponse
									.getDeleteHash(), uploadResponse.getLink());
				} else {
					PostPopup.this.completeDialog = new UploadFailedPopup(
							PostPopup.this.parentScreen,
							"Upload to imgur failed", uploadResponse
									.get("data"));
				}
			}
		});
		uploading = true;
	}

	/**
	 * Creates the Dropbox folder /mcScreenshots/
	 */
	private void makeDropboxDir() {
		File t = new File(System.getProperty("user.home")
				+ "/dropbox/mcScreenshots/");
		if (!t.exists()) {
			t.mkdirs();
		}
	}

	/**
	 * Copies a file into the dropbox folder /mcScreenshots/ and opens native
	 * file browser and highlights it
	 */
	private void doDropBox() {
		File src = ((GuiScreenShotManager) parentScreen).getScreenShotFiles()
				.get(((GuiScreenShotManager) parentScreen).selected);
		File dropboxCopy = new File(System.getProperty("user.home")
				+ "/dropbox/mcScreenshots/", src.getName());
		FileChannel source = null;
		FileChannel destination = null;
		FileInputStream inputStream = null;
		FileOutputStream outputStream = null;
		try {
			inputStream = new FileInputStream(src);
			outputStream = new FileOutputStream(dropboxCopy);
			source = inputStream.getChannel();
			destination = outputStream.getChannel();
			destination.transferFrom(source, 0, source.size());
			EnumOS os = net.minecraft.src.Util.getOSType();
			if (os.equals(EnumOS.WINDOWS)) {
				new ProcessBuilder("explorer.exe", "/select,", dropboxCopy.toString()).start();
			} else if (os.equals(EnumOS.MACOS)) {
				new ProcessBuilder("open", "-R", dropboxCopy.toString())
						.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				// Close any memory leaks
				if (inputStream != null) {
					inputStream.close();
				}
				if (outputStream != null) {
					outputStream.close();
				}
				if (source != null) {
					source.close();
				}
				if (destination != null) {
					destination.close();
				}
			} catch (IOException e) {
			}
		}
	}

}
