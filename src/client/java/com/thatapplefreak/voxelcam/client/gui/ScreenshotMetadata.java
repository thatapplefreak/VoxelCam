package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.CaptureContext;
import com.thatapplefreak.voxelcam.client.screenshot.Favorite;
import com.thatapplefreak.voxelcam.client.screenshot.PngDimensions;
import com.thatapplefreak.voxelcam.client.screenshot.PngTextChunk;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * File facts shown alongside screenshots. Every one of them is cached per file — the reads
 * that open the file (dimensions from the PNG header, the embedded capture context, the
 * starred flag) and the two that only stat it ({@link #fileSize(File)},
 * {@link #displayName(File)}). All five are asked for once per visible row per extracted
 * frame, so none of them may touch the disk twice for the same file.
 *
 * <p>Entries live until the manager closes and calls {@link #forgetAll()}; the one thing that
 * rewrites a listed screenshot in place while it is open is the star toggle, which calls
 * {@link #forget(File)} afterwards. Renaming or capturing produces a different {@code File}
 * key, so a rebuilt list reads those afresh without any eviction.
 */
public final class ScreenshotMetadata {

	public record Dimensions(int width, int height) {
	}

	private static final Map<File, Dimensions> DIMENSIONS = new HashMap<>();
	private static final Map<File, Optional<CaptureContext>> CONTEXTS = new HashMap<>();
	private static final Map<File, Boolean> STARRED = new HashMap<>();
	private static final Map<File, String> NAMES = new HashMap<>();
	private static final Map<File, String> SIZES = new HashMap<>();
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm");

	private ScreenshotMetadata() {
	}

	/**
	 * Reads width/height from the PNG IHDR chunk. Decoding the whole image just to
	 * learn its size would be far more expensive, and this runs for every visible row.
	 */
	public static Dimensions dimensions(File file) {
		if (file == null) {
			return null;
		}
		Dimensions cached = DIMENSIONS.get(file);
		if (cached != null) {
			return cached;
		}
		PngDimensions.Dimensions read = PngDimensions.read(file);
		if (read == null) {
			return null;
		}
		Dimensions dimensions = new Dimensions(read.width(), read.height());
		DIMENSIONS.put(file, dimensions);
		return dimensions;
	}

	/**
	 * Reads back the tags {@code CaptureContext.toTags()} writes at capture time, cached the
	 * same way {@link #dimensions(File)} is — including the negative case, since unlike
	 * dimensions() this reads the whole file. Files captured before this feature shipped, or
	 * by vanilla F2, or by another mod sharing the folder, simply have no tags — this returns
	 * null for those rather than a half-built context, and caches that so the preview pane
	 * doesn't re-read the whole file every frame it stays selected.
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
	 * Cached the same way {@link #dimensions(File)} is, since this is now checked for every
	 * visible row's thumbnail badge rather than just the selected file.
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

	public static void forgetAll() {
		DIMENSIONS.clear();
		CONTEXTS.clear();
		STARRED.clear();
		NAMES.clear();
		SIZES.clear();
	}

	/**
	 * Drops one file's cached facts after {@code Favorite.setStarred} rewrites it. The star is
	 * a tEXt chunk written into the PNG itself, so the rewrite also changes the file's length
	 * and its modification time — the cached size and display name have to go with the flag or
	 * the caption keeps quoting the pre-toggle bytes.
	 */
	public static void forget(File file) {
		STARRED.remove(file);
		NAMES.remove(file);
		SIZES.remove(file);
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
		String name = file.getName().replaceFirst("(?i)\\.png$", "");
		String display = name.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}(_\\d+)?")
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
