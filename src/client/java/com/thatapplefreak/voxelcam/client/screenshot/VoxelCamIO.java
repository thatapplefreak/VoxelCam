package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VoxelCamIO {

	private static List<File> screenShotFiles = new ArrayList<>();
	private static File selected;

	private VoxelCamIO() {
	}

	public static List<File> getScreenShotFiles() {
		return screenShotFiles;
	}

	public static void selectPhoto(File file) {
		selected = file;
	}

	public static File getSelectedPhoto() {
		return selected;
	}

	/** Lists .png files in the directory, ordered by {@link SortMode#current()}, filtered by a case-insensitive name match. */
	public static void updateScreenShotFilesList(File screenshotsDir, String filter) {
		File[] filesInDir = screenshotsDir.listFiles();
		String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
		List<File> files = new ArrayList<>();
		if (filesInDir != null) {
			for (File file : filesInDir) {
				String name = file.getName();
				if (name.toLowerCase(Locale.ROOT).endsWith(".png")
						&& name.toLowerCase(Locale.ROOT).contains(needle)) {
					files.add(file);
				}
			}
		}
		files.sort(SortMode.current().comparator());
		screenShotFiles = files;
	}

	/** Renames the selected screenshot, returning the new file (or null if it failed). */
	public static File rename(File screenshotsDir, String newName) {
		if (selected == null) {
			return null;
		}
		File target = new File(screenshotsDir, newName + ".png");
		if (target.equals(selected) || !selected.renameTo(target)) {
			return null;
		}
		ScreenshotImageCache.release(selected);
		selected = target;
		return target;
	}

	public static void delete() {
		if (selected == null) {
			return;
		}
		ScreenshotImageCache.release(selected);
		selected.delete();
		screenShotFiles.remove(selected);
		selected = null;
	}
}
