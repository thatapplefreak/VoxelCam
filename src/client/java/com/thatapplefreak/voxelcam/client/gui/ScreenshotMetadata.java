package com.thatapplefreak.voxelcam.client.gui;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/** Cheap, cached file facts shown alongside screenshots. */
public final class ScreenshotMetadata {

	public record Dimensions(int width, int height) {
	}

	private static final Map<File, Dimensions> DIMENSIONS = new HashMap<>();
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm");
	private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

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
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			// 8-byte PNG signature, 4-byte chunk length, 4-byte "IHDR", then width/height.
			in.skipNBytes(16);
			Dimensions dimensions = new Dimensions(in.readInt(), in.readInt());
			DIMENSIONS.put(file, dimensions);
			return dimensions;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	public static void forgetAll() {
		DIMENSIONS.clear();
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

	/** "14:32" for today, "Yesterday 14:32", otherwise "17 Aug, 14:32". */
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
