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
import org.junit.jupiter.api.Assumptions;
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
	void aFreeNameDoesNotCollide() throws IOException {
		File original = shot("sunset.png", 1_000L);

		assertFalse(VoxelCamIO.nameCollides(dir.toFile(), "dawn", original));
	}

	@Test
	void anotherFilesNameCollides() throws IOException {
		File original = shot("sunset.png", 1_000L);
		shot("dawn.png", 1_000L);

		assertTrue(VoxelCamIO.nameCollides(dir.toFile(), "dawn", original));
	}

	/**
	 * The case-only rename the popup used to refuse. It is only a probe worth making on a
	 * case-insensitive volume (macOS, Windows) — where the candidate resolves to the very
	 * file being renamed — so the assumption skips rather than passing vacuously elsewhere.
	 */
	@Test
	void aCaseVariantOfTheFileItselfIsNotACollision() throws IOException {
		File original = shot("sunset.png", 1_000L);
		File candidate = dir.resolve("Sunset.png").toFile();
		Assumptions.assumeTrue(candidate.exists(), "case-sensitive filesystem: nothing to reproduce here");

		assertFalse(VoxelCamIO.nameCollides(dir.toFile(), "Sunset", original));
	}

	/**
	 * A file that is no longer there cannot be compared for identity — isSameFile throws —
	 * and the name really is another file's, so the answer has to stay yes rather than
	 * letting the exception open the way to clobbering it.
	 */
	@Test
	void aVanishedCurrentFileStillLeavesTheNameTaken() throws IOException {
		File gone = dir.resolve("gone.png").toFile();
		shot("dawn.png", 1_000L);

		assertTrue(VoxelCamIO.nameCollides(dir.toFile(), "dawn", gone));
	}

	/**
	 * Recapitalising is a real rename, not a no-op: the guard in {@link VoxelCamIO#rename}
	 * compares names rather than {@code File}s so that it stays a no-op check on Windows,
	 * where {@code File.equals} folds case and would refuse this outright.
	 */
	@Test
	void renameToACaseVariantGoesThrough() throws IOException {
		File original = shot("sunset.png", 1_000L);
		VoxelCamIO.selectPhoto(original);

		File renamed = VoxelCamIO.rename(dir.toFile(), "Sunset");

		assertEquals("Sunset.png", renamed.getName());
		assertEquals(List.of("Sunset.png"), List.of(dir.toFile().list()));
		assertEquals(renamed, VoxelCamIO.getSelectedPhoto());
	}

	@Test
	void renameWithNothingSelectedDoesNothing() {
		assertNull(VoxelCamIO.rename(dir.toFile(), "whatever"));
	}

	/**
	 * The failure the popup has to report. {@code renameTo} answers a source that another
	 * program moved or deleted with a bare false and no exception, so null is the only
	 * signal there is — and it has to be distinguishable from a rename that happened, or
	 * the player is told nothing while the old name stays on screen.
	 */
	@Test
	void renameOfAFileThatIsGoneFails() throws IOException {
		File original = shot("sunset.png", 1_000L);
		VoxelCamIO.selectPhoto(original);
		Files.delete(original.toPath());

		assertNull(VoxelCamIO.rename(dir.toFile(), "dawn"));
		// Nothing was created under the new name either: there is no half-done rename to
		// leave the selection pointing at.
		assertFalse(dir.resolve("dawn.png").toFile().exists());
		assertEquals(original, VoxelCamIO.getSelectedPhoto());
	}

	@Test
	void deleteRemovesTheFileAndClearsTheSelection() throws IOException {
		File doomed = shot("doomed.png", 1_000L);
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");
		VoxelCamIO.selectPhoto(doomed);

		assertTrue(VoxelCamIO.delete());

		assertFalse(doomed.exists());
		assertNull(VoxelCamIO.getSelectedPhoto());
		assertFalse(VoxelCamIO.getScreenShotFiles().contains(doomed));
	}

	/**
	 * A non-empty directory named like a screenshot is listed the same as a file and
	 * refuses to be deleted, which is the portable stand-in for the file a Windows image
	 * viewer holds open — it relies on updateScreenShotFilesList listing by extension
	 * alone, so adding an isFile() guard there means picking another way to fail. The row and the selection have to survive it: the manager
	 * re-lists the directory on the way back from the popup, so an entry dropped anyway
	 * would silently reappear and make the confirmation look like it had worked.
	 */
	@Test
	void aRefusedDeleteKeepsTheEntryAndTheSelection() throws IOException {
		File stubborn = dir.resolve("stubborn.png").toFile();
		assertTrue(stubborn.mkdir());
		Files.writeString(stubborn.toPath().resolve("holding-it-open.txt"), "x");
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");
		VoxelCamIO.selectPhoto(stubborn);

		assertFalse(VoxelCamIO.delete());

		assertTrue(stubborn.exists());
		assertEquals(stubborn, VoxelCamIO.getSelectedPhoto());
		assertTrue(VoxelCamIO.getScreenShotFiles().contains(stubborn));
	}

	@Test
	void deleteWithNothingSelectedIsHarmless() throws IOException {
		File keep = shot("keep.png", 1_000L);
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		assertFalse(VoxelCamIO.delete());

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
