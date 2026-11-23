package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** Width/height straight out of the PNG IHDR chunk, without decoding the image. */
public final class PngDimensions {

	public record Dimensions(int width, int height) {
	}

	private PngDimensions() {
	}

	/**
	 * @return null for anything that is not a PNG with a whole {@code IHDR} first and both
	 *         sides positive. The manager lists every file named {@code .png}, and a JPEG renamed
	 *         to one has bytes at the IHDR offsets too: read on trust they came out as
	 *         65536×-2424765 in its row and sorted below every empty file.
	 */
	public static Dimensions read(File file) {
		if (file == null) {
			return null;
		}
		try (InputStream in = new FileInputStream(file)) {
			byte[] header = in.readNBytes(PngTextChunk.HEADER_LENGTH);
			if (!PngTextChunk.startsWithIhdr(header)) {
				return null;
			}
			// 8-byte PNG signature, 4-byte chunk length, 4-byte "IHDR", then width/height.
			ByteBuffer ihdr = ByteBuffer.wrap(header);
			int width = ihdr.getInt(16);
			int height = ihdr.getInt(20);
			return width > 0 && height > 0 ? new Dimensions(width, height) : null;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
