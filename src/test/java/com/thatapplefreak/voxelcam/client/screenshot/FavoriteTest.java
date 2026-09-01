package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FavoriteTest {

	@TempDir
	Path dir;

	/** A minimal but real PNG: signature, IHDR, an empty IDAT, IEND. Good enough to splice into. */
	private File png(String name) throws IOException {
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
	void aFreshScreenshotIsNotStarred() throws IOException {
		assertFalse(Favorite.isStarred(png("shot.png")));
	}

	@Test
	void aMissingFileIsNotStarred() {
		assertFalse(Favorite.isStarred(dir.resolve("absent.png").toFile()));
	}

	@Test
	void settingStarredMakesItReadAsStarred() throws IOException {
		File file = png("shot.png");

		Favorite.setStarred(file, true);

		assertTrue(Favorite.isStarred(file));
	}

	@Test
	void togglingBackOffIsReflectedImmediately() throws IOException {
		File file = png("shot.png");

		Favorite.setStarred(file, true);
		Favorite.setStarred(file, false);

		assertFalse(Favorite.isStarred(file));
	}

	/** Repeated toggling must not shadow the latest value behind an earlier chunk. */
	@Test
	void survivesRepeatedToggling() throws IOException {
		File file = png("shot.png");

		for (int i = 0; i < 5; i++) {
			Favorite.setStarred(file, true);
			assertTrue(Favorite.isStarred(file));
			Favorite.setStarred(file, false);
			assertFalse(Favorite.isStarred(file));
		}
	}

	/** Otherwise starring a shot would jump it to the top under the newest-first sort. */
	@Test
	void settingStarredPreservesTheOriginalModificationTime() throws IOException {
		File file = png("shot.png");
		long original = 1_000_000L;
		assertTrue(file.setLastModified(original));

		Favorite.setStarred(file, true);

		assertEquals(original, file.lastModified());
	}
}
