package com.thatapplefreak.voxelcam.upload;

import java.io.File;

import net.minecraft.client.resources.I18n;
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
				cmb.append("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
				cmb.append(" " + I18n.format("dropboxnoinstallerror"));
				cmb.showChatMessageIngame();
			}
		}
		if (config.getBoolProperty(config.AUTO_UPLOAD_GOOGLEDRIVE)) {
			if (new File(System.getProperty("user.home"), "/Google Drive/").exists()) {
				uploadToGoogleDrive(image);
			} else {
				ChatMessageBuilder cmb = new ChatMessageBuilder();
				cmb.append("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
				cmb.append(" " + I18n.format("googledrivenoinstallerror"));
				cmb.showChatMessageIngame();
			}
		}
		if (config.getBoolProperty(config.AUTO_UPLOAD_IMGUR)) {
			uploadToImgur(image);
		}
	}

	private static void uploadToImgur(File image) {
		final ImgurUpload poster = new ImgurUpload(image, image.getName(), "");
		poster.start(new ImgurCallback() {
			
			@Override
			public void onHTTPFailure(int responseCode, String responseMessage) {
				ChatMessageBuilder cmb = new ChatMessageBuilder();
				cmb.append("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
				cmb.append(" " + I18n.format("imgurautouploaderror") + " (");
				cmb.append(String.valueOf(responseCode));
				cmb.append("): ");
				cmb.append(responseMessage);
				cmb.showChatMessageIngame();
			}
			
			@Override
			public void onCompleted(ImgurResponse response) {
				ImgurUploadResponse uploadResponse = (ImgurUploadResponse) poster.getResponse();
				ChatMessageBuilder cmb = new ChatMessageBuilder();
				cmb.append("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
				cmb.append(" " + I18n.format("imgurautouploadsuccess") + ": ");
				cmb.append(uploadResponse.getLink(), uploadResponse.getLink(), true);
				cmb.showChatMessageIngame();
			}
		});
	}

	private static void uploadToDropbox(File image) {
		File dropbox = DropboxHandler.doDropBox(image, false);
		ChatMessageBuilder cmb = new ChatMessageBuilder();
		cmb.append("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
		cmb.append(" " + I18n.format("dropboxautouploadsuccess") + " ");
		cmb.append(I18n.format("clicktoview"), dropbox.getPath(), false);
		cmb.showChatMessageIngame();
	}

	private static void uploadToGoogleDrive(File image) {
		File googleDrive = GoogleDriveHandler.doGoogleDrive(image, false);
		ChatMessageBuilder cmb = new ChatMessageBuilder();
		cmb.append("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
		cmb.append(" " + I18n.format("googledriveautouploadsuccess") + " ");
		cmb.append(I18n.format("clicktoview"), googleDrive.getPath(), false);
		cmb.showChatMessageIngame();
	}

}
