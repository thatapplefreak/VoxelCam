package com.thatapplefreak.voxelcam.upload.dropbox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import net.minecraft.util.Util.EnumOS;

public abstract class DropboxHandler {
	
	/**
	 * Copies a file into the dropbox folder /mcScreenshots/ and opens native
	 * file browser and highlights it
	 */
	public static File doDropBox(File screenshot, boolean openFileManager) {
		makeDropboxDir();
		File dropboxCopy = new File(System.getProperty("user.home") + "/dropbox/mcScreenshots/", screenshot.getName());
		FileChannel source = null;
		FileChannel destination = null;
		FileInputStream inputStream = null;
		FileOutputStream outputStream = null;
		try {
			inputStream = new FileInputStream(screenshot);
			outputStream = new FileOutputStream(dropboxCopy);
			source = inputStream.getChannel();
			destination = outputStream.getChannel();
			destination.transferFrom(source, 0, source.size());
			if (openFileManager) {
				EnumOS os = net.minecraft.util.Util.getOSType();
				if (os.equals(EnumOS.WINDOWS)) {
					new ProcessBuilder("explorer.exe", "/select,",
							dropboxCopy.toString()).start();
				} else if (os.equals(EnumOS.OSX)) {
					new ProcessBuilder("open", "-R", dropboxCopy.toString())
							.start();
				}
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
		return dropboxCopy;
	}
	


	/**
	 * Creates the Dropbox folder /mcScreenshots/
	 */
	private static void makeDropboxDir() {
		File t = new File(System.getProperty("user.home") + "/dropbox/mcScreenshots/");
		if (!t.exists()) {
			t.mkdirs();
		}
	}
	
}
