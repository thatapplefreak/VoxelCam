package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Burst frames are written by this encoder rather than Minecraft's, so every one of them has to
 * open in a decoder that is not ours, pixel for pixel. The JDK's PNG reader is that decoder here.
 */
class PngEncoderTest {

	@Test
	void anOpaqueImageRoundTripsAsRgb() throws IOException {
		int[] pixels = scene(97, 61, new Random(1));
		byte[] png = PngEncoder.encode(pixels, 97, 61);

		assertEquals(2, png[8 + 8 + 9], "colour type 2: RGB, no alpha channel for an opaque frame");
		assertArrayEquals(pixels, decode(png));
	}

	@Test
	void transparencyIsKeptAsRgba() throws IOException {
		int[] pixels = scene(16, 9, new Random(2));
		pixels[5] = 0x80123456;
		byte[] png = PngEncoder.encode(pixels, 16, 9);

		assertEquals(6, png[8 + 8 + 9], "colour type 6: RGBA");
		assertArrayEquals(pixels, decode(png));
	}

	@Test
	void aLargeNoisyImageSpillsAcrossSeveralIdatChunksAndStillDecodes() throws IOException {
		Random random = new Random(3);
		int[] pixels = new int[400 * 400];
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = 0xFF000000 | random.nextInt(0x1000000);
		}
		byte[] png = PngEncoder.encode(pixels, 400, 400);

		assertTrue(countOf(png, "IDAT") > 1, "IDATs are capped in size rather than one giant chunk");
		assertArrayEquals(pixels, decode(png));
	}

	@Test
	void tinyImagesDecode() throws IOException {
		assertArrayEquals(new int[] { 0xFF102030 }, decode(PngEncoder.encode(new int[] { 0xFF102030 }, 1, 1)));
		int[] two = { 0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF0000FF };
		assertArrayEquals(two, decode(PngEncoder.encode(two, 1, 4)));
	}

	@Test
	void eachRowGetsTheFilterThatLeavesTheLeast() {
		byte[] above = { 10, 20, 30, 40, 50, 60 };
		byte[] same = above.clone();
		byte[][] candidates = new byte[5][7];
		assertEquals(2, PngEncoder.filterRow(same, above, 3, candidates)[0], "a row equal to the one above: Up");

		byte[] flat = { 7, 7, 7, 7, 7, 7 };
		assertEquals(1, PngEncoder.filterRow(flat, new byte[6], 3, candidates)[0], "a flat row under nothing: Sub");
	}

	private static int[] decode(byte[] png) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	private static int countOf(byte[] data, String type) {
		byte[] needle = type.getBytes(StandardCharsets.US_ASCII);
		int count = 0;
		outer:
		for (int i = 0; i + 4 <= data.length; i++) {
			for (int k = 0; k < 4; k++) {
				if (data[i + k] != needle[k]) {
					continue outer;
				}
			}
			count++;
		}
		return count;
	}

	private static int[] scene(int w, int h, Random random) {
		int[] pixels = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = y < h / 2 ? 0x87CEEB - y : ((x / 8 + y / 8) % 2 == 0 ? 0x228B22 : 0x1E7B1E);
				if (random.nextInt(30) == 0) {
					rgb = random.nextInt(0x1000000);
				}
				pixels[y * w + x] = 0xFF000000 | rgb;
			}
		}
		return pixels;
	}
}
