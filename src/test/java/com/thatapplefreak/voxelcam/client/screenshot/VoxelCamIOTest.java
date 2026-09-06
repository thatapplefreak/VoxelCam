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

	/**
	 * The manager holds its own copy of the selection and rebuilds itself whenever a popup
	 * closes, so by the time it re-lists the directory its copy names the file the rename
	 * moved away from. The newer file here is what makes that visible: the head of the list
	 * is a screenshot the player never touched, and that is what the next Delete would aim
	 * at.
	 */
	@Test
	void selectionFollowsARenameTheScreenHasNotSeen() throws IOException {
		File stale = shot("sunset.png", 1_000L);
		File newest = shot("newest.png", 2_000L);
		VoxelCamIO.selectPhoto(stale);

		File renamed = VoxelCamIO.rename(dir.toFile(), "dawn");
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");
		List<File> files = VoxelCamIO.getScreenShotFiles();

		assertEquals(newest, files.get(0));
		assertEquals(renamed, VoxelCamIO.selectionFor(files, stale));
	}

	/**
	 * The case-only rename 995e05f enabled, re-listed. {@code File.equals} folds case on
	 * Windows and does not elsewhere, so which of the two branches answers depends on the
	 * filesystem — the assertion is that the recapitalised file is found either way rather
	 * than falling through to the head of the list, and it compares Files, not names, for
	 * the same reason.
	 */
	@Test
	void selectionSurvivesACaseOnlyRename() throws IOException {
		File original = shot("sunset.png", 1_000L);
		shot("newest.png", 2_000L);
		VoxelCamIO.selectPhoto(original);

		File renamed = VoxelCamIO.rename(dir.toFile(), "Sunset");
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		assertEquals(renamed, VoxelCamIO.selectionFor(VoxelCamIO.getScreenShotFiles(), original));
	}

	/**
	 * A selection that is still listed wins over the one here, which is what keeps a failed
	 * delete's message pinned to the file it is about: that file is still on disk, and the
	 * rebuild the popup's return triggers must not move off it.
	 */
	@Test
	void aListedSelectionIsKeptOverTheOneInIO() throws IOException {
		File shown = shot("shown.png", 1_000L);
		File other = shot("other.png", 2_000L);
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");
		VoxelCamIO.selectPhoto(other);

		assertEquals(shown, VoxelCamIO.selectionFor(VoxelCamIO.getScreenShotFiles(), shown));
	}

	@Test
	void withBothSelectionsGoneTheHeadOfTheListIsTaken() throws IOException {
		File newest = shot("newest.png", 2_000L);
		shot("oldest.png", 1_000L);
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");
		VoxelCamIO.selectPhoto(dir.resolve("deleted-elsewhere.png").toFile());

		assertEquals(newest,
				VoxelCamIO.selectionFor(VoxelCamIO.getScreenShotFiles(), dir.resolve("also-gone.png").toFile()));
	}

	@Test
	void anEmptyListSelectsNothing() {
		VoxelCamIO.selectPhoto(dir.resolve("filtered-out.png").toFile());

		assertNull(VoxelCamIO.selectionFor(List.of(), null));
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

	// --- burst grouping ----------------------------------------------------------------------

	@Test
	void aBurstsFramesAreFoldedIntoItsKey() throws IOException {
		shot("burst.png", 1_000L);
		shot("burst_burst01.png", 1_000L);
		shot("burst_burst02.png", 1_000L);

		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		assertEquals(List.of("burst.png"), names());
	}

	@Test
	void burstFrameCountReflectsTheFoldedFrames() throws IOException {
		File key = shot("burst.png", 1_000L);
		shot("burst_burst01.png", 1_000L);
		shot("burst_burst02.png", 1_000L);

		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		assertEquals(2, VoxelCamIO.burstFrameCount(key));
	}

	@Test
	void burstFrameCountIsZeroForAPlainShot() throws IOException {
		File plain = shot("plain.png", 1_000L);

		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		assertEquals(0, VoxelCamIO.burstFrameCount(plain));
	}

	/**
	 * The key-name lookup a sub-frame is folded through has to come from the unfiltered
	 * listing: with the needle applied first, typing something that matches a frame's name but
	 * not its key's would make the frame pop back into view as if its key were gone, when it is
	 * sitting right there on disk.
	 */
	@Test
	void aSearchThatExcludesTheKeyDoesNotUnhideItsFrames() throws IOException {
		shot("sunset.png", 1_000L);
		shot("sunset_burst01.png", 1_000L);

		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "burst");

		assertEquals(List.of(), names(),
				"the frame matches the needle but its key does not, so neither should be listed");
	}

	/** A frame whose key has been renamed or deleted out from under it is not lost — it
	 * reappears as an ordinary screenshot rather than becoming invisible and unreachable. */
	@Test
	void aFrameWhoseKeyIsGoneIsListedOnItsOwn() throws IOException {
		shot("burst_burst01.png", 1_000L);

		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");

		assertEquals(List.of("burst_burst01.png"), names());
	}

	@Test
	void deletingABurstKeyTakesItsFramesWithIt() throws IOException {
		File key = shot("burst.png", 1_000L);
		File frame1 = shot("burst_burst01.png", 1_000L);
		File frame2 = shot("burst_burst02.png", 1_000L);
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");
		VoxelCamIO.selectPhoto(key);

		assertTrue(VoxelCamIO.delete());

		assertFalse(key.exists());
		assertFalse(frame1.exists());
		assertFalse(frame2.exists());
		assertNull(VoxelCamIO.getSelectedPhoto());
	}

	/**
	 * Deleting the key after a frame failed would both manufacture an orphan and make a
	 * "deleted" report a lie about a file that is still there, so a frame that cannot be
	 * removed has to stop the whole delete rather than only itself. The stubborn frame here is
	 * the same portable stand-in {@code aRefusedDeleteKeepsTheEntryAndTheSelection} uses for a
	 * file another program holds open: a non-empty directory named like one.
	 */
	@Test
	void aRefusedFrameDeleteLeavesTheKeyAndReportsFailure() throws IOException {
		File key = shot("burst.png", 1_000L);
		File stubbornFrame = dir.resolve("burst_burst01.png").toFile();
		assertTrue(stubbornFrame.mkdir());
		Files.writeString(stubbornFrame.toPath().resolve("holding-it-open.txt"), "x");
		File okFrame = shot("burst_burst02.png", 1_000L);
		VoxelCamIO.updateScreenShotFilesList(dir.toFile(), "");
		VoxelCamIO.selectPhoto(key);

		assertFalse(VoxelCamIO.delete());

		assertTrue(key.exists(), "the key must survive a partial delete");
		assertTrue(stubbornFrame.exists());
		assertTrue(okFrame.exists());
	}

	@Test
	void renamingABurstKeyCarriesItsFramesAcross() throws IOException {
		File key = shot("burst.png", 1_000L);
		shot("burst_burst01.png", 1_000L);
		shot("burst_burst02.png", 1_000L);
		VoxelCamIO.selectPhoto(key);

		File renamed = VoxelCamIO.rename(dir.toFile(), "sunset");

		assertEquals("sunset.png", renamed.getName());
		assertTrue(dir.resolve("sunset_burst01.png").toFile().exists());
		assertTrue(dir.resolve("sunset_burst02.png").toFile().exists());
		assertFalse(dir.resolve("burst_burst01.png").toFile().exists());
		assertFalse(dir.resolve("burst_burst02.png").toFile().exists());
	}

	/**
	 * Every target name — the key's and every frame's — is collision-checked before anything
	 * moves, so a rename that would only fail on frame 2 of 8 refuses cleanly rather than
	 * carrying the first frame across and then rolling it back.
	 */
	@Test
	void aRenameThatCollidesOnAFrameNameIsRefusedWholesale() throws IOException {
		File key = shot("burst.png", 1_000L);
		File frame = shot("burst_burst01.png", 1_000L);
		// Blocks the frame's target name, even though "sunset.png" itself is free.
		shot("sunset_burst01.png", 1_000L);
		VoxelCamIO.selectPhoto(key);

		assertNull(VoxelCamIO.rename(dir.toFile(), "sunset"));

		assertTrue(key.exists(), "nothing should have moved once a frame's target was found to collide");
		assertTrue(frame.exists());
		assertFalse(dir.resolve("sunset.png").toFile().exists());
	}

	// --- Set as key ----------------------------------------------------------------------------

	@Test
	void setAsKeySwapsTheTwoFilesContents() throws IOException {
		File key = dir.resolve("burst.png").toFile();
		Files.writeString(key.toPath(), "key-bytes");
		File frame = dir.resolve("burst_burst01.png").toFile();
		Files.writeString(frame.toPath(), "frame-bytes");
		VoxelCamIO.selectPhoto(key);

		assertTrue(VoxelCamIO.setAsKey(frame));

		// The paths — and so the group's structure, and which one is folded under the other —
		// are untouched; only the bytes underneath moved.
		assertEquals("frame-bytes", Files.readString(key.toPath()));
		assertEquals("key-bytes", Files.readString(frame.toPath()));
	}

	@Test
	void setAsKeyRefusesWithNothingSelected() throws IOException {
		File frame = shot("burst_burst01.png", 1_000L);

		assertFalse(VoxelCamIO.setAsKey(frame));
	}

	@Test
	void setAsKeyRefusesToPromoteTheKeyToItself() throws IOException {
		File key = shot("burst.png", 1_000L);
		VoxelCamIO.selectPhoto(key);

		assertFalse(VoxelCamIO.setAsKey(key));
	}

	@Test
	void setAsKeyRefusesANullFrame() throws IOException {
		File key = shot("burst.png", 1_000L);
		VoxelCamIO.selectPhoto(key);

		assertFalse(VoxelCamIO.setAsKey(null));
	}

	private static List<String> names() {
		return VoxelCamIO.getScreenShotFiles().stream().map(File::getName).toList();
	}
}
