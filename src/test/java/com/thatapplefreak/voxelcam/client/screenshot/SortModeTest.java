package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * current()/setCurrent() are static, session-wide state — the same kind BigScreenshot
 * holds for its capture size — so every test restores the default afterwards.
 */
class SortModeTest {

	@TempDir
	Path dir;

	@AfterEach
	void resetCurrent() {
		SortMode.setCurrent(SortMode.DATE_NEWEST);
	}

	private File file(String name, long modifiedAt, long bytes) throws IOException {
		File file = dir.resolve(name).toFile();
		Files.write(file.toPath(), new byte[(int) bytes]);
		assertTrue(file.setLastModified(modifiedAt));
		return file;
	}

	/** A real PNG header carrying width and height, for the dimensions modes. */
	private File png(String name, int width, int height) throws IOException {
		byte[] ihdr = new byte[13];
		writeInt(ihdr, 0, width);
		writeInt(ihdr, 4, height);
		ihdr[8] = 8;
		ihdr[9] = 2;

		byte[] chunk = new byte[8 + ihdr.length + 4];
		writeInt(chunk, 0, ihdr.length);
		chunk[4] = 'I';
		chunk[5] = 'H';
		chunk[6] = 'D';
		chunk[7] = 'R';
		System.arraycopy(ihdr, 0, chunk, 8, ihdr.length);
		CRC32 crc = new CRC32();
		crc.update(chunk, 4, 4 + ihdr.length);
		writeInt(chunk, 8 + ihdr.length, (int) crc.getValue());

		byte[] signature = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
		File file = dir.resolve(name).toFile();
		try (var out = Files.newOutputStream(file.toPath())) {
			out.write(signature);
			out.write(chunk);
		}
		return file;
	}

	private static void writeInt(byte[] target, int at, int value) {
		target[at] = (byte) (value >>> 24);
		target[at + 1] = (byte) (value >>> 16);
		target[at + 2] = (byte) (value >>> 8);
		target[at + 3] = (byte) value;
	}

	private static List<String> sortedNames(List<File> files, SortMode mode) {
		List<File> copy = new ArrayList<>(files);
		mode.sort(copy);
		return copy.stream().map(File::getName).toList();
	}

	/**
	 * Counts the reads a sort key costs. {@code lastModified}/{@code length} are a stat each
	 * and {@code PngDimensions.read} opens the file through {@code getPath}, so a comparator
	 * that re-derives its key pays them O(n log n) times; {@link SortMode#sort} must pay them
	 * once per file. {@code getName()} reads {@code File}'s path field directly and is not
	 * counted here, which is what lets the name modes assert zero of all three.
	 */
	private static final class CountingFile extends File {

		private int lastModifiedCalls;
		private int lengthCalls;
		private int pathCalls;

		CountingFile(File real) {
			super(real.getPath());
		}

		@Override
		public long lastModified() {
			lastModifiedCalls++;
			return super.lastModified();
		}

		@Override
		public long length() {
			lengthCalls++;
			return super.length();
		}

		@Override
		public String getPath() {
			pathCalls++;
			return super.getPath();
		}
	}

	private List<CountingFile> counted(File... files) {
		List<CountingFile> counting = new ArrayList<>();
		for (File file : files) {
			counting.add(new CountingFile(file));
		}
		return counting;
	}

	private static void sortCounted(List<CountingFile> files, SortMode mode) {
		List<File> copy = new ArrayList<>(files);
		mode.sort(copy);
	}

	@Test
	void theDateSortReadsEachFilesTimestampOnce() throws IOException {
		List<CountingFile> files = counted(file("a.png", 3_000L, 1), file("b.png", 1_000L, 1),
				file("c.png", 2_000L, 1), file("d.png", 4_000L, 1));

		sortCounted(files, SortMode.DATE_NEWEST);

		for (CountingFile file : files) {
			assertEquals(1, file.lastModifiedCalls, file.getName() + " was stat'd more than once");
		}
	}

	@Test
	void theSizeSortReadsEachFilesLengthOnce() throws IOException {
		List<CountingFile> files = counted(file("a.png", 1_000L, 30), file("b.png", 1_000L, 10),
				file("c.png", 1_000L, 20), file("d.png", 1_000L, 40));

		sortCounted(files, SortMode.SIZE_LARGEST);

		for (CountingFile file : files) {
			assertEquals(1, file.lengthCalls, file.getName() + " was stat'd more than once");
		}
	}

	/**
	 * The costliest key of the four: every comparison used to open, read and close the PNG.
	 * How many times one open asks the {@code File} for its path is a JDK internal, so the
	 * cost of a single read is measured rather than assumed — what is pinned is that the
	 * sort pays it once per file however many that is.
	 */
	@Test
	void theDimensionsSortOpensEachFileOnce() throws IOException {
		CountingFile probe = new CountingFile(png("probe.png", 1, 1));
		PngDimensions.read(probe);
		int perRead = probe.pathCalls;
		List<CountingFile> files = counted(png("a.png", 30, 30), png("b.png", 10, 10),
				png("c.png", 20, 20), png("d.png", 40, 40));

		sortCounted(files, SortMode.DIMENSIONS_LARGEST);

		for (CountingFile file : files) {
			assertEquals(perRead, file.pathCalls, file.getName() + " was opened more than once");
		}
	}

