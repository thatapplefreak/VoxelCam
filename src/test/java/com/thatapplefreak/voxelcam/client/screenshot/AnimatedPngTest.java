package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the APNG byte layout. Each of the first groups is a known way an APNG goes wrong in the
 * field — a browser shows the first frame and nothing else, or nothing at all — so they assert
 * the bytes directly rather than through a decoder that might forgive them. The round trip then
 * proves the composited frames are the source frames, through the JDK's own PNG decoder.
 */
class AnimatedPngTest {

	@TempDir
	Path dir;

	// --- fixtures -------------------------------------------------------------------------

	/** A real PNG from the JDK's encoder: a solid colour plus one marker pixel, so frames differ. */
	private static byte[] png(int width, int height, int rgb, int markerX) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, rgb);
			}
		}
		image.setRGB(markerX, 0, 0xFFFFFF);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	/** A solid frame with one marker pixel, as ARGB. */
	private static int[] frame(int width, int height, int rgb, int markerX) {
		int[] pixels = new int[width * height];
		Arrays.fill(pixels, 0xFF000000 | rgb);
		pixels[markerX] = 0xFFFFFFFF;
		return pixels;
	}

	private static int[] noise(int n, Random random) {
		int[] pixels = new int[n];
		for (int i = 0; i < n; i++) {
			pixels[i] = 0xFF000000 | random.nextInt(0x1000000);
		}
		return pixels;
	}

	private record Chunk(String type, byte[] data, boolean crcOk) {
	}

	private static List<Chunk> chunks(byte[] png) {
		List<Chunk> chunks = new ArrayList<>();
		int pos = 8;
		while (pos + 12 <= png.length) {
			int length = readInt(png, pos);
			String type = new String(png, pos + 4, 4, StandardCharsets.US_ASCII);
			byte[] data = Arrays.copyOfRange(png, pos + 8, pos + 8 + length);
			CRC32 crc = new CRC32();
			crc.update(png, pos + 4, 4 + length);
			chunks.add(new Chunk(type, data, (int) crc.getValue() == readInt(png, pos + 8 + length)));
			pos += 12 + length;
		}
		return chunks;
	}

	private static byte[] chunk(String type, byte[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		out.writeBytes(intBytes(data.length));
		out.writeBytes(typeBytes);
		out.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		out.writeBytes(intBytes((int) crc.getValue()));
		return out.toByteArray();
	}

	private static byte[] intBytes(int v) {
		return new byte[] { (byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v };
	}

	private static int readInt(byte[] d, int at) {
		return ((d[at] & 0xFF) << 24) | ((d[at + 1] & 0xFF) << 16) | ((d[at + 2] & 0xFF) << 8) | (d[at + 3] & 0xFF);
	}

	private static int readShort(byte[] d, int at) {
		return ((d[at] & 0xFF) << 8) | (d[at + 1] & 0xFF);
	}

	private static byte[] assemble(int width, int height, List<int[]> frames, int... delays) throws IOException {
		return AnimatedPng.assemble(frames, width, height, delays, Map.of());
	}

	/** Plays the file the way a browser does: every frame's patch onto one canvas, snapshotted. */
	private static List<int[]> play(byte[] apng, int width, int height) throws IOException {
		List<int[]> shown = new ArrayList<>();
		int[] canvas = new int[width * height];
		for (AnimatedPng.Frame frame : AnimatedPng.splitFrames(apng)) {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame.png()));
			int[] patch = image.getRGB(0, 0, frame.width(), frame.height(), null, 0, frame.width());
			new FramePatch(frame.x(), frame.y(), frame.width(), frame.height(), patch)
					.composite(canvas, width, frame.blend());
			shown.add(canvas.clone());
		}
		return shown;
	}

	// --- 1. sequence numbering --------------------------------------------------------------

	@Test
	void fcTLAndFdATShareOneCounterAndFrameZeroUsesPlainIdat() throws IOException {
		Random random = new Random(1);
		// Noise compresses to nothing, so a 400x400 frame spills over several data chunks — and
		// each fdAT chunk must cost a sequence number of its own.
		List<int[]> frames = List.of(noise(400 * 400, random), noise(400 * 400, random), frame(400, 400, 0, 3));
		List<Chunk> out = chunks(assemble(400, 400, frames, 50, 50, 50));

		List<String> types = new ArrayList<>();
		List<Integer> numbers = new ArrayList<>();
		for (Chunk ch : out) {
			if (ch.type.equals("fcTL") || ch.type.equals("fdAT")) {
				types.add(ch.type);
				numbers.add(readInt(ch.data, 0));
			}
		}
		for (int i = 0; i < numbers.size(); i++) {
			assertEquals(i, numbers.get(i), "one shared counter, no gaps: " + types);
		}
		assertEquals("fcTL", types.get(0));
		assertEquals("fcTL", types.get(1), "frame 0's data is plain IDAT, which takes no number");
		int secondFrameData = types.indexOf("fcTL") == 0 ? types.subList(2, types.lastIndexOf("fcTL")).size() : 0;
		assertTrue(secondFrameData > 1, "frame 1 should span several fdATs: " + types);
		assertTrue(out.stream().filter(c -> c.type.equals("IDAT")).count() > 1, "and frame 0 several IDATs");
	}

	// --- 2. acTL placement and count --------------------------------------------------------

	@Test
	void acTLComesBeforeTheFirstIdatAndCountsEveryFrame() throws IOException {
		int[] a = frame(8, 4, 0x102030, 0);
		List<Chunk> out = chunks(assemble(8, 4, List.of(a, a, a, a), 40, 40, 40, 40));
		List<String> types = out.stream().map(Chunk::type).toList();

		assertEquals("IHDR", types.get(0));
		int actl = types.indexOf("acTL");
		assertTrue(actl > 0 && actl < types.indexOf("IDAT"), types.toString());
		assertEquals(1, types.stream().filter("acTL"::equals).count());
		assertEquals(4, readInt(out.get(actl).data, 0), "num_frames");
		assertEquals(0, readInt(out.get(actl).data, 4), "num_plays: loop forever");
		assertEquals(4, types.stream().filter("fcTL"::equals).count(), "num_frames must match the fcTLs");
		assertEquals("IEND", types.get(types.size() - 1));
	}

	@Test
	void theDefaultImageIsTheFirstFrameNotAPosterOutsideTheCount() throws IOException {
		int[] a = frame(8, 4, 0x102030, 0);
		List<String> types = chunks(assemble(8, 4, List.of(a, a), 40, 40)).stream().map(Chunk::type).toList();
		assertTrue(types.indexOf("fcTL") < types.indexOf("IDAT"), types.toString());
	}

	@Test
	void theFirstFrameCoversTheCanvasAndEveryLaterOneOnlyWhatChanged() throws IOException {
		int[] first = frame(12, 6, 0x102030, 0);
		int[] second = first.clone();
		second[3 * 12 + 7] = 0xFFFF0000; // one pixel at (7, 3)
		List<Chunk> fctls = chunks(assemble(12, 6, List.of(first, second, second), 16, 1234, 40)).stream()
				.filter(c -> c.type.equals("fcTL")).toList();

		Chunk whole = fctls.get(0);
		assertEquals(12, readInt(whole.data, 4));
		assertEquals(6, readInt(whole.data, 8));
		assertEquals(0, whole.data[25], "blend SOURCE: the first frame replaces the canvas");

		Chunk patch = fctls.get(1);
		// The change is at (7, 3); the corner rounds down to (6, 2), so the patch is 2x2.
		assertEquals(6, readInt(patch.data, 12), "x, rounded down to even");
		assertEquals(2, readInt(patch.data, 16), "y, rounded down to even");
		assertEquals(2, readInt(patch.data, 4), "width, from the rounded corner to the change");
		assertEquals(2, readInt(patch.data, 8), "height, likewise");
		assertEquals(1234, readShort(patch.data, 20));
		assertEquals(1000, readShort(patch.data, 22));
		assertEquals(0, patch.data[24], "dispose NONE: the next patch lands on this canvas");
		assertEquals(1, patch.data[25], "blend OVER: the patch's transparent pixels keep the frame before");

		Chunk unchanged = fctls.get(2);
		assertEquals(1, readInt(unchanged.data, 4), "an unchanged frame is a single pixel");
		assertEquals(1, readInt(unchanged.data, 8));
	}

	@Test
	void everyChunkWrittenHasAValidCrc() throws IOException {
		int[] a = frame(8, 4, 0x102030, 0);
		int[] b = frame(8, 4, 0x102030, 3);
		Map<String, String> tags = Map.of("voxelcam:world", "Wörld");
		for (Chunk c : chunks(AnimatedPng.assemble(List.of(a, b), 8, 4, new int[] { 40, 40 }, tags))) {
			assertTrue(c.crcOk, c.type);
		}
	}

	// --- 3. size agreement ------------------------------------------------------------------

	@Test
	void framesOfDifferentSizesAreRefusedRatherThanWrittenCorrupt() {
		int[] small = frame(8, 4, 0x102030, 0);
		int[] large = frame(16, 8, 0x102030, 0);
		assertThrows(IOException.class, () -> assemble(8, 4, List.of(small, large), 40, 40));
	}

	// --- the pixels survive ----------------------------------------------------------------

	@Test
	void everyFramePlaysBackToItsSourcePixels() throws IOException {
		Random random = new Random(4);
		int w = 30;
		int h = 17;
		int[] base = noise(w * h, random);
		List<int[]> sources = new ArrayList<>();
		for (int f = 0; f < 6; f++) {
			int[] next = base.clone();
			for (int y = 2 + f; y < 6 + f; y++) {
				for (int x = 1 + 3 * f; x < 5 + 3 * f; x++) {
					next[y * w + x] = 0xFF000000 | (f * 0x302010);
				}
			}
			sources.add(next);
		}
		sources.add(sources.get(5)); // and one frame that changed nothing
		byte[] apng = assemble(w, h, sources, 40, 40, 40, 40, 40, 40, 40);

		List<int[]> shown = play(apng, w, h);
		assertEquals(sources.size(), shown.size());
		for (int f = 0; f < sources.size(); f++) {
			assertArrayEquals(sources.get(f), shown.get(f), "frame " + f);
		}
		// And a reader that knows nothing of APNG still opens the file, as the first frame.
		BufferedImage still = ImageIO.read(new ByteArrayInputStream(apng));
		assertEquals(sources.get(0)[0], still.getRGB(0, 0));
	}

	@Test
	void splittingKeepsEachFramesDelay() throws IOException {
		List<int[]> frames = List.of(frame(10, 5, 0x336699, 0), frame(10, 5, 0x336699, 4), frame(10, 5, 0x336699, 9));
		List<AnimatedPng.Frame> split = AnimatedPng.splitFrames(
				AnimatedPng.assemble(frames, 10, 5, new int[] { 40, 1234, 25 }, Map.of("voxelcam:world", "Overworld")));
		assertEquals(List.of(40, 1234, 25), split.stream().map(AnimatedPng.Frame::delayMillis).toList());
		for (AnimatedPng.Frame frame : split) {
			for (Chunk c : chunks(frame.png())) {
				assertTrue(c.crcOk, c.type);
			}
		}
	}

	@Test
	void aPlainPngOrGarbageSplitsIntoNothing() throws IOException {
		assertTrue(AnimatedPng.splitFrames(png(8, 4, 0x102030, 0)).isEmpty());
		assertTrue(AnimatedPng.splitFrames("not a png".getBytes(StandardCharsets.US_ASCII)).isEmpty());
		byte[] apng = assemble(8, 4, List.of(frame(8, 4, 0x102030, 0), frame(8, 4, 0x102030, 1)), 40, 40);
		assertTrue(AnimatedPng.splitFrames(Arrays.copyOf(apng, apng.length / 2)).size() < 2,
				"a truncated file must not play a half-read frame as whole");
	}

	@Test
	void aFrameThatDisposesIsNotPlayed() throws IOException {
		byte[] apng = assemble(8, 4, List.of(frame(8, 4, 0x102030, 0), frame(8, 4, 0x102030, 1)), 40, 40);
		// Set the second fcTL's dispose_op: a foreign APNG this player does not reproduce.
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(Arrays.copyOf(apng, 8));
		int fctls = 0;
		for (Chunk c : chunks(apng)) {
			byte[] data = c.data;
			if (c.type.equals("fcTL") && fctls++ == 1) {
				data = data.clone();
				data[24] = 1;
			}
			out.writeBytes(chunk(c.type, data));
		}
		assertTrue(AnimatedPng.splitFrames(out.toByteArray()).isEmpty());
	}

	// --- timing -----------------------------------------------------------------------------

	@Test
	void eachFrameIsHeldUntilTheNextWasCaptured() {
		assertArrayEquals(new int[] { 30, 45, 25, 33 }, AnimatedPng.delaysFor(new int[] { 0, 30, 75, 100 }));
	}

	@Test
	void delaysAreClampedToWhatAPlayerCanSeeAndTheFieldCanHold() {
		int[] delays = AnimatedPng.delaysFor(new int[] { 0, 0, 200_000 });
		assertEquals(AnimatedPng.MIN_DELAY_MILLIS, delays[0]);
		assertEquals(AnimatedPng.MAX_DELAY_MILLIS, delays[1]);
	}

	@Test
	void aLoneFrameFallsBackToTheDefaultDelay() {
		assertArrayEquals(new int[] { AnimatedPng.FALLBACK_DELAY_MILLIS }, AnimatedPng.delaysFor(new int[] { 0 }));
	}

	// --- order ------------------------------------------------------------------------------

	@Test
	void captureOrderFollowsTheTagsAcrossASetAsKeySwap() {
		// The key path now holds what was captured third; frame 02's path holds the original key.
		List<File> files = List.of(new File("k.png"), new File("k_burst01.png"), new File("k_burst02.png"));
		List<Map<String, String>> tags = List.of(
				new BurstFrame("g", 2, 60).toTags(),
				new BurstFrame("g", 1, 30).toTags(),
				new BurstFrame("g", 0, 0).toTags());
		assertArrayEquals(new int[] { 2, 1, 0 }, AnimatedPng.captureOrder(files, tags));
	}

	@Test
	void anUntaggedGroupKeepsTheKeyFirstThenFrameNames() {
		List<File> files = List.of(new File("k.png"), new File("k_burst02.png"), new File("k_burst01.png"));
		List<Map<String, String>> tags = List.of(Map.of(), Map.of(), Map.of());
		assertArrayEquals(new int[] { 0, 2, 1 }, AnimatedPng.captureOrder(files, tags));
	}

	// --- tags and naming --------------------------------------------------------------------

	@Test
	void theAnimationKeepsWhereItWasFilmedButNotItsBurstPositionOrStar() {
		Map<String, String> first = new LinkedHashMap<>(new BurstFrame("g", 0, 0).toTags());
		first.put("voxelcam:world", "Survival");
		first.put(Favorite.STARRED_KEY, "true");

		Map<String, String> carried = AnimatedPng.carriedTags(first, 8);
		assertEquals("Survival", carried.get("voxelcam:world"));
		assertEquals("8", carried.get(AnimatedPng.FRAMES_KEY));
		assertEquals(8, AnimatedPng.frameCount(carried));
		assertFalse(carried.containsKey(Favorite.STARRED_KEY));
		assertTrue(carried.keySet().stream().noneMatch(k -> k.startsWith("voxelcam:burst")), carried.toString());
		assertEquals(0, AnimatedPng.frameCount(Map.of()));
	}

	@Test
	void theTargetNameNeverOverwritesAndIsNeverMistakenForAFrame() throws IOException {
		File key = dir.resolve("2026-11-12_10.00.00.png").toFile();
		File first = AnimatedPng.targetFor(key);
		assertEquals("2026-11-12_10.00.00_animation.png", first.getName());
		assertEquals(null, ScreenshotNamer.burstKeyName(first.getName()));

		Files.write(first.toPath(), new byte[0]);
		assertEquals("2026-11-12_10.00.00_animation_2.png", AnimatedPng.targetFor(key).getName());
	}

	// --- the file path ----------------------------------------------------------------------

	@Test
	void exportWritesOneTaggedAnimationBesideTheKey() throws IOException {
		File key = writeFrame("shot.png", 0, 0, 0);
		File f1 = writeFrame("shot_burst01.png", 1, 40, 1);
		File f2 = writeFrame("shot_burst02.png", 2, 90, 2);

		AnimatedPng.Exported exported = AnimatedPng.export(key, List.of(f2, f1), AnimatedPngTest::argbOf);
		File out = exported.apng();

		assertEquals("shot_animation.png", out.getName());
		byte[] bytes = Files.readAllBytes(out.toPath());
		Map<String, String> tags = PngTextChunk.read(bytes);
		assertEquals(3, AnimatedPng.frameCount(tags));
		assertEquals("Overworld", tags.get("voxelcam:world"));
		List<Integer> delays = chunks(bytes).stream().filter(c -> c.type.equals("fcTL"))
				.map(c -> readShort(c.data, 20)).toList();
		assertEquals(List.of(40, 50, 45), delays);
		assertEquals(PngDimensions.read(key), PngDimensions.read(out));

		// And its sharing copy beside it: the same frames, in the same order, for as long each.
		assertEquals("shot_animation.webp", exported.webp().getName());
		byte[] webp = Files.readAllBytes(exported.webp().toPath());
		assertEquals("RIFF", new String(webp, 0, 4, StandardCharsets.US_ASCII));
		assertEquals("WEBP", new String(webp, 8, 4, StandardCharsets.US_ASCII));
		assertEquals(List.of(40, 50, 45), anmfDurations(webp));
		assertOnlyExportsLeft();
	}

	@Test
	void aRefusedExportLeavesNothingBehind() throws IOException {
		File key = writeFrame("shot.png", 0, 0, 0);
		Path odd = dir.resolve("shot_burst01.png");
		Files.write(odd, png(4, 4, 0, 0));
		PngTextChunk.embed(odd.toFile(), new BurstFrame("g", 1, 40).toTags());

		assertThrows(IOException.class, () -> AnimatedPng.export(key, List.of(odd.toFile()), AnimatedPngTest::argbOf));
		assertFalse(Files.exists(dir.resolve("shot_animation.png")));
		assertFalse(Files.exists(dir.resolve("shot_animation.webp")));
		assertOnlyExportsLeft();
	}

	private File writeFrame(String name, int index, int offset, int marker) throws IOException {
		Path path = dir.resolve(name);
		Files.write(path, png(8, 4, 0x405060, marker));
		Map<String, String> tags = new LinkedHashMap<>(new BurstFrame("g", index, offset).toTags());
		tags.put("voxelcam:world", "Overworld");
		PngTextChunk.embed(path.toFile(), tags);
		return path.toFile();
	}

	@Test
	void theTargetNameIsSkippedWhenOnlyItsWebpNameIsTaken() throws IOException {
		File key = dir.resolve("shot.png").toFile();
		Files.write(dir.resolve("shot_animation.webp"), new byte[0]);
		assertEquals("shot_animation_2.png", AnimatedPng.targetFor(key).getName());
		assertEquals("shot_animation_2.webp", AnimatedPng.webpBeside(AnimatedPng.targetFor(key)).getName());
	}

	private void assertOnlyExportsLeft() throws IOException {
		try (var files = Files.list(dir)) {
			assertTrue(files.allMatch(p -> p.getFileName().toString().matches(".*\\.(png|webp)")),
					"a temp file was left");
		}
	}

	/** The JDK's decoder standing in for NativeImage, which needs a running client. */
	private static int[] argbOf(byte[] png) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	private static List<Integer> anmfDurations(byte[] webp) {
		List<Integer> durations = new ArrayList<>();
		for (int pos = 12; pos < webp.length; ) {
			int size = (webp[pos + 4] & 0xFF) | ((webp[pos + 5] & 0xFF) << 8) | ((webp[pos + 6] & 0xFF) << 16)
					| ((webp[pos + 7] & 0xFF) << 24);
			if (new String(webp, pos, 4, StandardCharsets.US_ASCII).equals("ANMF")) {
				int at = pos + 8 + 12;
				durations.add((webp[at] & 0xFF) | ((webp[at + 1] & 0xFF) << 8) | ((webp[at + 2] & 0xFF) << 16));
			}
			pos += 8 + ((size + 1) & ~1);
		}
		return durations;
	}
}
