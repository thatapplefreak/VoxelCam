package com.thatapplefreak.voxelcam.gametest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * What a test that opens the manager on a folder of its own needs before it starts: the
 * folder, empty, and real PNGs to put in it. {@code RenameSelectionTest} and {@code
 * ScreenshotManagerTest} each carry their own copy of the same two steps from before this
 * existed.
 */
final class ScreenshotFixtures {

	private ScreenshotFixtures() {
	}

	/**
	 * Creates {@code dir}, or clears out what an earlier run left in it. The run directory
	 * survives between local runs, and a test that renames or exports leaves files a second
	 * run would otherwise list.
	 */
	static void emptyFolder(File dir) throws IOException {
		if (dir.isDirectory()) {
			File[] stale = dir.listFiles();
			if (stale != null) {
				for (File file : stale) {
					Files.deleteIfExists(file.toPath());
				}
			}
		}
		Files.createDirectories(dir.toPath());
	}

	/** A real, decodable PNG — the manager uploads these as textures. */
	static void writePng(File file, int width, int height) throws IOException {
		java.awt.image.BufferedImage image =
				new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, (x * 255 / width) << 16 | (y * 255 / height) << 8 | 0x80);
			}
		}
		javax.imageio.ImageIO.write(image, "png", file);
	}
}
