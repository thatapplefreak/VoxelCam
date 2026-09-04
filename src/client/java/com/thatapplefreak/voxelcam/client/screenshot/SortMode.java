package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The four things a screenshot list can be ordered by, each ascending or descending.
 * Held as an in-memory static default for the client session, the same pattern
 * {@link BigScreenshot} uses for its capture size — no persisted config.
 *
 * A mode is a key to read off a file plus a direction rather than a ready-made
 * {@link Comparator}, because a comparator re-reads its key on every comparison and every
 * key here costs I/O: {@code File.lastModified()} and {@code File.length()} each stat the
 * file — {@code java.io.File} holds a path and caches nothing — and the dimensions key
 * opens and reads the PNG header. Sorting is the only thing that consumes a key, so
 * {@link #sort(List)} reads each one once instead of the O(n log n) times a comparator
 * would, and no comparator is exposed for a caller to reintroduce that with.
 */
public enum SortMode {

	DATE_NEWEST("date.desc", File::lastModified, true),
	DATE_OLDEST("date.asc", File::lastModified, false),
	NAME_A_TO_Z("name.asc", SortMode::lowerName, false),
	NAME_Z_TO_A("name.desc", SortMode::lowerName, true),
	SIZE_LARGEST("size.desc", File::length, true),
	SIZE_SMALLEST("size.asc", File::length, false),
	DIMENSIONS_LARGEST("dimensions.desc", SortMode::pixelCount, true),
	DIMENSIONS_SMALLEST("dimensions.asc", SortMode::pixelCount, false);

	private static volatile SortMode current = DATE_NEWEST;

	/** One file with its key already read, so the sort never asks the filesystem again. */
	private record Keyed(File file, Comparable<?> key) {
	}

	private final String labelKey;
	private final Function<File, ? extends Comparable<?>> key;
	private final boolean descending;

	<K extends Comparable<? super K>> SortMode(String labelSuffix, Function<File, K> key, boolean descending) {
		this.labelKey = "voxelcam.sort." + labelSuffix;
		this.key = key;
		this.descending = descending;
	}

	public String labelKey() {
		return labelKey;
	}

	/**
	 * Orders {@code files} in place. In place rather than returning a new list because the
	 * caller's list outlives this call and is mutated later — {@code VoxelCamIO.delete()}
	 * drops a row from it.
	 *
	 * Ties break on the file name, ascending in both directions, so that a burst of captures
	 * inside one second — or a filesystem whose mtime granularity is coarser than that —
	 * lands in a defined order rather than whatever order {@code listFiles()} happened to
	 * return. The reverse applies to the key alone for the same reason: reversing the whole
	 * comparator would flip the tiebreak with it.
	 */
	public void sort(List<File> files) {
		List<Keyed> keyed = new ArrayList<>(files.size());
		for (File file : files) {
			keyed.add(new Keyed(file, key.apply(file)));
		}
		Comparator<Keyed> byKey = Comparator.comparing(Keyed::key, SortMode::compareKeys);
		keyed.sort((descending ? byKey.reversed() : byKey).thenComparing(entry -> entry.file().getName()));
		for (int i = 0; i < keyed.size(); i++) {
			files.set(i, keyed.get(i).file());
		}
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

	/**
	 * The constructor's type parameter is what guarantees this: a mode's extractor returns
	 * one key type for every file, so the two keys reaching here are always mutually
	 * comparable. The field cannot carry that bound — the modes' key types differ.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static int compareKeys(Comparable<?> left, Comparable<?> right) {
		return ((Comparable) left).compareTo(right);
	}

	private static String lowerName(File file) {
		return file.getName().toLowerCase(Locale.ROOT);
	}

	/** A file whose dimensions can't be read sorts as if it were 0×0. */
	private static Long pixelCount(File file) {
		PngDimensions.Dimensions dimensions = PngDimensions.read(file);
		return dimensions == null ? 0 : (long) dimensions.width() * dimensions.height();
	}
}