	/** The name modes have no reason to touch the disk at all, and a guard added to the listing would. */
	@Test
	void theNameSortTouchesTheDiskNotAtAll() throws IOException {
		List<CountingFile> files = counted(file("c.png", 1_000L, 1), file("a.png", 1_000L, 1),
				file("d.png", 1_000L, 1), file("b.png", 1_000L, 1));

		sortCounted(files, SortMode.NAME_A_TO_Z);

		for (CountingFile file : files) {
			assertEquals(0, file.lastModifiedCalls + file.lengthCalls + file.pathCalls,
					file.getName() + " was read from disk during a name sort");
		}
	}

	/**
	 * Same key on every file: without a tiebreak the order is whatever {@code listFiles()}
	 * returned, which is unspecified. Descending reverses the key alone — a reversal applied
	 * to the composed comparator would flip the names too, and no ordering test above would
	 * notice.
	 */
	@Test
	void tiesBreakOnTheNameAscendingInBothDirections() throws IOException {
		File b = file("b.png", 1_000L, 1);
		File a = file("a.png", 1_000L, 1);
		File c = file("c.png", 1_000L, 1);
		List<File> files = List.of(b, c, a);

		assertEquals(List.of("a.png", "b.png", "c.png"), sortedNames(files, SortMode.DATE_NEWEST));
		assertEquals(List.of("a.png", "b.png", "c.png"), sortedNames(files, SortMode.DATE_OLDEST));
	}

	@Test
	void sortsByDateInBothDirections() throws IOException {
		File old = file("old.png", 1_000L, 1);
		File newest = file("newest.png", 3_000L, 1);
		File middle = file("middle.png", 2_000L, 1);
		List<File> files = List.of(old, newest, middle);

		assertEquals(List.of("newest.png", "middle.png", "old.png"), sortedNames(files, SortMode.DATE_NEWEST));
		assertEquals(List.of("old.png", "middle.png", "newest.png"), sortedNames(files, SortMode.DATE_OLDEST));
	}

	@Test
	void sortsByNameCaseInsensitivelyInBothDirections() throws IOException {
		File banana = file("Banana.png", 1_000L, 1);
		File apple = file("apple.png", 1_000L, 1);
		File cherry = file("cherry.png", 1_000L, 1);
		List<File> files = List.of(banana, apple, cherry);

		assertEquals(List.of("apple.png", "Banana.png", "cherry.png"), sortedNames(files, SortMode.NAME_A_TO_Z));
		assertEquals(List.of("cherry.png", "Banana.png", "apple.png"), sortedNames(files, SortMode.NAME_Z_TO_A));
	}

	@Test
	void sortsByFileSizeInBothDirections() throws IOException {
		File small = file("small.png", 1_000L, 10);
		File big = file("big.png", 1_000L, 1000);
		File medium = file("medium.png", 1_000L, 100);
		List<File> files = List.of(small, big, medium);

		assertEquals(List.of("big.png", "medium.png", "small.png"), sortedNames(files, SortMode.SIZE_LARGEST));
		assertEquals(List.of("small.png", "medium.png", "big.png"), sortedNames(files, SortMode.SIZE_SMALLEST));
	}

	@Test
	void sortsByPixelAreaInBothDirections() throws IOException {
		File small = png("small.png", 10, 10);
		File big = png("big.png", 1000, 1000);
		File medium = png("medium.png", 100, 100);
		List<File> files = List.of(small, big, medium);

		assertEquals(List.of("big.png", "medium.png", "small.png"), sortedNames(files, SortMode.DIMENSIONS_LARGEST));
		assertEquals(List.of("small.png", "medium.png", "big.png"), sortedNames(files, SortMode.DIMENSIONS_SMALLEST));
	}

	/** Unreadable dimensions must not throw mid-sort — they sort as 0×0 instead. */
	@Test
	void filesWithUnreadableDimensionsSortAsSmallest() throws IOException {
		Files.writeString(dir.resolve("bogus.png"), "not a png");
		File bogus = dir.resolve("bogus.png").toFile();
		File real = png("real.png", 10, 10);
		List<File> files = List.of(bogus, real);

		assertEquals(List.of("real.png", "bogus.png"), sortedNames(files, SortMode.DIMENSIONS_LARGEST));
	}

	@Test
	void nextCyclesThroughEveryModeAndWrapsAround() {
		SortMode mode = SortMode.DATE_NEWEST;
		for (int i = 0; i < SortMode.values().length - 1; i++) {
			mode = mode.next();
		}
		assertEquals(SortMode.values()[SortMode.values().length - 1], mode);
		assertSame(SortMode.DATE_NEWEST, mode.next());
	}

	@Test
	void currentDefaultsToDateNewestAndCanBeChanged() {
		assertSame(SortMode.DATE_NEWEST, SortMode.current());

		SortMode.setCurrent(SortMode.NAME_A_TO_Z);

		assertSame(SortMode.NAME_A_TO_Z, SortMode.current());
	}
}
