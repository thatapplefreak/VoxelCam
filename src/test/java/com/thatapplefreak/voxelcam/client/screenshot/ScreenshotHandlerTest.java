package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rest of the save path needs a live client, but the failure cleanup does not: a real
 * NativeImage encode creates the file before it can fail, and the seam reproduces exactly that
 * ordering — create, then throw.
 */
class ScreenshotHandlerTest {

	@TempDir
	Path dir;

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
}
