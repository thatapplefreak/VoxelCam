package com.thatapplefreak.voxelcam.client.upload;

import net.minecraft.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Replaces DropboxHandler/GoogleDriveHandler: both were plain file copies
 * into the desktop sync client's watched folder, not real API integrations.
 * Ported as-is, fixing folder detection (case-sensitive on Linux, where the
 * real folder is capitalized "Dropbox") and using Util.OperatingSystem's
 * built-in per-OS file-manager opener instead of java.awt.Desktop.
 */
public final class FolderUploader {

	private FolderUploader() {
	}

	public static File findDropboxFolder() {
		File home = new File(System.getProperty("user.home"));
		for (String candidate : new String[] { "Dropbox", "dropbox" }) {
			File dir = new File(home, candidate);
			if (dir.isDirectory()) {
				return dir;
			}
		}
		return null;
	}

	public static File findGoogleDriveFolder() {
		File home = new File(System.getProperty("user.home"));
		for (String candidate : new String[] { "Google Drive", "GoogleDrive" }) {
			File dir = new File(home, candidate);
			if (dir.isDirectory()) {
				return dir;
			}
		}
		return null;
	}

	/** Copies the screenshot into "<syncFolder>/mcScreenshots/", returning the copy. */
	public static File copyInto(File syncFolder, File screenshot, boolean openFileManager) throws IOException {
		File targetDir = new File(syncFolder, "mcScreenshots");
		if (!targetDir.exists()) {
			targetDir.mkdirs();
		}
		File copy = new File(targetDir, screenshot.getName());
		try (FileInputStream in = new FileInputStream(screenshot);
				FileOutputStream out = new FileOutputStream(copy);
				FileChannel source = in.getChannel();
				FileChannel destination = out.getChannel()) {
			destination.transferFrom(source, 0, source.size());
		}
		if (openFileManager) {
			Util.getOperatingSystem().open(targetDir);
		}
		return copy;
	}
}
