package com.thatapplefreak.voxelcam.client.upload;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import com.thatapplefreak.voxelcam.client.util.ChatMessages;

import java.io.File;
import java.io.IOException;

public final class AutoUploader {

	private AutoUploader() {
	}

	/** Manual "Post to..." action - always uploads to Imgur regardless of the auto-upload toggle. */
	public static void postNow(File image) {
		uploadToImgur(image);
	}

	public static void upload(File image) {
		VoxelCamConfig config = VoxelCamClient.getConfig();

		if (config.autoUploadDropbox) {
			File dropbox = FolderUploader.findDropboxFolder();
			if (dropbox != null) {
				uploadToFolder(dropbox, image, "voxelcam.dropboxautouploadsuccess");
			} else {
				ChatMessages.send("voxelcam.dropboxnoinstallerror");
			}
		}
		if (config.autoUploadGoogleDrive) {
			File googleDrive = FolderUploader.findGoogleDriveFolder();
			if (googleDrive != null) {
				uploadToFolder(googleDrive, image, "voxelcam.googledriveautouploadsuccess");
			} else {
				ChatMessages.send("voxelcam.googledrivenoinstallerror");
			}
		}
		if (config.autoUploadImgur) {
			uploadToImgur(image);
		}
	}

	private static void uploadToFolder(File syncFolder, File image, String successKey) {
		try {
			File copy = FolderUploader.copyInto(syncFolder, image, false);
			ChatMessages.send(successKey, copy.getPath());
		} catch (IOException e) {
			VoxelCamClient.LOGGER.warn("Failed to copy screenshot into {}", syncFolder, e);
			ChatMessages.send("voxelcam.uploadfailed");
		}
	}

	private static void uploadToImgur(File image) {
		String clientId = VoxelCamClient.getConfig().imgurClientId;
		if (clientId == null || clientId.isBlank()) {
			ChatMessages.send("voxelcam.imgurnoclientid");
			return;
		}
		ImgurUploader.upload(image, clientId).whenComplete((response, error) -> {
			if (error != null || response == null || !response.isSuccessful()) {
				VoxelCamClient.LOGGER.warn("Imgur upload failed", error);
				ChatMessages.send("voxelcam.imguruploadfail");
				return;
			}
			ChatMessages.sendWithLink("voxelcam.imgurautouploadsuccess", response.getLink());
		});
	}
}
