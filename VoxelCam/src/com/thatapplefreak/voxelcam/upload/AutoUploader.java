package com.thatapplefreak.voxelcam.upload;

import java.io.File;

import com.thatapplefreak.voxelcam.LiteModVoxelCam;
import com.thatapplefreak.voxelcam.VoxelCamConfig;
import com.thatapplefreak.voxelcam.upload.dropbox.DropboxHandler;
import com.thatapplefreak.voxelcam.upload.googleDrive.GoogleDriveHandler;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurCallback;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurResponse;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUpload;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUploadResponse;
import com.thevoxelbox.common.util.AbstractionLayer;

public abstract class AutoUploader {

	public static void upload(File image) {
		VoxelCamConfig config = LiteModVoxelCam.getConfig();
		if (config.getBoolProperty(config.AUTO_UPLOAD_IMGUR)) {
			uploadToImgur(image);
		}
		if (config.getBoolProperty(config.AUTO_UPLOAD_DROPBOX)) {
			if (new File(System.getProperty("user.home"), "/dropbox/").exists()) {
				uploadToDropbox(image);
			} else {
				AbstractionLayer.addChatMessage("§4[VoxelCam]§ Error uploading to dropbox! Dropbox not installed!");
			}
		}
		if (config.getBoolProperty(config.AUTO_UPLOAD_GOOGLEDRIVE)) {
			if (new File(System.getProperty("user.home"), "/Google Drive/").exists()) {
				uploadToGoogleDrive(image);
			} else {
				AbstractionLayer.addChatMessage("§4[VoxelCam]§ Error uploading to Google Drive! Google Drive not installed!");
			}
		}
	}

	private static void uploadToImgur(File image) {
		final ImgurUpload poster = new ImgurUpload(image, image.getName(), "");
		poster.start(new ImgurCallback() {
			
			@Override
			public void onHTTPFailure(int responseCode, String responseMessage) {
				AbstractionLayer.addChatMessage("§4[VoxelCam]§ Error uploading image to imgur (" + responseCode + "): " + responseMessage);
			}
			
			@Override
			public void onCompleted(ImgurResponse response) {
				ImgurUploadResponse uploadResponse = (ImgurUploadResponse) poster.getResponse();
				AbstractionLayer.addChatMessage("§4[VoxelCam]§ Auto upload to Imgur succeded: " + uploadResponse.getLink());
			}
		});
	}

	private static void uploadToDropbox(File image) {
		DropboxHandler.doDropBox(image, false);
		AbstractionLayer.addChatMessage("§4[VoxelCam]§ Auto upload to Dropbox succeded");
	}

	private static void uploadToGoogleDrive(File image) {
		GoogleDriveHandler.doGoogleDrive(image, false);
		AbstractionLayer.addChatMessage("§4[VoxelCam]§ Auto upload to Google Drive succeded");
	}

}
