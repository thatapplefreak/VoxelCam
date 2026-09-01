package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * VoxelCamIO owns the file list, the selection, and the rename/delete the manager
 * drives. Its state is static and outlives a screen, so each test resets it.
 *
 * These exercise rename and delete for real, on real files in a temp directory —
 * they are the only operations in the mod that destroy user data.
 */
class VoxelCamIOTest {

	@TempDir
	Path dir;

	@BeforeEach
	void reset() {
		VoxelCamIO.selectPhoto(null);
		SortMode.setCurrent(SortMode.DATE_NEWEST);
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");
	}

	private File shot(String name, long modifiedAt) throws IOException {
		File file = dir.resolve(name).toFile();
		Files.writeString(file.toPath(), "x");
		assertTrue(file.setLastModified(modifiedAt));
		return file;
	}

	/** A minimal but real PNG, needed wherever a test exercises the favorite flag's splice. */
	private File realPng(String name) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
		out.writeBytes(chunk("IHDR", ihdrData(64, 32)));
		out.writeBytes(chunk("IDAT", new byte[0]));
		out.writeBytes(chunk("IEND", new byte[0]));

		File file = dir.resolve(name).toFile();
		Files.write(file.toPath(), out.toByteArray());
		return file;
	}

	private static byte[] ihdrData(int width, int height) {
		byte[] data = new byte[13];
		writeInt(data, 0, width);
		writeInt(data, 4, height);
		data[8] = 8;
		data[9] = 2;
		return data;
	}

	private static byte[] chunk(String type, byte[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeInt4(out, data.length);
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		out.writeBytes(typeBytes);
		out.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		writeInt4(out, (int) crc.getValue());
		return out.toByteArray();
	}

	private static void writeInt(byte[] target, int at, int value) {
		target[at] = (byte) (value >>> 24);
		target[at + 1] = (byte) (value >>> 16);
		target[at + 2] = (byte) (value >>> 8);
		target[at + 3] = (byte) value;
	}

	private static void writeInt4(ByteArrayOutputStream out, int value) {
		out.writeBytes(new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value });
	}

	@Test
	void listsOnlyPngFiles() throws IOException {
		shot("a.png", 1_000L);
		shot("notes.txt", 1_000L);
		shot("archive.zip", 1_000L);

		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		assertEquals(List.of("a.png"), names());
	}

	/** The extension check is case-insensitive, so .PNG from another tool still shows. */
	@Test
	void uppercaseExtensionStillCounts() throws IOException {
		shot("SHOT.PNG", 1_000L);

		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		assertEquals(List.of("SHOT.PNG"), names());
	}

	@Test
	void newestFirst() throws IOException {
		shot("old.png", 1_000L);
		shot("newest.png", 3_000L);
		shot("middle.png", 2_000L);

		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		assertEquals(List.of("newest.png", "middle.png", "old.png"), names());
	}

	/** {@link SortMode#current()} is what updateScreenShotFilesList actually sorts by. */
	@Test
	void listRespectsTheCurrentSortMode() throws IOException {
		shot("banana.png", 1_000L);
		shot("apple.png", 2_000L);
		shot("cherry.png", 1_500L);
		SortMode.setCurrent(SortMode.NAME_A_TO_Z);

		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		assertEquals(List.of("apple.png", "banana.png", "cherry.png"), names());
	}

	@Test
	void filterMatchesAnywhereInTheNameAndIgnoresCase() throws IOException {
		shot("sunset-base.png", 2_000L);
		shot("cave.png", 1_000L);

		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "BASE");

		assertEquals(List.of("sunset-base.png"), names());
	}

	@Test
	void missingDirectoryYieldsAnEmptyListRatherThanThrowing() {
		VoxelCamIO.updateScreenShotFilesList(dir.resolve("nope").toFile(), "");

		assertTrue(VoxelCamIO.getScreenShotFiles().isEmpty());
	}

	@Test
	void renameMovesTheFileAndFollowsTheSelection() throws IOException {
		File original = shot("2026-08-27_10.00.00.png", 1_000L);
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");
		VoxelCamIO.selectPhoto(original);

		File renamed = VoxelCamIO.rename(dir.toFile(), "sunset");

		assertEquals("sunset.png", renamed.getName());
		assertTrue(renamed.exists());
		assertFalse(original.exists());
		// The selection has to follow, or the next action targets a file that is gone.
		assertEquals(renamed, VoxelCamIO.getSelectedPhoto());
	}

	@Test
	void renamingToTheCurrentNameIsRefused() throws IOException {
		File original = shot("sunset.png", 1_000L);
		VoxelCamIO.selectPhoto(original);

		assertNull(VoxelCamIO.rename(dir.toFile(), "sunset"));
		assertTrue(original.exists());
	}

	@Test
	void renameWithNothingSelectedDoesNothing() {
		assertNull(VoxelCamIO.rename(dir.toFile(), "whatever"));
	}

	@Test
	void deleteRemovesTheFileAndClearsTheSelection() throws IOException {
		File doomed = shot("doomed.png", 1_000L);
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");
		VoxelCamIO.selectPhoto(doomed);

		VoxelCamIO.delete();

		assertFalse(doomed.exists());
		assertNull(VoxelCamIO.getSelectedPhoto());
		assertFalse(VoxelCamIO.getScreenShotFiles().contains(doomed));
	}

	@Test
	void deleteWithNothingSelectedIsHarmless() throws IOException {
		File keep = shot("keep.png", 1_000L);
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		VoxelCamIO.delete();

		assertTrue(keep.exists());
	}

	@Test
	void isSelectedFavoriteIsFalseWithNothingSelected() {
		assertFalse(VoxelCamIO.isSelectedFavorite());
	}

	@Test
	void toggleSelectedFavoriteFlipsTheFlag() throws IOException {
		File file = realPng("shot.png");
		VoxelCamIO.selectPhoto(file);
		assertFalse(VoxelCamIO.isSelectedFavorite());

		VoxelCamIO.toggleSelectedFavorite();
		assertTrue(VoxelCamIO.isSelectedFavorite());

		VoxelCamIO.toggleSelectedFavorite();
		assertFalse(VoxelCamIO.isSelectedFavorite());
	}

	@Test
	void toggleSelectedFavoriteWithNothingSelectedIsHarmless() {
		VoxelCamIO.toggleSelectedFavorite();
	}

	private static List<String> names() {
		return VoxelCamIO.getScreenShotFiles().stream().map(File::getName).toList();
	}
}
