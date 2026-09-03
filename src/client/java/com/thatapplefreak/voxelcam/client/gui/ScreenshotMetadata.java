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
import java.util.Map;
import java.util.Optional;

/**
 * File facts shown alongside screenshots. The reads that open the file — dimensions from the
 * PNG header, the embedded capture context, the starred flag — are cached until
 * {@link #forgetAll()}. {@link #fileSize(File)} and {@link #displayName(File)} are not: both
 * stat the file on every call, once per visible row per frame.
 */
public final class ScreenshotMetadata {

	public record Dimensions(int width, int height) {
	}

	private static final Map<File, Dimensions> DIMENSIONS = new HashMap<>();
	private static final Map<File, Optional<CaptureContext>> CONTEXTS = new HashMap<>();
	private static final Map<File, Boolean> STARRED = new HashMap<>();
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
	}

	/** Drops one file's cached starred flag after {@code Favorite.setStarred} rewrites it. */
	public static void forget(File file) {
		STARRED.remove(file);
	}

	/**
	 * Vanilla-style capture names like {@code 2026-08-17_15.38.16_3} are long enough
	 * to be trimmed to uselessness in a list row, and the raw timestamp is noise
	 * anyway. Those get shown as a friendly time; anything the user renamed is shown
	 * verbatim, since they chose it.
	 */
	public static String displayName(File file) {
		String name = file.getName().replaceFirst("(?i)\\.png$", "");
		if (name.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}(_\\d+)?")) {
			return relativeTime(file);
		}
		return name;
	}

	/** "Today 14:32", "Yesterday 14:32", otherwise "17 Aug, 14:32". */
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

	public static String fileSize(File file) {
		long bytes = file.length();
		if (bytes < 1024) {
			return bytes + " B";
		}
		if (bytes < 1024 * 1024) {
			return String.format("%.0f KB", bytes / 1024.0);
		}
		return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
	}
}
