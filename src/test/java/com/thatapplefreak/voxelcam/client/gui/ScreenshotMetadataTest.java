package com.thatapplefreak.voxelcam.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatapplefreak.voxelcam.client.screenshot.CaptureContext;
import com.thatapplefreak.voxelcam.client.screenshot.PngTextChunk;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.zip.CRC32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Dimensions are read straight out of the PNG header rather than by decoding the
 * image, so the byte offsets are load-bearing and worth pinning. The rest of this
 * covers what the list rows and the preview caption actually say.
 */
class ScreenshotMetadataTest {

	@TempDir
	Path dir;

	@BeforeEach
	void reset() {
		// Dimensions are cached per File for the life of the process.
		ScreenshotMetadata.forgetAll();
	}

	/** A real PNG header: signature, then an IHDR chunk carrying width and height. */
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

	private File sized(String name, long bytes) throws IOException {
		File file = dir.resolve(name).toFile();
		Files.write(file.toPath(), new byte[(int) bytes]);
		return file;
	}

	@Test
	void readsDimensionsFromThePngHeader() throws IOException {
		assertEquals(new ScreenshotMetadata.Dimensions(1920, 1080),
				ScreenshotMetadata.dimensions(png("shot.png", 1920, 1080)));
	}

	@Test
	void handlesNonSquareAndTinyImages() throws IOException {
		assertEquals(new ScreenshotMetadata.Dimensions(480, 120),
				ScreenshotMetadata.dimensions(png("wide.png", 480, 120)));
		assertEquals(new ScreenshotMetadata.Dimensions(1, 1),
				ScreenshotMetadata.dimensions(png("dot.png", 1, 1)));
	}

	/** A truncated or non-PNG file must read as unknown, not blow up a list row. */
	@Test
	void unreadableFilesGiveNullRatherThanThrowing() throws IOException {
		Files.writeString(dir.resolve("bogus.png"), "not a png");

		assertNull(ScreenshotMetadata.dimensions(dir.resolve("bogus.png").toFile()));
		assertNull(ScreenshotMetadata.dimensions(dir.resolve("absent.png").toFile()));
		assertNull(ScreenshotMetadata.dimensions(null));
	}

	@Test
	void fileSizeSwitchesUnitsAtTheThresholds() throws IOException {
		assertEquals("512 B", ScreenshotMetadata.fileSize(sized("a.png", 512)));
		assertEquals("1023 B", ScreenshotMetadata.fileSize(sized("b.png", 1023)));
		assertEquals("1 KB", ScreenshotMetadata.fileSize(sized("c.png", 1024)));
		assertEquals("100 KB", ScreenshotMetadata.fileSize(sized("d.png", 1024 * 100)));
		assertEquals("1.0 MB", ScreenshotMetadata.fileSize(sized("e.png", 1024 * 1024)));
	}

	/**
	 * A vanilla capture name is a timestamp, which is noise in a narrow row, so it is
	 * shown as a friendly time instead. Anything the user renamed is theirs and is
	 * shown verbatim.
	 */
	@Test
	void captureNamesBecomeFriendlyTimesButRenamesAreKept() throws IOException {
		File capture = sized("2026-08-27_10.00.00.png", 1);
		File burst = sized("2026-08-27_10.00.00_3.png", 1);
		File renamed = sized("sunset over base.png", 1);

		assertTrue(ScreenshotMetadata.displayName(capture).matches("(Today|Yesterday) \\d{2}:\\d{2}|\\d+ \\w+, \\d{2}:\\d{2}"),
				ScreenshotMetadata.displayName(capture));
		assertTrue(ScreenshotMetadata.displayName(burst).matches("(Today|Yesterday) \\d{2}:\\d{2}|\\d+ \\w+, \\d{2}:\\d{2}"),
				ScreenshotMetadata.displayName(burst));
		assertEquals("sunset over base", ScreenshotMetadata.displayName(renamed));
	}

	@Test
	void displayNameDropsTheExtensionCaseInsensitively() throws IOException {
		assertEquals("holiday", ScreenshotMetadata.displayName(sized("holiday.PNG", 1)));
	}

	@Test
	void relativeTimeLabelsTodayAndYesterday() throws IOException {
		File file = sized("x.png", 1);

		assertTrue(ScreenshotMetadata.relativeTime(file).startsWith("Today "),
				ScreenshotMetadata.relativeTime(file));

		long yesterday = LocalDateTime.now().minusDays(1)
				.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		assertTrue(file.setLastModified(yesterday));
		assertTrue(ScreenshotMetadata.relativeTime(file).startsWith("Yesterday "),
				ScreenshotMetadata.relativeTime(file));
	}

	@Test
	void olderFilesFallBackToADatedLabel() throws IOException {
		File file = sized("x.png", 1);
		long lastMonth = LocalDateTime.now().minusDays(40)
				.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		assertTrue(file.setLastModified(lastMonth));

		String label = ScreenshotMetadata.relativeTime(file);
		assertTrue(label.matches("\\d+ \\w+, \\d{2}:\\d{2}"), label);
	}

	@Test
	void readsCaptureContextEmbeddedByPngTextChunk() throws IOException {
		File file = png("shot.png", 640, 480);
		CaptureContext embedded = new CaptureContext("minecraft:the_nether", 12, 70, -45, "New World");
		PngTextChunk.embed(file, embedded.toTags());

		assertEquals(embedded, ScreenshotMetadata.captureContext(file));
	}

	/** Every screenshot taken before this feature shipped has no tags at all. */
	@Test
	void captureContextIsNullWhenThereAreNoTags() throws IOException {
		assertNull(ScreenshotMetadata.captureContext(png("untagged.png", 100, 100)));
		assertNull(ScreenshotMetadata.captureContext(null));
	}

	@Test
	void captureContextSurvivesACorruptOrUnrelatedTag() throws IOException {
		File file = png("partial.png", 100, 100);
		PngTextChunk.embed(file, Map.of("voxelcam:dimension", "minecraft:overworld", "some:other:tag", "x"));

		assertNull(ScreenshotMetadata.captureContext(file));
	}

	/** Nothing here should depend on the platform default charset. */
	@Test
	void nonAsciiNamesSurvive() throws IOException {
		String name = "café–shot";
		File file = sized(name + ".png", 1);

		assertEquals(name, ScreenshotMetadata.displayName(file));
		assertEquals(name, new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
	}
}
