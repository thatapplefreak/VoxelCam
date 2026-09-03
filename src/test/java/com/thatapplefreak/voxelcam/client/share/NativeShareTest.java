package com.thatapplefreak.voxelcam.client.share;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import com.thatapplefreak.voxelcam.client.share.NativeShare.RevealOutcome;
import net.minecraft.util.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The parts of the share targets that are reachable without a window.
 *
 * The native Save-As dialog and GLFW's clipboard both need a live client, so what
 * is tested here is everything on either side of them: which path the dialog's
 * answer turns into, that the copy really happens, and which command each platform
 * gets for revealing a file.
 */
class NativeShareTest {

	@TempDir
	Path dir;

	private File screenshot(String name) throws IOException {
		File file = dir.resolve(name).toFile();
		Files.writeString(file.toPath(), "pretend png");
		return file;
	}

	// --- the extension the dialog does not always add -------------------------

	@Test
	void missingExtensionIsAdded() {
		assertEquals(Path.of("/tmp/holiday.png"), NativeShare.targetPath("/tmp/holiday"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/tmp/holiday.png", "/tmp/holiday.PNG", "/tmp/holiday.PnG" })
	void anExistingExtensionIsNotDoubledWhateverItsCase(String chosen) {
		assertEquals(Path.of(chosen), NativeShare.targetPath(chosen));
	}

	/** A dot in the name is not an extension — "base v1.2" must still gain .png. */
	@Test
	void dotsElsewhereInTheNameDoNotCountAsAnExtension() {
		assertEquals(Path.of("/tmp/base v1.2.png"), NativeShare.targetPath("/tmp/base v1.2"));
		assertEquals(Path.of("/tmp/shot.jpg.png"), NativeShare.targetPath("/tmp/shot.jpg"));
	}

	// --- the copy the dialog leads to ----------------------------------------

	@Test
	void copyWritesTheFileWhereTheUserPointed() throws IOException {
		File source = screenshot("2026-08-27_10.00.00.png");
		Path chosen = dir.resolve("elsewhere/holiday.png");
		Files.createDirectories(chosen.getParent());

		Path written = NativeShare.copyTo(source, chosen.toString());

		assertEquals(chosen, written);
		assertEquals("pretend png", Files.readString(written));
		// A copy, not a move: the original stays in the screenshots folder.
		assertTrue(source.exists());
	}

	@Test
	void copyAppliesTheExtensionToo() throws IOException {
		File source = screenshot("shot.png");

		Path written = NativeShare.copyTo(source, dir.resolve("no-extension").toString());

		assertEquals(dir.resolve("no-extension.png"), written);
		assertTrue(Files.exists(written));
	}

	/**
	 * The dialog already asked the user about overwriting, so by the time the path
	 * gets here replacing is the expected outcome rather than a failure.
	 */
	@Test
	void copyReplacesAnExistingFile() throws IOException {
		File source = screenshot("shot.png");
		Path target = dir.resolve("target.png");
		Files.writeString(target, "old contents");

		NativeShare.copyTo(source, target.toString());

		assertEquals("pretend png", Files.readString(target));
	}

	@Test
	void copyToAnUnwritableLocationReportsTheFailure() throws IOException {
		File source = screenshot("shot.png");
		String chosen = dir.resolve("does/not/exist/holiday.png").toString();

		assertThrows(IOException.class, () -> NativeShare.copyTo(source, chosen));
	}

	@Test
	void copyOfAMissingScreenshotReportsTheFailure() {
		File missing = dir.resolve("absent.png").toFile();

		assertThrows(IOException.class,
				() -> NativeShare.copyTo(missing, dir.resolve("out.png").toString()));
	}

	@Test
	void nonAsciiNamesRoundTrip() throws IOException {
		File source = screenshot("café.png");

		Path written = NativeShare.copyTo(source, dir.resolve("sünset").toString());

		assertEquals(dir.resolve("sünset.png"), written);
		assertEquals("pretend png", Files.readString(written, StandardCharsets.UTF_8));
	}

	// --- revealing in the file manager ---------------------------------------

	@Test
	void macOsRevealsTheItemItself() throws IOException {
		File file = screenshot("shot.png");

		assertArrayEquals(new String[] { "open", "-R", file.getAbsolutePath() },
				NativeShare.revealCommand(Util.OS.OSX, file));
	}

	/** explorer.exe wants /select and the path joined into one argument. */
	@Test
	void windowsSelectsTheItemInOneArgument() throws IOException {
		File file = screenshot("shot.png");

		assertArrayEquals(new String[] { "explorer.exe", "/select," + file.getAbsolutePath() },
				NativeShare.revealCommand(Util.OS.WINDOWS, file));
	}

	/**
	 * Only macOS and Windows document a "reveal this item" command. Everywhere else
	 * returns null, which is the signal to open the containing folder instead —
	 * returning a wrong command would silently open nothing.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "LINUX", "SOLARIS", "UNKNOWN" })
	void otherPlatformsHaveNoRevealCommand(String platform) throws IOException {
		File file = screenshot("shot.png");

		assertNull(NativeShare.revealCommand(Util.OS.valueOf(platform), file));
	}

	/** Every platform must be handled, so the switch cannot fall through unexpectedly. */
	@Test
	void everyPlatformIsAccountedFor() throws IOException {
		File file = screenshot("shot.png");

		for (Util.OS platform : Util.OS.values()) {
			String[] command = NativeShare.revealCommand(platform, file);
			if (command != null && command.length == 0) {
				throw new AssertionError(platform + " produced an empty command");
			}
		}
	}

	// --- what the reveal command's exit status is taken to mean ---------------

	/**
	 * The bug this guards: a reveal that launches and then fails — "open -R" on a
	 * file a cloud-sync client removed between opening the popup and clicking —
	 * exits non-zero instead of throwing. Ignore the exit status and the fallback
	 * never runs while the popup still reports success, so no window ever appears.
	 */
	@Test
	@DisabledOnOs(OS.WINDOWS)
	void aRevealThatLaunchesAndFailsFallsBackToTheFolder() {
		AtomicInteger folderOpened = new AtomicInteger();

		RevealOutcome outcome = NativeShare.reveal(
				new String[] { "sh", "-c", "exit 1" }, dir.toFile(), folderOpened::incrementAndGet);

		assertEquals(RevealOutcome.OPENED_FOLDER, outcome);
		assertEquals(1, folderOpened.get());
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void aRevealThatSucceedsLeavesTheFolderAlone() {
		AtomicInteger folderOpened = new AtomicInteger();

		RevealOutcome outcome = NativeShare.reveal(
				new String[] { "sh", "-c", "exit 0" }, dir.toFile(), folderOpened::incrementAndGet);

		assertEquals(RevealOutcome.REVEALED, outcome);
		assertEquals(0, folderOpened.get());
	}

	/** A command that cannot be launched at all is the older IOException path. */
	@Test
	void aRevealCommandThatIsNotInstalledFallsBackToTheFolder() {
		AtomicInteger folderOpened = new AtomicInteger();

		RevealOutcome outcome = NativeShare.reveal(
				new String[] { "voxelcam-no-such-program" }, dir.toFile(), folderOpened::incrementAndGet);

		assertEquals(RevealOutcome.OPENED_FOLDER, outcome);
		assertEquals(1, folderOpened.get());
	}

	/** Linux and friends have no reveal command, so the folder is the intended path there. */
	@Test
	void noRevealCommandOpensTheFolderDirectly() {
		AtomicInteger folderOpened = new AtomicInteger();

		RevealOutcome outcome = NativeShare.reveal(null, dir.toFile(), folderOpened::incrementAndGet);

		assertEquals(RevealOutcome.OPENED_FOLDER, outcome);
		assertEquals(1, folderOpened.get());
	}

	/** With the folder gone too there is nothing left to open, so say so rather than claim success. */
	@Test
	void aMissingFolderIsReportedRatherThanOpened() {
		AtomicInteger folderOpened = new AtomicInteger();

		RevealOutcome outcome = NativeShare.reveal(
				null, dir.resolve("deleted").toFile(), folderOpened::incrementAndGet);

		assertEquals(RevealOutcome.FAILED, outcome);
		assertEquals(0, folderOpened.get());
	}

	@Test
	void openReportsItsFailureThroughTheExitCode() {
		assertTrue(NativeShare.revealed(new String[] { "open", "-R", "/tmp/shot.png" }, 0));
		assertFalse(NativeShare.revealed(new String[] { "open", "-R", "/tmp/shot.png" }, 1));
	}

	/**
	 * explorer.exe exits 1 whether or not it opened the window, so trusting its
	 * status would stack the containing folder on top of every Windows reveal.
	 */
	@Test
	void explorersExitStatusIsIgnored() {
		assertTrue(NativeShare.revealed(new String[] { "explorer.exe", "/select,C:\\shot.png" }, 1));
	}
}
