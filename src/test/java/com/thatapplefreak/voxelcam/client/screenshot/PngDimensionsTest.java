package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PngDimensionsTest {

	@TempDir
	Path dir;

	/** A real PNG header: signature, then an IHDR chunk carrying width and height. */
	private java.io.File png(String name, int width, int height) throws IOException {
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
		java.io.File file = dir.resolve(name).toFile();
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
}
