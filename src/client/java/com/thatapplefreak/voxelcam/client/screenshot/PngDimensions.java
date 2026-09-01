package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Width/height straight out of the PNG IHDR chunk, without decoding the image. */
public final class PngDimensions {

	public record Dimensions(int width, int height) {
	}

	private PngDimensions() {
	}

	public static Dimensions read(File file) {
		if (file == null) {
			return null;
		}
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			// 8-byte PNG signature, 4-byte chunk length, 4-byte "IHDR", then width/height.
			in.skipNBytes(16);
			return new Dimensions(in.readInt(), in.readInt());
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
