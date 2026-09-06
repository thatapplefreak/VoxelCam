package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VoxelCamIO {

	private static List<File> screenShotFiles = new ArrayList<>();
	private static File selected;

	/** How many sub-frames are folded under each burst key, from the same scan that builds
	 * {@link #screenShotFiles}. A key absent here has none. */
	private static Map<File, Integer> burstCounts = Map.of();

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
	 * case-insensitive name match — except a burst's sub-frames, which are folded under their key
	 * rather than listed, so a burst shows as one row.
	 *
	 * <p>This runs on the render thread from {@code GuiScreenShotManager.init()}, which is re-run
	 * for every search keystroke and every window-resize event, so what it does per entry is
	 * paid tens of times during a window drag. That is why the name is lowercased once rather
	 * than per predicate, why the sort is {@link SortMode#sort} rather than a comparator, and
	 * why the extension test is not backed up by an {@code isFile()} guard: that would stat
	 * every entry — including under the name modes, which otherwise touch the disk not at all
	 * — to exclude a directory someone named {@code something.png}. Such a directory is listed
	 * and refuses to be deleted, which {@code VoxelCamIOTest} relies on.
	 *
	 * <p>The key-name lookup used to fold a sub-frame away is built from the <em>unfiltered</em>
	 * listing, not the needle-filtered one: with the needle applied first, typing something that
	 * excludes a key's own name would make all of its frames pop back into view as if they were
	 * orphaned. A frame whose key is genuinely absent — renamed or deleted — is not folded and
	 * reappears as an ordinary screenshot; that is the orphan rule, and it is what keeps a
	 * sub-frame reachable even if the two channels this mod uses to track a group (the filename
	 * tag and the PNG's embedded {@code voxelcam:burst} tags) ever disagree.
	 */
	public static void updateScreenShotFilesList(File screenshotsDir, String filter) {
		File[] filesInDir = screenshotsDir.listFiles();
		String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
		List<File> files = new ArrayList<>();
		Map<File, Integer> counts = new HashMap<>();

		if (filesInDir != null) {
			Map<String, File> byLowerName = new HashMap<>();
			for (File file : filesInDir) {
				String name = file.getName().toLowerCase(Locale.ROOT);
				if (name.endsWith(".png")) {
					byLowerName.put(name, file);
				}
			}

			for (File file : filesInDir) {
				String name = file.getName().toLowerCase(Locale.ROOT);
				if (!name.endsWith(".png") || !name.contains(needle)) {
					continue;
				}
				String keyName = ScreenshotNamer.burstKeyName(name);
				File key = keyName == null ? null : byLowerName.get(keyName);
				if (key != null) {
					counts.merge(key, 1, Integer::sum);
					continue;
				}
				files.add(file);
			}
		}

		SortMode.current().sort(files);
		screenShotFiles = files;
		burstCounts = counts;
	}

	/** The number of sub-frames folded under {@code key}, or 0 if it is not a burst key —
	 * never 1, since a burst of one frame has no sub-frames to fold. Free: counted from the
	 * name scan {@link #updateScreenShotFilesList} already did. */
	public static int burstFrameCount(File key) {
		return burstCounts.getOrDefault(key, 0);
	}

	/**
	 * The sub-frames folded under {@code key}, in index order, read fresh from disk. The caller
	 * is about to touch these files (the frame browser, delete, rename), so a fresh listing
	 * rather than the cached count is worth the extra directory read here.
	 */
	public static List<File> burstFrames(File screenshotsDir, File key) {
		if (key == null) {
			return List.of();
		}
		File[] filesInDir = screenshotsDir.listFiles();
		if (filesInDir == null) {
			return List.of();
		}
		String keyLower = key.getName().toLowerCase(Locale.ROOT);
		List<File> frames = new ArrayList<>();
		for (File file : filesInDir) {
			String name = file.getName().toLowerCase(Locale.ROOT);
			if (!name.endsWith(".png")) {
				continue;
			}
			if (keyLower.equals(ScreenshotNamer.burstKeyName(name))) {
				frames.add(file);
			}
		}
		frames.sort(Comparator.comparing(File::getName));
		return frames;
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
	 * <p>When the selection is a burst key, its sub-frames are carried across too: every target
	 * name (the key's and every frame's) is collision-checked up front, then the frames are
	 * renamed before the key, and a failure anywhere rolls back everything already moved. A
	 * player renaming a burst key — something they do constantly to their good shots — would
	 * otherwise shatter the group into several ungrouped rows on every rename.
	 *
	 * <p>{@code File.renameTo} rather than {@link Files#move}: move reports success without
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
		if (nameCollides(screenshotsDir, newName, selected)) {
			return null;
		}

		List<File> frames = burstFrames(screenshotsDir, selected);
		List<File> frameTargets = new ArrayList<>(frames.size());
		for (File frame : frames) {
			String suffix = ScreenshotNamer.burstFrameSuffix(frame.getName());
			if (suffix == null || nameCollides(screenshotsDir, newName + suffix, frame)) {
				return null;
			}
			frameTargets.add(new File(screenshotsDir, newName + suffix + ".png"));
		}

		List<File> movedFrom = new ArrayList<>();
		List<File> movedTo = new ArrayList<>();
		for (int i = 0; i < frames.size(); i++) {
			File from = frames.get(i);
			File to = frameTargets.get(i);
			if (!from.renameTo(to)) {
				VoxelCamClient.LOGGER.error("Failed to rename burst frame {} to {}; rolling back", from, to);
				rollbackRename(movedFrom, movedTo);
				return null;
			}
			movedFrom.add(from);
			movedTo.add(to);
		}

		if (!selected.renameTo(target)) {
			if (movedTo.isEmpty()) {
				VoxelCamClient.LOGGER.error("Failed to rename {} to {}", selected, target);
			} else {
				VoxelCamClient.LOGGER.error("Failed to rename {} to {}; rolling back its burst frames", selected, target);
			}
			rollbackRename(movedFrom, movedTo);
			return null;
		}

		for (File frame : movedTo) {
			ScreenshotImageCache.release(frame);
		}
		ScreenshotImageCache.release(selected);
		selected = target;
		return target;
	}

	/** Undoes whichever burst-frame renames already succeeded, in reverse order, after the
	 * group rename they were part of failed partway through. */
	private static void rollbackRename(List<File> movedFrom, List<File> movedTo) {
		for (int i = movedTo.size() - 1; i >= 0; i--) {
			File from = movedFrom.get(i);
			File to = movedTo.get(i);
			if (!to.renameTo(from)) {
				VoxelCamClient.LOGGER.error("Failed to roll back {} to {} after a failed group rename", to, from);
			}
		}
	}

	/**
	 * Swaps the bytes of the selected burst key with one of its frames — the iPhone "set key
	 * photo" gesture. Only the two files' contents move; their paths, and so the whole group's
	 * structure (which one is the key, which are frames, the collapse that hides the frames),
	 * are untouched. The tags inside each file — capture order, offset from the key — are
	 * deliberately NOT rewritten to match: the name a swap changes is a presentation choice, the
	 * tags record capture history, and a future export needs the latter regardless of which
	 * frame is shown first.
	 *
	 * @return true if the swap happened.
	 */
	public static boolean setAsKey(File frame) {
		if (selected == null || frame == null || frame.equals(selected)) {
			return false;
		}
		File dir = selected.getParentFile();
		File tmp;
		try {
			Path tmpPath = Files.createTempFile(dir.toPath(), "voxelcam-setkey-", ".png.tmp");
			Files.delete(tmpPath);
			tmp = tmpPath.toFile();
		} catch (IOException e) {
			VoxelCamClient.LOGGER.error("Failed to reserve a swap file for Set as key", e);
			return false;
		}

		if (!selected.renameTo(tmp)) {
			VoxelCamClient.LOGGER.error("Failed to swap {} aside for Set as key", selected);
			return false;
		}
		if (!frame.renameTo(selected)) {
			VoxelCamClient.LOGGER.error("Failed to move {} into the key slot for Set as key; rolling back", frame);
			if (!tmp.renameTo(selected)) {
				VoxelCamClient.LOGGER.error("Failed to roll back {} to {} after a failed Set as key", tmp, selected);
			}
			return false;
		}
		if (!tmp.renameTo(frame)) {
			VoxelCamClient.LOGGER.error("Failed to move the old key into {} for Set as key; rolling back", frame);
			if (!selected.renameTo(frame) || !tmp.renameTo(selected)) {
				VoxelCamClient.LOGGER.error("Failed to fully roll back a failed Set as key swap between {} and {}",
						selected, frame);
			}
			return false;
		}

		// Cache invalidation is the caller's job, the same as toggleSelectedFavorite()'s: this
		// class only touches files, and ScreenshotMetadata lives in the gui package that calls
		// into this one, not the other way around.
		ScreenshotImageCache.release(selected);
		ScreenshotImageCache.release(frame);
		return true;
	}

	/**
	 * Reads the flag back off the disk every time, deliberately. The GUI asks
	 * {@code ScreenshotMetadata.isStarred} instead, since it asks once per frame; what is
	 * left here is the ground-truth check the game test uses to prove a toggle actually
	 * reached the file rather than only the cache in front of it.
	 */
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
	 *
	 * <p>When the selection is a burst key, its sub-frames go first and the key only if every
	 * one of them actually went. Deleting the key after a frame failure would both manufacture
	 * orphans and make a "deleted" report a lie about a file that is still there; this way a
	 * partial failure leaves an honest row behind with a smaller badge.
	 */
	public static boolean delete() {
		if (selected == null) {
			return false;
		}
		File dir = selected.getParentFile();
		List<File> frames = burstFrames(dir, selected);
		for (File frame : frames) {
			try {
				Files.delete(frame.toPath());
			} catch (IOException e) {
				VoxelCamClient.LOGGER.error("Failed to delete burst frame {}", frame, e);
				return false;
			}
			ScreenshotImageCache.release(frame);
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
