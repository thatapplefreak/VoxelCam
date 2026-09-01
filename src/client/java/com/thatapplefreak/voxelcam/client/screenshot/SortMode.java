package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.File;
import java.util.Comparator;
import java.util.Locale;

/**
 * The four things a screenshot list can be ordered by, each ascending or descending.
 * Held as an in-memory static default for the client session, the same pattern
 * {@link BigScreenshot} uses for its capture size — no persisted config.
 */
public enum SortMode {

	DATE_NEWEST("date.desc", Comparator.comparingLong(File::lastModified).reversed()),
	DATE_OLDEST("date.asc", Comparator.comparingLong(File::lastModified)),
	NAME_A_TO_Z("name.asc", Comparator.comparing(SortMode::lowerName)),
	NAME_Z_TO_A("name.desc", Comparator.comparing(SortMode::lowerName).reversed()),
	SIZE_LARGEST("size.desc", Comparator.comparingLong(File::length).reversed()),
	SIZE_SMALLEST("size.asc", Comparator.comparingLong(File::length)),
	DIMENSIONS_LARGEST("dimensions.desc", Comparator.comparingLong(SortMode::pixelCount).reversed()),
	DIMENSIONS_SMALLEST("dimensions.asc", Comparator.comparingLong(SortMode::pixelCount));

	private static volatile SortMode current = DATE_NEWEST;

	private final String labelKey;
	private final Comparator<File> comparator;

	SortMode(String labelSuffix, Comparator<File> comparator) {
		this.labelKey = "voxelcam.sort." + labelSuffix;
		this.comparator = comparator;
	}

	public Comparator<File> comparator() {
		return comparator;
	}

	public String labelKey() {
		return labelKey;
	}

	/** Cycles through every mode in a fixed order, for a single toggle button. */
	public SortMode next() {
		SortMode[] modes = values();
		return modes[(ordinal() + 1) % modes.length];
	}

	public static SortMode current() {
		return current;
	}

	public static void setCurrent(SortMode mode) {
		current = mode;
	}

	private static String lowerName(File file) {
		return file.getName().toLowerCase(Locale.ROOT);
	}

	/** A file whose dimensions can't be read sorts as if it were 0×0. */
	private static long pixelCount(File file) {
		PngDimensions.Dimensions dimensions = PngDimensions.read(file);
		return dimensions == null ? 0 : (long) dimensions.width() * dimensions.height();
	}
}
