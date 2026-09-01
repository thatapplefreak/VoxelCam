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
		copy.sort(mode.comparator());
		return copy.stream().map(File::getName).toList();
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
