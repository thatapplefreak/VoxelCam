package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class VoxelCamIO {

	private static List<File> screenShotFiles = new ArrayList<>();
	private static int selected = 0;

	private VoxelCamIO() {
	}

	public static List<File> getScreenShotFiles() {
		return screenShotFiles;
	}

	public static void selectPhotoIndex(int i) {
		selected = i;
	}

	public static int getSelectedIndex() {
		return selected;
	}

	public static boolean isSelected(int i) {
		return i == selected;
	}

	public static void updateScreenShotFilesList(File screenshotsDir, String filter) {
		File[] filesInDir = screenshotsDir.listFiles();
		List<File> files = new ArrayList<>();
		if (filesInDir != null) {
			for (File f : filesInDir) {
				if (f.getName().endsWith(".png") && f.getName().contains(filter)) {
					files.add(f);
				}
			}
		}
		files.sort(Comparator.comparingLong(File::lastModified).reversed());
		screenShotFiles = files;
		if (selected >= screenShotFiles.size()) {
			selected = Math.max(0, screenShotFiles.size() - 1);
		}
	}

	public static void rename(File screenshotsDir, String newName) {
		File selectedPhoto = getSelectedPhoto();
		if (selectedPhoto != null) {
			selectedPhoto.renameTo(new File(screenshotsDir, newName + ".png"));
		}
	}

	public static void delete() {
		File selectedPhoto = getSelectedPhoto();
		if (selectedPhoto == null) {
			return;
		}
		ScreenshotTextureCache.release(selectedPhoto);
		selectedPhoto.delete();
		screenShotFiles.remove(selected);
		if (selected > 0 && selected >= screenShotFiles.size()) {
			selected--;
		}
	}

	public static File getSelectedPhoto() {
		if (screenShotFiles.isEmpty() || selected < 0 || selected >= screenShotFiles.size()) {
			return null;
		}
		return screenShotFiles.get(selected);
	}
}
