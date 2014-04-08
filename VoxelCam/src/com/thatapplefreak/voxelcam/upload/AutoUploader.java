package com.thatapplefreak.voxelcam.upload;

import java.io.File;

import net.minecraft.util.EnumChatFormatting;

import com.thatapplefreak.voxelcam.VoxelCamCore;
import com.thatapplefreak.voxelcam.VoxelCamConfig;
import com.thatapplefreak.voxelcam.upload.dropbox.DropboxHandler;
import com.thatapplefreak.voxelcam.upload.googleDrive.GoogleDriveHandler;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurCallback;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurResponse;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUpload;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUploadResponse;
import com.thevoxelbox.common.util.AbstractionLayer;
import com.thevoxelbox.common.util.ChatMessageBuilder;

public abstract class AutoUploader {

	public static void upload(File image) {
		VoxelCamConfig config = VoxelCamCore.getConfig();
		if (config.getBoolProperty(config.AUTO_UPLOAD_DROPBOX)) {
			if (new File(System.getProperty("user.home"), "/dropbox/").exists()) {
				uploadToDropbox(image);
			} else {
				ChatMessageBuilder cmb = new ChatMessageBuilder();
				cmb.appendText("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
				cmb.appendText(" Error uploading to dropbox! Dropbox not installed!");
				cmb.showChatMessageIngame();
			}
		}
		if (config.getBoolProperty(config.AUTO_UPLOAD_GOOGLEDRIVE)) {
			if (new File(System.getProperty("user.home"), "/Google Drive/").exists()) {
				uploadToGoogleDrive(image);
			} else {
				ChatMessageBuilder cmb = new ChatMessageBuilder();
				cmb.appendText("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
				cmb.appendText(" Error uploading to Google Drive! Google Drive not installed!");
				cmb.showChatMessageIngame();
			}
		}
		if (config.getBoolProperty(config.AUTO_UPLOAD_IMGUR)) {
			uploadToImgur(image);
		}
	}

	private static void uploadToImgur(File image) {
		final ImgurUpload poster = new ImgurUpload(image, image.getName(), "");
		System.out.println("start upload");
		poster.start(new ImgurCallback() {
			
			@Override
			public void onHTTPFailure(int responseCode, String responseMessage) {
				ChatMessageBuilder cmb = new ChatMessageBuilder();
				cmb.appendText("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
				cmb.appendText(" Error uploading image to imgur (");
				cmb.appendText(String.valueOf(responseCode));
				cmb.appendText("): ");
				cmb.appendText(responseMessage);
				cmb.showChatMessageIngame();
			}
			
			@Override
			public void onCompleted(ImgurResponse response) {
				ImgurUploadResponse uploadResponse = (ImgurUploadResponse) poster.getResponse();
				ChatMessageBuilder cmb = new ChatMessageBuilder();
				cmb.appendText("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
				cmb.appendText(" Auto upload to Imgur succeded: ");
				cmb.appendLink(uploadResponse.getLink(), uploadResponse.getLink());
			}
		});
	}

	private static void uploadToDropbox(File image) {
		File dropbox = DropboxHandler.doDropBox(image, false);
		ChatMessageBuilder cmb = new ChatMessageBuilder();
		cmb.appendText("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
		cmb.appendText(" Auto upload to Dropbox succeded ");
		cmb.appendLink("Click to view", dropbox.getPath());
		cmb.showChatMessageIngame();
	}

	private static void uploadToGoogleDrive(File image) {
		File googleDrive = GoogleDriveHandler.doGoogleDrive(image, false);
		ChatMessageBuilder cmb = new ChatMessageBuilder();
		cmb.appendText("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
		cmb.appendText(" Auto upload to Google Drive succeded ");
		cmb.appendLink("Click to view", googleDrive.getPath());
		cmb.showChatMessageIngame();
	}

}
