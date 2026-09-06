package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rest of the save path needs a live client, but the failure cleanup does not: a real
 * NativeImage encode creates the file before it can fail, and the seam reproduces exactly that
 * ordering — create, then throw. The in-flight counter needs no client either — it is a plain
 * {@code AtomicInteger} exercised directly through {@code beginSave}/{@code endSave}/{@code
 * joinSave}, the seams a burst, a plain capture and the oversized path all share.
 */
class ScreenshotHandlerTest {

	@TempDir
	Path dir;

	/** The counter is static, so a test that leaves it non-zero would follow the next one in. */
	@AfterEach
	void settle() {
		ScreenshotHandler.forgetSavesInFlight();
	}

	/** What a disk-full encode leaves behind: a created, empty file and an IOException. */
	private static ScreenshotHandler.PngWriter failsAfterCreating() {
		return target -> {
			Files.write(target.toPath(), new byte[0]);
			throw new IOException("stb encode failed");
		};
	}

	@Test
	void aFailedWriteLeavesNoFileBehind() {
		File target = new File(dir.toFile(), "capture.png");

		assertThrows(IOException.class, () -> ScreenshotHandler.writeOrDiscard(target, failsAfterCreating()));

		assertFalse(target.exists(), "a failed save left a broken .png in the screenshots folder");
	}

	@Test
	void theWriteFailureItselfStillPropagates() {
		File target = new File(dir.toFile(), "capture.png");
		IOException failure = new IOException("stb encode failed");

		IOException thrown = assertThrows(IOException.class, () -> ScreenshotHandler.writeOrDiscard(target, t -> {
			throw failure;
		}));

		assertSame(failure, thrown);
	}

	/** Only what this write created is undone; a file that was already at the path is not ours to remove. */
	@Test
	void aFileThatWasNotCreatedByThisWriteIsNotDeleted() throws IOException {
		File target = new File(dir.toFile(), "capture.png");
		Files.write(target.toPath(), "not ours".getBytes(StandardCharsets.UTF_8));

		assertThrows(IOException.class, () -> ScreenshotHandler.writeOrDiscard(target, failsAfterCreating()));

		assertTrue(target.exists());
	}

	@Test
	void aSuccessfulWriteKeepsItsFile() throws IOException {
		File target = new File(dir.toFile(), "capture.png");
		byte[] contents = "png".getBytes(StandardCharsets.UTF_8);

		ScreenshotHandler.writeOrDiscard(target, t -> Files.write(t.toPath(), contents));

		assertArrayEquals(contents, Files.readAllBytes(target.toPath()));
	}

	@Test
	void nBeginsAndNEndsReturnToIdle() {
		assertTrue(ScreenshotHandler.beginSave(3));
		assertTrue(ScreenshotHandler.beginSave(3));
		assertTrue(ScreenshotHandler.beginSave(3));

		ScreenshotHandler.endSave();
		ScreenshotHandler.endSave();
		ScreenshotHandler.endSave();

		assertFalse(ScreenshotHandler.isSaving(), "three begins balanced by three ends must return to idle");
	}

	@Test
	void theCounterRefusesPastItsCap() {
		assertTrue(ScreenshotHandler.beginSave(3));
		assertTrue(ScreenshotHandler.beginSave(3));
		assertTrue(ScreenshotHandler.beginSave(3));

		assertFalse(ScreenshotHandler.beginSave(3), "a fourth slot must be refused once three are already claimed");
	}

	/** An unpaired end (a bug elsewhere, not a scenario this class should ever produce on its
	 * own) must not leave the counter refusing every future capture for the rest of the session. */
	@Test
	void theCounterNeverGoesNegative() {
		ScreenshotHandler.endSave();

		assertTrue(ScreenshotHandler.beginSave(1),
				"an unpaired end must not leave the counter stuck refusing the next begin");
	}

	/** The plain path's own refusal semantics, unchanged by the counter rework: a second
	 * concurrent plain capture is still refused. */
	@Test
	void aPlainCaptureStillRefusesASecondOne() {
		assertTrue(ScreenshotHandler.beginSave(1));

		assertFalse(ScreenshotHandler.beginSave(1), "a plain capture must refuse while another is already in flight");
	}

	/** The oversized path is gated by {@code BigScreenshot}'s own state, not by this cap — it
	 * joins unconditionally. */
	@Test
	void joinSaveIgnoresTheCap() {
		ScreenshotHandler.joinSave();
		ScreenshotHandler.joinSave();
		ScreenshotHandler.joinSave();

		assertEquals(3, ScreenshotHandler.savesInFlight());
	}

	@Test
	void isSavingFollowsTheCounter() {
		assertFalse(ScreenshotHandler.isSaving());

		ScreenshotHandler.beginSave(1);
		assertTrue(ScreenshotHandler.isSaving());

		ScreenshotHandler.endSave();
		assertFalse(ScreenshotHandler.isSaving());
	}
}
