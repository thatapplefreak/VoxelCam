package com.thatapplefreak.voxelcam.upload.imgur;

import java.io.File;

import com.thatapplefreak.voxelcam.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.popups.PostPopup;
import com.thatapplefreak.voxelcam.popups.UploadFailedPopup;
import com.thatapplefreak.voxelcam.popups.UploadSuccessPopup;

public abstract class ImgurHandler {
	
	public static void doImgur(final PostPopup callbackGui, File screenshot) {
		final ImgurUpload poster = new ImgurUpload(screenshot, screenshot.getName(), "");
		poster.start(new ImgurCallback() {

			@Override
			public void onHTTPFailure(int responseCode, String responseMessage) {
				callbackGui.onUploadCompleted(new UploadFailedPopup(callbackGui.getParentScreen(), "Upload to imgur failed", String.format("HTTP Error: %d %s", responseCode, responseMessage)));
			}

			@Override
			public void onCompleted(ImgurResponse response) {

				ImgurUploadResponse uploadResponse = (ImgurUploadResponse) poster.getResponse();
				if (uploadResponse.isSuccessful()) {
					callbackGui.onUploadCompleted(new UploadSuccessPopup(callbackGui.getParentScreen(), "Upload to imgur succeeded", uploadResponse.getDeleteHash(), uploadResponse.getLink()));
				} else {
					callbackGui.onUploadCompleted(new UploadFailedPopup(callbackGui.getParentScreen(), "Upload to imgur failed", uploadResponse.get("data")));
				}
			}
		});
	}
	
}
