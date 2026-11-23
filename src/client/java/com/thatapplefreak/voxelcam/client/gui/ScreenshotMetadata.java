package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.AnimatedPng;
import com.thatapplefreak.voxelcam.client.screenshot.BurstFrame;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTag;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureContext;
import com.thatapplefreak.voxelcam.client.screenshot.Favorite;
import com.thatapplefreak.voxelcam.client.screenshot.PngDimensions;
import com.thatapplefreak.voxelcam.client.screenshot.PngTextChunk;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * File facts shown alongside screenshots. Every one of them is cached per file — the reads
 * that open the file (dimensions from the PNG header, the embedded capture context, the
 * starred flag) and the two that only stat it ({@link #fileSize(File)},
 * {@link #displayName(File)}). Every one of them is asked for on every extracted frame and
 * so may not touch the disk twice for the same file: a list row wants all but
 * {@link #captureContext(File)}, once per visible row, and the selection is asked again on
 * the same frame — dimensions, size and context for the preview caption,
 * {@link #isStarred(File)} for the favorite button's tint.
 *
 * <p>A read that failed is cached as well, as an absent answer — the file that cannot be read
 * is the one whose retry costs the most, and a half-finished save is exactly what leaves one
 * in the folder. {@code ScreenshotImageCache.FAILED} takes the same line with a decode.
 *
 * <p>Entries live until the manager closes and calls {@link #forgetAll()}, unless something
 * changes what a path holds while it is open, and each thing that does evicts for itself. The
 * star toggle rewrites a file in place and calls {@link #forget(File)}; Set as key swaps two
 * files' bytes and calls {@link #forgetFile(File)} for both. A rename or a delete leaves the
 * bytes alone but takes the file away from its path, and that path can be given to another
 * screenshot before the manager closes, so the manager renames and deletes through
 * {@link #renameSelectedAndForget} and {@link #deleteSelectedAndForget}. A capture needs none
 * of this: it is written under a name that is free, and a name the manager freed was evicted
 * when it was.
 */
public final class ScreenshotMetadata {

	public record Dimensions(int width, int height) {
	}

	private static final Map<File, Optional<Dimensions>> DIMENSIONS = new HashMap<>();
	private static final Map<File, Optional<CaptureContext>> CONTEXTS = new HashMap<>();
	private static final Map<File, Optional<BurstFrame>> BURST_FRAMES = new HashMap<>();
	private static final Map<File, Optional<MomentTag>> MOMENTS = new HashMap<>();
	private static final Map<File, Integer> ANIMATION_FRAMES = new HashMap<>();
	private static final Map<File, Boolean> STARRED = new HashMap<>();
	private static final Map<File, String> NAMES = new HashMap<>();
	private static final Map<File, String> SIZES = new HashMap<>();
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm");
	// String.replaceFirst and String.matches compile a fresh Pattern on every call. The
	// NAMES cache means that is a compile per file rather than per frame, but the fill path
	// still runs for every file whose row is scrolled into view, once per manager session —
	// a large folder browsed end to end is a thousand throwaway compiles for two constants.
	private static final Pattern PNG_SUFFIX = Pattern.compile("(?i)\\.png$");
	// Deliberately no UNICODE_CHARACTER_CLASS: ScreenshotNamer stamps its timestamp with
	// Locale.ROOT precisely because this is an ASCII-only \d test, and widening it here
	// would quietly start matching names ScreenshotNamer can never produce. The optional
	// "_burstNN" tail matches ScreenshotNamer.burstFrameName's own shape, so an orphaned
	// sub-frame (one whose key has been renamed or deleted, and so is listed on its own) still
	// gets a friendly time instead of a raw stamp.
	private static final Pattern CAPTURE_NAME =
			Pattern.compile("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}(_\\d+)?(_burst\\d+)?");

	private ScreenshotMetadata() {
	}

	/**
	 * Reads width/height from the PNG IHDR chunk. Decoding the whole image just to
	 * learn its size would be far more expensive, and this runs for every visible row.
	 *
	 * <p>A header that would not read is remembered as absent rather than left uncached. A
	 * 0-byte or truncated {@code .png}, or a file that is not a PNG at all, fails the header read
	 * every time, so retrying costs an open, a read and a close — per visible row per extracted
	 * frame, on the render thread, for the one file that is guaranteed never to answer.
	 */
	public static Dimensions dimensions(File file) {
		if (file == null) {
			return null;
		}
		Optional<Dimensions> cached = DIMENSIONS.get(file);
		if (cached != null) {
			return cached.orElse(null);
		}
		PngDimensions.Dimensions read = PngDimensions.read(file);
		Dimensions dimensions = read == null ? null : new Dimensions(read.width(), read.height());
		DIMENSIONS.put(file, Optional.ofNullable(dimensions));
		return dimensions;
	}

	/**
	 * Reads back the tags {@code CaptureContext.toTags()} writes at capture time, cached the
	 * same way {@link #dimensions(File)} is, absent answers included — and this one reads the
	 * whole file to produce them. The absence here is the ordinary answer rather than a fault:
	 * files captured before this feature shipped, or by vanilla F2, or by another mod sharing
	 * the folder, simply have no tags — this returns null for those rather than a half-built
	 * context, and caches that so the preview pane doesn't re-read the whole file every frame
	 * it stays selected.
	 */
	public static CaptureContext captureContext(File file) {
		if (file == null) {
			return null;
		}
		Optional<CaptureContext> cached = CONTEXTS.get(file);
		if (cached != null) {
			return cached.orElse(null);
		}
		CaptureContext context = CaptureContext.fromTags(PngTextChunk.read(file));
		CONTEXTS.put(file, Optional.ofNullable(context));
		return context;
	}

	/**
	 * Cached the same way {@link #dimensions(File)} is, and the one here that most needs it:
	 * the flag is an {@code iTXt} chunk, so {@code Favorite.isStarred} reads the whole PNG,
	 * and this is asked for by every visible row's thumbnail badge, by the favorite button's
	 * tint for the selection, and by the favorites-only filter — the first two once per
	 * extracted frame each.
	 */
	public static boolean isStarred(File file) {
		if (file == null) {
			return false;
		}
		Boolean cached = STARRED.get(file);
		if (cached != null) {
			return cached;
		}
		boolean starred = Favorite.isStarred(file);
		STARRED.put(file, starred);
		return starred;
	}

	/**
	 * The burst position tags a frame was captured with — group id, index, and how long after
	 * the key frame it was taken — or null if this file has none. Cached the same way {@link
	 * #captureContext(File)} is, a whole-file read included: only the burst-frames popup ever
	 * calls this, never a row or the badge (the badge counts sub-frames from a free directory
	 * scan in {@code VoxelCamIO} instead), so it must never be asked for per row.
	 */
	public static BurstFrame burstFrame(File file) {
		if (file == null) {
			return null;
		}
		Optional<BurstFrame> cached = BURST_FRAMES.get(file);
		if (cached != null) {
			return cached.orElse(null);
		}
		BurstFrame frame = BurstFrame.fromTags(PngTextChunk.read(file));
		BURST_FRAMES.put(file, Optional.ofNullable(frame));
		return frame;
	}

	/**
	 * Why an automatic capture exists — which trigger armed it and what it was about — or null for
	 * the screenshots somebody pressed a key for, which is most of them. Cached exactly the way
	 * {@link #captureContext(File)} is, and read for the same reason: it feeds the details line
	 * under the preview, which redraws every frame for the one selected file.
	 */
	public static MomentTag moment(File file) {
		if (file == null) {
			return null;
		}
		Optional<MomentTag> cached = MOMENTS.get(file);
		if (cached != null) {
			return cached.orElse(null);
		}
		MomentTag tag = MomentTag.fromTags(PngTextChunk.read(file));
		MOMENTS.put(file, Optional.ofNullable(tag));
		return tag;
	}

	/**
	 * How many frames an exported burst animation holds, or 0 for every other screenshot. An APNG
	 * opens as its first frame everywhere in the manager, so this is the only thing that says it
	 * moves. Cached and read exactly as {@link #moment(File)} is, for the same details line.
	 */
	public static int animationFrames(File file) {
		if (file == null) {
			return 0;
		}
		Integer cached = ANIMATION_FRAMES.get(file);
		if (cached != null) {
			return cached;
		}
		int frames = AnimatedPng.frameCount(PngTextChunk.read(file));
		ANIMATION_FRAMES.put(file, frames);
		return frames;
	}

	public static void forgetAll() {
		ANIMATION_FRAMES.clear();
		DIMENSIONS.clear();
		CONTEXTS.clear();
		BURST_FRAMES.clear();
		MOMENTS.clear();
		STARRED.clear();
		NAMES.clear();
		SIZES.clear();
	}

	/**
	 * Drops one file's cached facts after {@code Favorite.setStarred} rewrites it. The star is
	 * an {@code iTXt} chunk written into the PNG itself, so the rewrite also changes the file's
	 * length and its modification time — the cached size and display name have to go with the
	 * flag or the caption keeps quoting the pre-toggle bytes.
	 *
	 * <p>Dimensions and capture context deliberately stay, a cached failure included: the
	 * toggle splices its chunk in after the IHDR without touching either the IHDR or the
	 * capture tags, and it cannot turn a file whose header would not read into one that will.
	 * {@link #forgetAll()} on manager close is what gives a failed read another chance.
	 */
	public static void forget(File file) {
		STARRED.remove(file);
		NAMES.remove(file);
		SIZES.remove(file);
	}

	/**
	 * Drops every cached fact for one file, dimensions and capture context included — unlike
	 * {@link #forget(File)}. A star toggle only splices a chunk in after the IHDR, so those two
	 * cannot have changed; a "Set as key" swap rewrites the file's actual bytes at that path
	 * (it swaps the two files' contents, not their tags), so nothing cached about it can be
	 * trusted afterwards.
	 */
	public static void forgetFile(File file) {
		ANIMATION_FRAMES.remove(file);
		DIMENSIONS.remove(file);
		CONTEXTS.remove(file);
		BURST_FRAMES.remove(file);
		MOMENTS.remove(file);
		STARRED.remove(file);
		NAMES.remove(file);
		SIZES.remove(file);
	}

	/**
	 * {@code VoxelCamIO.rename}, as the manager has to call it: with every path the rename takes
	 * a file away from, or brings one to, evicted. Every cache here is keyed by path, and a
	 * path can change hands while the manager is open. Rename a starred {@code castle} away,
	 * give another screenshot its name, and without this the new {@code castle} is drawn with
	 * the old one's star, size and dimensions.
	 *
	 * <p>The path a rename lands on is evicted as well as the one it leaves: a file can lose
	 * its name without the manager's doing, and what was cached under it would be waiting.
	 *
	 * <p>Here, not in {@code VoxelCamIO}, because that class only touches files and does not
	 * reach into this package; and here, not in {@code RenamePopup}, so that a plain JUnit
	 * test can reach it, the reason {@code VoxelCamIO.nameCollides} is not in the popup either.
	 */
	public static File renameSelectedAndForget(File screenshotsDir, String newName) {
		// Before the rename: afterwards the frames are no longer listed under their old names.
		forgetGroup(screenshotsDir, VoxelCamIO.getSelectedPhoto());
		File renamed = VoxelCamIO.rename(screenshotsDir, newName);
		if (renamed != null) {
			forgetGroup(screenshotsDir, renamed);
		}
		return renamed;
	}

	/**
	 * {@code VoxelCamIO.delete}, with the deleted paths evicted for the same reason
	 * {@link #renameSelectedAndForget} evicts the ones a rename leaves. Whatever the delete
	 * reports: a refused one may still have removed some of a burst's frames.
	 *
	 * @param listed the rows the manager is showing, which {@code VoxelCamIO.delete} picks the
	 *        next selection from.
	 */
	public static boolean deleteSelectedAndForget(List<File> listed) {
		File target = VoxelCamIO.getSelectedPhoto();
		if (target != null) {
			forgetGroup(target.getParentFile(), target);
		}
		return VoxelCamIO.delete(listed);
	}

	/** A screenshot and the burst frames folded under it, which rename and delete take with it.
	 * An evicted entry that turns out not to have moved costs one re-read. */
	private static void forgetGroup(File screenshotsDir, File key) {
		if (key == null) {
			return;
		}
		forgetFile(key);
		for (File frame : VoxelCamIO.burstFrames(screenshotsDir, key)) {
			forgetFile(frame);
		}
	}

	/**
	 * Vanilla-style capture names like {@code 2026-08-17_15.38.16_3} are long enough
	 * to be trimmed to uselessness in a list row, and the raw timestamp is noise
	 * anyway. Those get shown as a friendly time; anything the user renamed is shown
	 * verbatim, since they chose it.
	 *
	 * <p>Cached the same way {@link #dimensions(File)} is: a capture name routes to
	 * {@link #relativeTime(File)}, which stats the file, and this is asked for every visible
	 * row every frame. The cost of that is a label that still reads "Today" if the manager is
	 * left open across midnight, which lasts until the manager closes.
	 */
	public static String displayName(File file) {
		String cached = NAMES.get(file);
		if (cached != null) {
			return cached;
		}
		String name = PNG_SUFFIX.matcher(file.getName()).replaceFirst("");
		String display = CAPTURE_NAME.matcher(name).matches()
				? relativeTime(file)
				: name;
		NAMES.put(file, display);
		return display;
	}

	/**
	 * "Today 14:32", "Yesterday 14:32", otherwise "17 Aug, 14:32". Deliberately left as an
	 * uncached formatting primitive — {@link #displayName(File)} is the per-frame caller and
	 * caches its own result, so a caller that wants a freshly stat'd label still has one.
	 */
	public static String relativeTime(File file) {
		LocalDateTime when = modifiedAt(file);
		LocalDate today = LocalDate.now();
		if (when.toLocalDate().equals(today)) {
			return "Today " + TIME.format(when);
		}
		if (when.toLocalDate().equals(today.minusDays(1))) {
			return "Yesterday " + TIME.format(when);
		}
		return DATE_TIME.format(when);
	}

	private static LocalDateTime modifiedAt(File file) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
	}

	/**
	 * Cached the same way {@link #dimensions(File)} is, for the same reason: the list row and
	 * the preview caption both ask for this on every extracted frame, and {@code File} caches
	 * nothing, so each call is a fresh stat. {@code Locale.ROOT} because the caption sits next
	 * to the "1920×1080" the PNG header gives, which has no locale either.
	 */
	public static String fileSize(File file) {
		String cached = SIZES.get(file);
		if (cached != null) {
			return cached;
		}
		long bytes = file.length();
		String size;
		if (bytes < 1024) {
			size = bytes + " B";
		} else if (bytes < 1024 * 1024) {
			size = String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
		} else {
			size = String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
		}
		SIZES.put(file, size);
		return size;
	}
}
