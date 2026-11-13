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
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the APNG byte layout. Each of the first three groups is a known way an APNG goes wrong in
 * the field — a browser shows the first frame and nothing else, or nothing at all — so they
 * assert the bytes directly rather than through a decoder that might forgive them. The round
 * trip then proves the reframed pixels are the source pixels, through the JDK's own PNG decoder.
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

	/** The same image with its pixel data spread over {@code parts} IDAT chunks — legal, and what
	 * a large stb-encoded screenshot looks like. Each extra chunk must cost one sequence number. */
	private static byte[] splitIdat(byte[] png, int parts) {
		List<Chunk> chunks = chunks(png);
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		for (Chunk c : chunks) {
			if (c.type.equals("IDAT")) {
				data.writeBytes(c.data);
			}
		}
		byte[] zlib = data.toByteArray();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(Arrays.copyOf(png, 8));
		boolean written = false;
		for (Chunk c : chunks) {
			if (c.type.equals("IDAT")) {
				if (!written) {
					int step = (zlib.length + parts - 1) / parts;
					for (int at = 0; at < zlib.length; at += step) {
						out.writeBytes(chunk("IDAT", Arrays.copyOfRange(zlib, at, Math.min(zlib.length, at + step))));
					}
					written = true;
				}
			} else {
				out.writeBytes(chunk(c.type, c.data));
			}
		}
		return out.toByteArray();
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

	private static byte[] assemble(List<byte[]> frames, int... delays) throws IOException {
		return AnimatedPng.assemble(frames, delays, Map.of());
	}

	// --- 1. sequence numbering --------------------------------------------------------------

	@Test
	void fcTLAndFdATShareOneCounterAndFrameZeroUsesPlainIdat() throws IOException {
		byte[] a = png(8, 4, 0x204060, 0);
		byte[] b = splitIdat(png(8, 4, 0x204060, 1), 3);
		byte[] c = png(8, 4, 0x204060, 2);

		List<Chunk> out = chunks(assemble(List.of(a, b, c), 50, 50, 50));

		List<String> sequenceOf = new ArrayList<>();
		List<Integer> numbers = new ArrayList<>();
		for (Chunk ch : out) {
			if (ch.type.equals("fcTL") || ch.type.equals("fdAT")) {
				sequenceOf.add(ch.type);
				numbers.add(readInt(ch.data, 0));
			}
		}
		// Frame 0: fcTL 0 then IDAT (unnumbered). Frame 1: fcTL 1, three fdATs 2–4. Frame 2: fcTL 5, fdAT 6.
		assertEquals(List.of("fcTL", "fcTL", "fdAT", "fdAT", "fdAT", "fcTL", "fdAT"), sequenceOf);
		assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), numbers);
	}

	// --- 2. acTL placement and count --------------------------------------------------------

	@Test
	void acTLComesBeforeTheFirstIdatAndCountsEveryFrame() throws IOException {
		byte[] a = png(8, 4, 0x102030, 0);
		List<Chunk> out = chunks(assemble(List.of(a, a, a, a), 40, 40, 40, 40));
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
		byte[] a = png(8, 4, 0x102030, 0);
		List<String> types = chunks(assemble(List.of(a, a), 40, 40)).stream().map(Chunk::type).toList();
		assertTrue(types.indexOf("fcTL") < types.indexOf("IDAT"), types.toString());
	}

	@Test
	void everyFrameControlCoversTheWholeCanvasWithItsDelay() throws IOException {
		byte[] a = png(12, 6, 0x102030, 0);
		List<Chunk> fctls = chunks(assemble(List.of(a, a), 16, 1234)).stream()
				.filter(c -> c.type.equals("fcTL")).toList();

		Chunk second = fctls.get(1);
		assertEquals(26, second.data.length);
		assertEquals(12, readInt(second.data, 4));
		assertEquals(6, readInt(second.data, 8));
		assertEquals(0, readInt(second.data, 12));
		assertEquals(0, readInt(second.data, 16));
		assertEquals(1234, readShort(second.data, 20));
		assertEquals(1000, readShort(second.data, 22));
		assertEquals(0, second.data[25], "blend SOURCE, so no frame composites over the last");
	}

	@Test
	void everyChunkWrittenHasAValidCrc() throws IOException {
		byte[] a = png(8, 4, 0x102030, 0);
		byte[] b = splitIdat(png(8, 4, 0x102030, 3), 2);
		Map<String, String> tags = Map.of("voxelcam:world", "Wörld");
		for (Chunk c : chunks(AnimatedPng.assemble(List.of(a, b), new int[] { 40, 40 }, tags))) {
			assertTrue(c.crcOk, c.type);
		}
	}

	// --- 3. IHDR agreement ------------------------------------------------------------------

	@Test
	void framesOfDifferentSizesAreRefusedRatherThanWrittenCorrupt() throws IOException {
		byte[] small = png(8, 4, 0x102030, 0);
		byte[] large = png(16, 8, 0x102030, 0);
		assertThrows(IOException.class, () -> assemble(List.of(small, large), 40, 40));
	}

	@Test
	void somethingThatIsNotAPngIsRefused() {
		assertThrows(IOException.class, () -> assemble(List.of("hello".getBytes(StandardCharsets.US_ASCII)), 40));
	}

	// --- the pixels survive ----------------------------------------------------------------

	@Test
	void eachFrameDecodesBackToItsSourcePixels() throws IOException {
		List<byte[]> sources = List.of(png(10, 5, 0x336699, 0), splitIdat(png(10, 5, 0x336699, 4), 3),
				png(10, 5, 0x336699, 9));
		byte[] apng = assemble(sources, 40, 40, 40);

		// Rebuild each frame as a standalone PNG — IHDR, its data as IDAT, IEND — and decode that.
		List<Chunk> out = chunks(apng);
		Chunk ihdr = out.get(0);
		List<ByteArrayOutputStream> frames = new ArrayList<>();
		for (Chunk c : out) {
			if (c.type.equals("fcTL")) {
				frames.add(new ByteArrayOutputStream());
			} else if (c.type.equals("IDAT")) {
				frames.get(frames.size() - 1).writeBytes(c.data);
			} else if (c.type.equals("fdAT")) {
				frames.get(frames.size() - 1).writeBytes(Arrays.copyOfRange(c.data, 4, c.data.length));
			}
		}
		assertEquals(sources.size(), frames.size());
		for (int i = 0; i < sources.size(); i++) {
			ByteArrayOutputStream standalone = new ByteArrayOutputStream();
			standalone.writeBytes(Arrays.copyOf(apng, 8));
			standalone.writeBytes(chunk("IHDR", ihdr.data));
			standalone.writeBytes(chunk("IDAT", frames.get(i).toByteArray()));
			standalone.writeBytes(chunk("IEND", new byte[0]));

			BufferedImage expected = ImageIO.read(new ByteArrayInputStream(sources.get(i)));
			BufferedImage actual = ImageIO.read(new ByteArrayInputStream(standalone.toByteArray()));
			for (int x = 0; x < 10; x++) {
				assertEquals(expected.getRGB(x, 0), actual.getRGB(x, 0), "frame " + i + " pixel " + x);
			}
		}
		// And a reader that knows nothing of APNG still opens the file, as the first frame.
		BufferedImage still = ImageIO.read(new ByteArrayInputStream(apng));
		assertEquals(0xFFFFFFFF, still.getRGB(0, 0));
		assertEquals(0xFF336699, still.getRGB(4, 0));
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

		File out = AnimatedPng.export(key, List.of(f2, f1));

		assertEquals("shot_animation.png", out.getName());
		byte[] bytes = Files.readAllBytes(out.toPath());
		Map<String, String> tags = PngTextChunk.read(bytes);
		assertEquals(3, AnimatedPng.frameCount(tags));
		assertEquals("Overworld", tags.get("voxelcam:world"));
		List<Integer> delays = chunks(bytes).stream().filter(c -> c.type.equals("fcTL"))
				.map(c -> readShort(c.data, 20)).toList();
		assertEquals(List.of(40, 50, 45), delays);
		assertEquals(PngDimensions.read(key), PngDimensions.read(out));
		assertOnlyPngsLeft();
	}

	@Test
	void aRefusedExportLeavesNothingBehind() throws IOException {
		File key = writeFrame("shot.png", 0, 0, 0);
		Path odd = dir.resolve("shot_burst01.png");
		Files.write(odd, png(4, 4, 0, 0));
		PngTextChunk.embed(odd.toFile(), new BurstFrame("g", 1, 40).toTags());

		assertThrows(IOException.class, () -> AnimatedPng.export(key, List.of(odd.toFile())));
		assertFalse(AnimatedPng.targetFor(key).getName().endsWith("_2.png"));
		assertFalse(Files.exists(dir.resolve("shot_animation.png")));
		assertOnlyPngsLeft();
	}

	private File writeFrame(String name, int index, int offset, int marker) throws IOException {
		Path path = dir.resolve(name);
		Files.write(path, png(8, 4, 0x405060, marker));
		Map<String, String> tags = new LinkedHashMap<>(new BurstFrame("g", index, offset).toTags());
		tags.put("voxelcam:world", "Overworld");
		PngTextChunk.embed(path.toFile(), tags);
		return path.toFile();
	}

	private void assertOnlyPngsLeft() throws IOException {
		try (var files = Files.list(dir)) {
			assertTrue(files.allMatch(p -> p.getFileName().toString().endsWith(".png")), "a temp file was left");
		}
	}
}
