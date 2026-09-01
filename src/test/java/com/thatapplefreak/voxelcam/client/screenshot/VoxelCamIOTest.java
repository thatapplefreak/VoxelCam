package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

	private static List<String> names() {
		return VoxelCamIO.getScreenShotFiles().stream().map(File::getName).toList();
	}
}
