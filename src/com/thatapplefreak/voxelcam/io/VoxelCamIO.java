package com.thatapplefreak.voxelcam.io;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import com.thatapplefreak.voxelcam.VoxelCamCore;
import com.thatapplefreak.voxelcam.imagehandle.GLImageMemoryHandler;

public abstract class VoxelCamIO {

	/**
	 * List of all PNG files in the screenshot directory
	 */
	private static ArrayList<File> screenShotFiles;

	/**
	 * Index of the currently selected photo
	 */
	public static int selected = 0;

	public static ArrayList<File> getScreenShotFiles() {
		return screenShotFiles;
	}

	public static void selectPhotoIndex(int i) {
		selected = i;
	}

	public static boolean isSelected(int i) {
		return i == selected;
	}

	public static void updateScreenShotFilesList(String filter) {
		File[] filesInScreenshotDir = VoxelCamCore.getScreenshotsDir().listFiles();
		Arrays.sort(filesInScreenshotDir, new Comparator<File>() {
			@Override
			public int compare(File f, File g) {
				if (f.lastModified() > g.lastModified()) {
					return -1;
				} else if (f.lastModified() < g.lastModified()) {
					return 1;
				} else {
					return 0;
				}
			}
		});
		VoxelCamIO.screenShotFiles = new ArrayList<File>(Arrays.asList(filesInScreenshotDir));
		for (int i = getScreenShotFiles().size() - 1; i >= 0; i--) {
			if (!getScreenShotFiles().get(i).getName().endsWith(".png")) {
				getScreenShotFiles().remove(i); 
			}
		}
		for (int i = getScreenShotFiles().size() - 1; i >= 0; i--) {
			if (!getScreenShotFiles().get(i).getName().contains(filter)) {
				getScreenShotFiles().remove(i);
			}
		}
		if (getScreenShotFiles().isEmpty()) {
			getScreenShotFiles().add(null);
		}
	}

	public static void rename(String string) {
		File newFile = new File(VoxelCamCore.getScreenshotsDir(), string + ".png");
		getSelectedPhoto().renameTo(newFile);
	}

	public static void delete() {
		GLImageMemoryHandler.requestImageRemovalFromMem(getSelectedPhoto());
		getSelectedPhoto().delete(); // EXTERMINATE!
		getScreenShotFiles().remove(VoxelCamIO.selected); // remove refrence
		if (selected > 0) {
			selected--;
		}
	}
	
	public static File getSelectedPhoto() {
		return getScreenShotFiles().get(selected);
	}
	
}
