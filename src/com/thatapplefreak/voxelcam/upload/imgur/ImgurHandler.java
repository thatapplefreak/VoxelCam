package com.thatapplefreak.voxelcam.upload.imgur;

import java.io.File;

import com.thatapplefreak.voxelcam.gui.manager.PostPopup;

public abstract class ImgurHandler {
	
	public static void doImgur(final PostPopup callbackGui, File screenshot) {
		final ImgurUpload poster = new ImgurUpload(screenshot, screenshot.getName(), "");
		poster.start(new ImgurCallback() {

			@Override
			public void onHTTPFailure(int responseCode, String responseMessage) {
				callbackGui.onUploadCompleted(new ImgurUploadFailedPopup(callbackGui.getParentScreen(), String.format("HTTP Error: %d %s", responseCode, responseMessage)));
			}

			@Override
			public void onCompleted(ImgurResponse response) {

				ImgurUploadResponse uploadResponse = (ImgurUploadResponse) poster.getResponse();
				if (uploadResponse.isSuccessful()) {
					callbackGui.onUploadCompleted(new ImgurUploadSuccessPopup(callbackGui.getParentScreen(), uploadResponse.getDeleteHash(), uploadResponse.getLink()));
				} else {
					callbackGui.onUploadCompleted(new ImgurUploadFailedPopup(callbackGui.getParentScreen(), uploadResponse.get("data")));
				}
			}
		});
	}
	
}
