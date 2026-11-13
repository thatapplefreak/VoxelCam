package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The copy offered when an animation is too big for Discord: it has to fit, and it has to be smaller
 * only in pixels — the same frames, at the same times. */
class SmallerShareCopyTest {

	@TempDir
	Path dir;

	@Test
	void theCopyFitsUnderTheLimitAtASmallerSize() throws IOException {
		// Wide enough that a third of the size does not need a width under MIN_WIDTH.
		int w = 480;
		int h = 272;
		File apng = noisyAnimation(w, h, 6);
		long fullSize = fullWebp(apng, w, h).length;
		long limit = fullSize / 3;

		File copy = SmallerShareCopy.make(apng, fullSize, limit, SmallerShareCopyTest::argbOf);
		try {
			byte[] webp = Files.readAllBytes(copy.toPath());
			assertTrue(webp.length <= limit, webp.length + " bytes over a " + limit + " limit");
			assertEquals("RIFF", new String(webp, 0, 4, StandardCharsets.US_ASCII));
			int width = le24(webp, 24) + 1;
			int height = le24(webp, 27) + 1;
			assertTrue(width < w && width >= SmallerShareCopy.MIN_WIDTH, "width " + width);
			assertEquals(w / (double) h, width / (double) height, 0.05, "the aspect ratio holds");
			assertEquals(6, countOf(webp, "ANMF"), "every frame is kept");
			assertTrue(copy.getName().endsWith("_animation_discord.webp"), copy.getName());
		} finally {
			SmallerShareCopy.discard(copy);
		}
		assertFalse(copy.exists());
	}

	@Test
	void aLimitNothingCanMeetIsRefusedNotIgnored() throws IOException {
		File apng = noisyAnimation(200, 100, 3);
		assertThrows(IOException.class, () -> SmallerShareCopy.make(apng, 10_000_000L, 100, SmallerShareCopyTest::argbOf));
	}

	@Test
	void somethingThatIsNotAnExportedAnimationIsRefused() throws IOException {
		Path still = dir.resolve("still.png");
		Files.write(still, PngEncoder.encode(new int[] { 0xFF000000, 0xFF000000 }, 2, 1));
		assertThrows(IOException.class, () -> SmallerShareCopy.make(still.toFile(), 20_000_000L,
				SmallerShareCopy.DISCORD_LIMIT_BYTES, SmallerShareCopyTest::argbOf));
	}

	@Test
	void scalingAveragesTheAreaEachPixelCovers() {
		int[] checker = { 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000 };
		assertArrayEquals(new int[] { 0xFF808080 }, SmallerShareCopy.downscale(checker, 2, 2, 1, 1));
		int[] flat = new int[9 * 6];
		java.util.Arrays.fill(flat, 0xFF336699);
		for (int p : SmallerShareCopy.downscale(flat, 9, 6, 4, 3)) {
			assertEquals(0xFF336699, p, "a flat colour stays exactly that colour at any ratio");
		}
	}

	@Test
	void theFirstGuessScalesTheSideByTheSquareRootOfTheSizeRatio() {
		assertEquals(0.5, SmallerShareCopy.scaleFor(40, 10) / Math.sqrt(SmallerShareCopy.HEADROOM), 1e-9);
		assertEquals(1.0, SmallerShareCopy.scaleFor(5, 10), "never larger than the original");
	}

	@Test
	void onlyAFileOverTheLimitIsTooBig() throws IOException {
		Path small = dir.resolve("small.webp");
		Files.write(small, new byte[10]);
		assertFalse(SmallerShareCopy.tooBigForDiscord(small.toFile()));
		assertFalse(SmallerShareCopy.tooBigForDiscord(dir.resolve("missing.webp").toFile()));
		assertEquals("12.3", SmallerShareCopy.megabytes(12_340_000L));
	}

	private File noisyAnimation(int w, int h, int frames) throws IOException {
		Random random = new Random(11);
		List<int[]> pixels = new ArrayList<>();
		int[] delays = new int[frames];
		for (int f = 0; f < frames; f++) {
			int[] frame = new int[w * h];
			for (int i = 0; i < frame.length; i++) {
				frame[i] = 0xFF000000 | random.nextInt(0x1000000);
			}
			pixels.add(frame);
			delays[f] = 100;
		}
		Path file = dir.resolve("shot_animation.png");
		Files.write(file, AnimatedPng.assemble(pixels, w, h, delays, Map.of()));
		return file.toFile();
	}

	private static byte[] fullWebp(File apng, int w, int h) throws IOException {
		int[] canvas = new int[w * h];
		AnimatedWebp webp = new AnimatedWebp(w, h);
		for (AnimatedPng.Frame frame : AnimatedPng.splitFrames(Files.readAllBytes(apng.toPath()))) {
			new FramePatch(frame.x(), frame.y(), frame.width(), frame.height(), argbOf(frame.png()))
					.composite(canvas, w, frame.blend());
			webp.frame(canvas.clone(), frame.delayMillis());
		}
		return webp.finish();
	}

	private static int[] argbOf(byte[] png) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	private static int le24(byte[] d, int at) {
		return (d[at] & 0xFF) | ((d[at + 1] & 0xFF) << 8) | ((d[at + 2] & 0xFF) << 16);
	}

	private static int countOf(byte[] data, String fourCc) {
		int count = 0;
		for (int pos = 12; pos + 8 <= data.length; ) {
			int size = (data[pos + 4] & 0xFF) | ((data[pos + 5] & 0xFF) << 8) | ((data[pos + 6] & 0xFF) << 16)
					| ((data[pos + 7] & 0xFF) << 24);
			if (new String(data, pos, 4, StandardCharsets.US_ASCII).equals(fourCc)) {
				count++;
			}
			pos += 8 + ((size + 1) & ~1);
		}
		return count;
	}
}
