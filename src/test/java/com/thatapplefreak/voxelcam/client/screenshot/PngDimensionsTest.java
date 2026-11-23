package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PngDimensionsTest {

	@TempDir
	Path dir;

	/** A PNG header: signature, then a 13-byte chunk of {@code type} carrying width and height. */
	private static byte[] header(String type, int width, int height) {
		byte[] ihdr = new byte[13];
		writeInt(ihdr, 0, width);
		writeInt(ihdr, 4, height);
		ihdr[8] = 8;
		ihdr[9] = 2;

		byte[] chunk = new byte[8 + ihdr.length + 4];
		writeInt(chunk, 0, ihdr.length);
		for (int i = 0; i < 4; i++) {
			chunk[4 + i] = (byte) type.charAt(i);
		}
		System.arraycopy(ihdr, 0, chunk, 8, ihdr.length);
		CRC32 crc = new CRC32();
		crc.update(chunk, 4, 4 + ihdr.length);
		writeInt(chunk, 8 + ihdr.length, (int) crc.getValue());

		byte[] signature = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
		byte[] png = Arrays.copyOf(signature, signature.length + chunk.length);
		System.arraycopy(chunk, 0, png, signature.length, chunk.length);
		return png;
	}

	private File write(String name, byte[] bytes) throws IOException {
		File file = dir.resolve(name).toFile();
		Files.write(file.toPath(), bytes);
		return file;
	}

	private File png(String name, int width, int height) throws IOException {
		return write(name, header("IHDR", width, height));
	}

	private static void writeInt(byte[] target, int at, int value) {
		target[at] = (byte) (value >>> 24);
		target[at + 1] = (byte) (value >>> 16);
		target[at + 2] = (byte) (value >>> 8);
		target[at + 3] = (byte) value;
	}

	@Test
	void readsWidthAndHeightFromTheIhdrChunk() throws IOException {
		assertEquals(new PngDimensions.Dimensions(1920, 1080), PngDimensions.read(png("shot.png", 1920, 1080)));
	}

	@Test
	void unreadableFilesGiveNullRatherThanThrowing() throws IOException {
		Files.writeString(dir.resolve("bogus.png"), "not a png");

		assertNull(PngDimensions.read(dir.resolve("bogus.png").toFile()));
		assertNull(PngDimensions.read(dir.resolve("absent.png").toFile()));
		assertNull(PngDimensions.read(null));
	}

	/**
	 * The playtest's case: a JPEG renamed to {@code .png}. Its bytes 16–23 are two ints like any
	 * others, and read as an offset into the header they came out as 65536×-2424765.
	 */
	@Test
	void aJpegNamedPngIsUnreadable() throws IOException {
		File jpeg = dir.resolve("fake jpeg.png").toFile();
		ImageIO.write(new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);

		assertNull(PngDimensions.read(jpeg));
	}

	@Test
	void aFileCutShortInsideTheHeaderIsUnreadable() throws IOException {
		byte[] whole = header("IHDR", 640, 360);

		// Width and height are both present here; only the rest of IHDR is missing.
		assertNull(PngDimensions.read(write("cut.png", Arrays.copyOf(whole, 24))));
		assertNull(PngDimensions.read(write("cut-more.png", Arrays.copyOf(whole, whole.length - 1))));
	}

	@Test
	void aSignatureFollowedByAnotherChunkIsUnreadable() throws IOException {
		assertNull(PngDimensions.read(write("idat-first.png", header("IDAT", 640, 360))));
	}

	@Test
	void zeroOrNegativeDimensionsAreUnreadable() throws IOException {
		assertNull(PngDimensions.read(png("zero-wide.png", 0, 360)));
		assertNull(PngDimensions.read(png("zero-high.png", 640, 0)));
		assertNull(PngDimensions.read(png("negative.png", 65536, -2424765)));
	}
}
