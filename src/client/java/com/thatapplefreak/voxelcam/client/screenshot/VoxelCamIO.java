package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

	/**
	 * Lists .png files in the directory, ordered by {@link SortMode#current()}, filtered by a
	 * case-insensitive name match.
	 *
	 * This runs on the render thread from {@code GuiScreenShotManager.init()}, which is re-run
	 * for every search keystroke and every window-resize event, so what it does per entry is
	 * paid tens of times during a window drag. That is why the name is lowercased once rather
	 * than per predicate, why the sort is {@link SortMode#sort} rather than a comparator, and
	 * why the extension test is not backed up by an {@code isFile()} guard: that would stat
	 * every entry — including under the name modes, which otherwise touch the disk not at all
	 * — to exclude a directory someone named {@code something.png}. Such a directory is listed
	 * and refuses to be deleted, which {@code VoxelCamIOTest} relies on.
	 */
	public static void updateScreenShotFilesList(File screenshotsDir, String filter) {
		File[] filesInDir = screenshotsDir.listFiles();
		String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
		List<File> files = new ArrayList<>();
		if (filesInDir != null) {
			for (File file : filesInDir) {
				String name = file.getName().toLowerCase(Locale.ROOT);
				if (name.endsWith(".png") && name.contains(needle)) {
					files.add(file);
				}
			}
		}
		SortMode.current().sort(files);
		screenShotFiles = files;
	}

	/**
	 * Whether {@code newName} is already taken by a file other than {@code currentFile}.
	 * A bare {@code exists()} probe answers yes for a case-only rename on macOS and Windows,
	 * where the case variant resolves to the very file being renamed — so the popup would
	 * refuse a rename that {@code File.renameTo} performs happily. {@link Files#isSameFile}
	 * asks by file identity instead, which is false for that self-match on a
	 * case-insensitive filesystem and still true for a genuine collision anywhere.
	 * A candidate that cannot be compared counts as taken: refusing is the safe direction,
	 * and the rename would fail anyway.
	 */
	public static boolean nameCollides(File screenshotsDir, String newName, File currentFile) {
		File candidate = new File(screenshotsDir, newName + ".png");
		if (!candidate.exists()) {
			return false;
		}
		if (currentFile == null) {
			return true;
		}
		try {
			return !Files.isSameFile(candidate.toPath(), currentFile.toPath());
		} catch (IOException e) {
			return true;
		}
	}

	/**
	 * The screenshot a browser listing {@code files} should have selected, given the
	 * {@code current} one it is already showing.
	 *
	 * A screen that keeps its own copy of the selection has one that predates whatever a
	 * popup just did, since returning from a popup rebuilds it: after a rename {@code current}
	 * names a file that no longer exists, and defaulting straight to the head of the list
	 * would silently move the player onto whichever screenshot the sort happens to put first
	 * — the one the next Delete would then be aimed at. The selection {@link #rename} left
	 * here is the one that followed the file, so it gets asked before the fallback.
	 *
	 * It lives beside {@code rename} rather than in the manager so a plain JUnit test can
	 * reach it, the same reason {@link #nameCollides} is here and not in the popup.
	 */
	public static File selectionFor(List<File> files, File current) {
		if (current != null && files.contains(current)) {
			return current;
		}
		if (selected != null && files.contains(selected)) {
			return selected;
		}
		return files.isEmpty() ? null : files.get(0);
	}

	/**
	 * Renames the selected screenshot, returning the new file — or null if it did not
	 * happen, which the caller has to tell the player about: nothing here throws, the file
	 * keeps its old name, and the manager re-lists it as if nothing had been asked.
	 *
	 * {@code File.renameTo} rather than {@link Files#move}: move reports success without
	 * moving anything for a case-only rename on a case-insensitive filesystem, where it
	 * finds source and target are the same file — silently undoing the recapitalisation
	 * renameTo performs. The reason for a failure is lost to a bare false, so it is only
	 * ever as good as the log line below.
	 */
	public static File rename(File screenshotsDir, String newName) {
		if (selected == null) {
			return null;
		}
		File target = new File(screenshotsDir, newName + ".png");
		// The names, not the Files: WinNTFileSystem compares paths with
		// compareToIgnoreCase, so File.equals would make this guard swallow the case-only
		// rename it is not meant to catch — it only exists to refuse an unchanged name.
		if (target.getName().equals(selected.getName())) {
			return null;
		}
		if (!selected.renameTo(target)) {
			VoxelCamClient.LOGGER.error("Failed to rename {} to {}", selected, target);
			return null;
		}
		ScreenshotImageCache.release(selected);
		selected = target;
		return target;
	}

	public static boolean isSelectedFavorite() {
		return selected != null && Favorite.isStarred(selected);
	}

	/**
	 * The screenshot itself is unaffected either way; losing a star toggle to a rare I/O
	 * failure is logged rather than surfaced, the same call {@code ScreenshotHandler} makes
	 * for a failed capture-context embed.
	 */
	public static void toggleSelectedFavorite() {
		if (selected == null) {
			return;
		}
		try {
			Favorite.setStarred(selected, !Favorite.isStarred(selected));
		} catch (IOException e) {
			VoxelCamClient.LOGGER.error("Failed to toggle the favorite flag on {}", selected, e);
		}
	}

	/**
	 * Deletes the selected screenshot, reporting whether the file actually went.
	 * {@code File.delete} signals a locked, read-only or otherwise undeletable file with a
	 * bare false, so {@link Files#delete} is used for the reason and the failure is logged.
	 * The list and the selection are only touched once the file is gone: the manager
	 * re-lists the directory when a popup closes, so a row dropped from an optimistic
	 * delete would silently come back with nothing to explain it.
	 */
	public static boolean delete() {
		if (selected == null) {
			return false;
		}
		try {
			Files.delete(selected.toPath());
		} catch (IOException e) {
			VoxelCamClient.LOGGER.error("Failed to delete {}", selected, e);
			return false;
		}
		ScreenshotImageCache.release(selected);
		screenShotFiles.remove(selected);
		selected = null;
		return true;
	}
}
