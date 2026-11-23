package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The encoder was checked bit-exact against libwebp (through Pillow) on noise, gradients, still
 * frames, moving objects and scenes from 1x1 up to 854x480, and on a real 30-frame burst. That check needs Python, so what runs here is the
 * same round trip through a decoder for exactly the subset {@link AnimatedWebp} writes — written
 * from RFC 9649, not from the encoder — plus the container layout every viewer relies on.
 */
class AnimatedWebpTest {

	// --- the container ----------------------------------------------------------------------

	@Test
	void theFileIsARiffOfVp8xAnimAndOneAnmfPerFrame() {
		AnimatedWebp webp = new AnimatedWebp(5, 3);
		webp.frame(solid(15, 0xFF336699), 40);
		webp.frame(solid(15, 0xFF336698), 1234);
		byte[] file = webp.finish();

		assertEquals("RIFF", ascii(file, 0));
		assertEquals(file.length - 8, le32(file, 4));
		assertEquals("WEBP", ascii(file, 8));
		List<String> chunks = new ArrayList<>();
		for (int pos = 12; pos < file.length; ) {
			chunks.add(ascii(file, pos));
			pos += 8 + ((le32(file, pos + 4) + 1) & ~1);
		}
		assertEquals(List.of("VP8X", "ANIM", "ANMF", "ANMF"), chunks);
		assertEquals(0x12, file[20], "VP8X flags: animation, and alpha for the patches' transparency");
		assertEquals(4, le24(file, 24), "canvas width - 1");
		assertEquals(2, le24(file, 27), "canvas height - 1");
		assertEquals(0, file[30 + 8 + 4] | file[30 + 8 + 5], "loop count 0: forever");

		List<Frame> frames = decodeAll(file);
		assertEquals(List.of(40, 1234), frames.stream().map(Frame::duration).toList());
		assertEquals(0x02, frames.get(0).flags(), "the first frame replaces the canvas");
		assertEquals(0x00, frames.get(1).flags(), "every later frame is a patch that blends over the last");
	}

	@Test
	void aCanvasWebpCannotHoldIsRefusedUpFront() {
		assertThrows(IllegalArgumentException.class, () -> new AnimatedWebp(AnimatedWebp.MAX_DIMENSION + 1, 10));
		assertThrows(IllegalArgumentException.class, () -> new AnimatedWebp(10, 10).frame(new int[99], 40));
	}

	// --- the pixels round-trip --------------------------------------------------------------

	@Test
	void everyKindOfImageDecodesBackExactly() {
		Random random = new Random(7);
		int[][] sizes = { { 1, 1 }, { 7, 3 }, { 64, 48 }, { 333, 111 } };
		for (int[] size : sizes) {
			int w = size[0];
			int h = size[1];
			List<int[]> images = List.of(
					noise(w * h, random),
					solid(w * h, 0xFF87CEEB),
					gradient(w, h),
					scene(w, h, random));
			for (int[] image : images) {
				assertArrayEquals(image, decodeVp8l(AnimatedWebp.encodeVp8l(image, w, h), w, h), w + "x" + h);
			}
		}
	}

	@Test
	void theWholeAnimationDecodesBackFrameByFrame() {
		Random random = new Random(3);
		int[] a = scene(40, 20, random);
		int[] b = scene(40, 20, random);
		AnimatedWebp webp = new AnimatedWebp(40, 20);
		webp.frame(a, 20);
		webp.frame(b, 30);
		List<Frame> frames = decodeAll(webp.finish());
		assertArrayEquals(a, frames.get(0).pixels());
		assertArrayEquals(b, frames.get(1).pixels());
	}

	@Test
	void aFrameStoresOnlyTheRectangleThatChanged() {
		int w = 41;
		int h = 23;
		int[] still = scene(w, h, new Random(5));
		int[] moved = still.clone();
		// A 3x2 change starting at odd coordinates: the patch has to round its corner down to even.
		for (int y = 7; y < 9; y++) {
			for (int x = 13; x < 16; x++) {
				moved[y * w + x] = 0xFFFF0000;
			}
		}
		AnimatedWebp webp = new AnimatedWebp(w, h);
		webp.frame(still, 100);
		webp.frame(moved, 100);
		webp.frame(moved, 100);
		byte[] file = webp.finish();

		List<int[]> rects = anmfRects(file);
		assertArrayEquals(new int[] { 0, 0, w, h }, rects.get(0), "the first frame covers the canvas");
		assertArrayEquals(new int[] { 12, 6, 4, 3 }, rects.get(1), "x, y, width, height of the changed area");
		assertArrayEquals(new int[] { 0, 0, 1, 1 }, rects.get(2), "an unchanged frame is one transparent pixel");

		List<Frame> frames = decodeAll(file);
		assertArrayEquals(still, frames.get(0).pixels());
		assertArrayEquals(moved, frames.get(1).pixels());
		assertArrayEquals(moved, frames.get(2).pixels());
	}

	@Test
	void aMovingObjectAcrossManyFramesCompositesBackExactly() {
		int w = 64;
		int h = 40;
		int[] background = scene(w, h, new Random(9));
		AnimatedWebp webp = new AnimatedWebp(w, h);
		List<int[]> sources = new ArrayList<>();
		for (int f = 0; f < 8; f++) {
			int[] frame = background.clone();
			for (int y = 3 + f; y < 9 + f; y++) {
				for (int x = 5 + f * 7; x < 10 + f * 7; x++) {
					frame[y * w + x] = 0xFF000000 | (f * 0x203040);
				}
			}
			sources.add(frame);
			webp.frame(frame, 100);
		}
		List<Frame> frames = decodeAll(webp.finish());
		for (int f = 0; f < sources.size(); f++) {
			assertArrayEquals(sources.get(f), frames.get(f).pixels(), "frame " + f);
		}
	}

	@Test
	void onlyLeftAndAboveUseTheShortDistanceCodes() {
		assertEquals(2, AnimatedWebp.distanceCode(1, 100));
		assertEquals(1, AnimatedWebp.distanceCode(100, 100));
		assertEquals(5 + 120, AnimatedWebp.distanceCode(5, 100));
		assertEquals(2, AnimatedWebp.distanceCode(1, 1), "a one-pixel-wide image: left wins, the codes agree");
	}

	@Test
	void runsAndRepeatedRowsCompressWellBelowRawSize() {
		// The sky-and-field case: one colour above, a repeating texture below.
		int w = 256;
		int h = 128;
		int[] image = scene(w, h, new Random(1));
		byte[] encoded = AnimatedWebp.encodeVp8l(image, w, h);
		assertTrue(encoded.length < w * h * 3 / 4, "was " + encoded.length + " bytes");
	}

	// --- the coding primitives ----------------------------------------------------------------

	@Test
	void lengthAndDistancePrefixesMatchTheSpecTable() {
		// value → (prefix, extra bits, extra value), from RFC 9649 §5.2.2's decoding direction.
		int[][] cases = { { 1, 0, 0, 0 }, { 4, 3, 0, 0 }, { 5, 4, 1, 0 }, { 6, 4, 1, 1 }, { 7, 5, 1, 0 },
				{ 9, 6, 2, 0 }, { 4096, 23, 10, 1023 } };
		for (int[] c : cases) {
			int prefix = AnimatedWebp.prefixOf(c[0]);
			assertEquals(c[1], prefix, "prefix of " + c[0]);
			assertEquals(c[2], AnimatedWebp.extraBitsOf(prefix), "extra bits of " + c[0]);
			assertEquals(c[3], AnimatedWebp.extraValueOf(c[0]), "extra value of " + c[0]);
			assertEquals(c[0], prefixDecode(prefix, c[3]), "round trip of " + c[0]);
		}
	}

	@Test
	void codeLengthsRespectTheLimitAndFormACompleteCode() {
		// Fibonacci counts are the classic worst case: an unlimited Huffman tree is as deep as it is wide.
		int[] histogram = new int[30];
		histogram[0] = 1;
		histogram[1] = 1;
		for (int i = 2; i < histogram.length; i++) {
			histogram[i] = histogram[i - 1] + histogram[i - 2];
		}
		int[] lengths = AnimatedWebp.limitedLengths(histogram, 15);
		double kraft = 0;
		for (int length : lengths) {
			assertTrue(length >= 1 && length <= 15, "length " + length);
			kraft += Math.pow(2, -length);
		}
		assertEquals(1.0, kraft, 1e-12, "a complete prefix code, or the decoder rejects it");
	}

	// --- fixtures -----------------------------------------------------------------------------

	private static int[] solid(int n, int argb) {
		int[] pixels = new int[n];
		Arrays.fill(pixels, argb);
		return pixels;
	}

	private static int[] noise(int n, Random random) {
		int[] pixels = new int[n];
		for (int i = 0; i < n; i++) {
			pixels[i] = 0xFF000000 | random.nextInt(0x1000000);
		}
		return pixels;
	}

	private static int[] gradient(int w, int h) {
		int[] pixels = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				pixels[y * w + x] = 0xFF000000 | ((x * 255 / Math.max(1, w - 1)) << 16)
						| ((y * 255 / Math.max(1, h - 1)) << 8) | ((x + y) & 0xFF);
			}
		}
		return pixels;
	}

	/** Sky over a checkered field with a little noise: what a screenshot is mostly made of. */
	private static int[] scene(int w, int h, Random random) {
		int[] pixels = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = y < h / 2 ? 0x87CEEB : ((x / 8 + y / 8) % 2 == 0 ? 0x228B22 : 0x1E7B1E);
				if (random.nextInt(50) == 0) {
					rgb = random.nextInt(0x1000000);
				}
				pixels[y * w + x] = 0xFF000000 | rgb;
			}
		}
		return pixels;
	}

	// --- a decoder for the subset AnimatedWebp writes -----------------------------------------

	/** The composited canvas after each frame, its duration and its ANMF flags. */
	private record Frame(int[] pixels, int duration, int flags) {
	}

	private static List<Frame> decodeAll(byte[] file) {
		int canvasWidth = le24(file, 24) + 1;
		int canvasHeight = le24(file, 27) + 1;
		int[] canvas = new int[canvasWidth * canvasHeight];
		List<Frame> frames = new ArrayList<>();
		for (int pos = 12; pos < file.length; ) {
			String type = ascii(file, pos);
			int size = le32(file, pos + 4);
			if (type.equals("ANMF")) {
				int at = pos + 8;
				int x0 = le24(file, at) * 2;
				int y0 = le24(file, at + 3) * 2;
				int w = le24(file, at + 6) + 1;
				int h = le24(file, at + 9) + 1;
				int duration = le24(file, at + 12);
				int flags = file[at + 15];
				assertEquals("VP8L", ascii(file, at + 16));
				int vp8lSize = le32(file, at + 20);
				int[] patch = decodeVp8l(Arrays.copyOfRange(file, at + 24, at + 24 + vp8lSize), w, h);
				boolean blend = (flags & 0x02) == 0;
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int pixel = patch[y * w + x];
						int alpha = pixel >>> 24;
						if (blend && alpha == 0) {
							continue;
						}
						assertTrue(!blend || alpha == 0xFF, "the encoder only ever writes opaque or fully transparent");
						canvas[(y0 + y) * canvasWidth + x0 + x] = pixel;
					}
				}
				frames.add(new Frame(canvas.clone(), duration, flags));
			}
			pos += 8 + ((size + 1) & ~1);
		}
		return frames;
	}

	private static final int[] CODE_LENGTH_ORDER = { 17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };

	private static int[] decodeVp8l(byte[] data, int expectedWidth, int expectedHeight) {
		BitReader in = new BitReader(data);
		assertEquals(0x2F, in.read(8), "signature");
		int width = in.read(14) + 1;
		int height = in.read(14) + 1;
		assertEquals(expectedWidth, width);
		assertEquals(expectedHeight, height);
		in.read(1); // alpha hint
		assertEquals(0, in.read(3), "version");

		List<int[]> transforms = new ArrayList<>(); // {type, bits}, with the predictor's modes kept below
		int[] predictorModes = null;
		int predictorBits = 0;
		while (in.read(1) == 1) {
			int type = in.read(2);
			if (type == 2) {
				transforms.add(new int[] { 2 });
			} else {
				assertEquals(0, type, "only subtract-green and the predictor are expected");
				predictorBits = in.read(3) + 2;
				int blocksWide = (width + (1 << predictorBits) - 1) >> predictorBits;
				int blocksHigh = (height + (1 << predictorBits) - 1) >> predictorBits;
				predictorModes = decodeImage(in, blocksWide, blocksHigh, false);
				transforms.add(new int[] { 0 });
			}
		}
		int[] pixels = decodeImage(in, width, height, true);

		// Undo the transforms in reverse order of reading.
		for (int t = transforms.size() - 1; t >= 0; t--) {
			if (transforms.get(t)[0] == 0) {
				unpredict(pixels, width, height, predictorModes, predictorBits);
			} else {
				for (int i = 0; i < pixels.length; i++) {
					int p = pixels[i];
					int g = (p >>> 8) & 0xFF;
					int r = (((p >>> 16) & 0xFF) + g) & 0xFF;
					int b = ((p & 0xFF) + g) & 0xFF;
					pixels[i] = (p & 0xFF00FF00) | (r << 16) | b;
				}
			}
		}
		return pixels;
	}

	private static int[] decodeImage(BitReader in, int width, int height, boolean main) {
		int cacheBits = in.read(1) == 1 ? in.read(4) : 0;
		int cacheSize = cacheBits > 0 ? 1 << cacheBits : 0;
		if (main) {
			assertEquals(0, in.read(1), "no meta prefix codes");
		}
		Huffman green = readCode(in, 256 + 24 + cacheSize);
		Huffman red = readCode(in, 256);
		Huffman blue = readCode(in, 256);
		Huffman alpha = readCode(in, 256);
		Huffman distance = readCode(in, 40);

		int n = width * height;
		int[] pixels = new int[n];
		int[] cache = new int[Math.max(1, cacheSize)];
		for (int i = 0; i < n; ) {
			int g = green.read(in);
			int produced;
			if (g < 256) {
				int r = red.read(in);
				int b = blue.read(in);
				int a = alpha.read(in);
				pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
				produced = 1;
			} else if (g < 256 + 24) {
				int lengthPrefix = g - 256;
				int length = prefixDecode(lengthPrefix, in.read(prefixExtraBits(lengthPrefix)));
				int distancePrefix = distance.read(in);
				int code = prefixDecode(distancePrefix, in.read(prefixExtraBits(distancePrefix)));
				int dist = switch (code) {
					case 1 -> width; // (0, 1): the pixel above
					case 2 -> 1; // (1, 0): the pixel to the left
					default -> {
						assertTrue(code > 120, "only codes 1, 2 and plain distances are written, got " + code);
						yield code - 120;
					}
				};
				for (int k = 0; k < length; k++) {
					pixels[i + k] = pixels[i + k - dist];
				}
				produced = length;
			} else {
				pixels[i] = cache[g - 256 - 24];
				produced = 1;
			}
			for (int k = 0; k < produced && cacheSize > 0; k++) {
				int p = pixels[i + k];
				cache[(0x1E35A7BD * p) >>> (32 - cacheBits)] = p;
			}
			i += produced;
		}
		return pixels;
	}

	/** RFC 9649 §4.1, in decoding order: every prediction reads pixels already restored. */
	private static void unpredict(int[] p, int width, int height, int[] modes, int bits) {
		int blocksWide = (width + (1 << bits) - 1) >> bits;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int i = y * width + x;
				int prediction;
				if (i == 0) {
					prediction = 0xFF000000;
				} else if (y == 0) {
					prediction = p[i - 1];
				} else if (x == 0) {
					prediction = p[i - width];
				} else {
					int mode = (modes[(y >> bits) * blocksWide + (x >> bits)] >>> 8) & 0xFF;
					int l = p[i - 1];
					int t = p[i - width];
					int tl = p[i - width - 1];
					int tr = p[i - width + 1];
					prediction = switch (mode) {
						case 0 -> 0xFF000000;
						case 1 -> l;
						case 2 -> t;
						case 3 -> tr;
						case 4 -> tl;
						case 5 -> avg(avg(l, tr), t);
						case 6 -> avg(l, tl);
						case 7 -> avg(l, t);
						case 8 -> avg(tl, t);
						case 9 -> avg(t, tr);
						case 10 -> avg(avg(l, tl), avg(t, tr));
						case 11 -> {
							int pl = 0;
							int pt = 0;
							for (int s = 0; s < 32; s += 8) {
								int e = ((l >>> s) & 0xFF) + ((t >>> s) & 0xFF) - ((tl >>> s) & 0xFF);
								pl += Math.abs(e - ((l >>> s) & 0xFF));
								pt += Math.abs(e - ((t >>> s) & 0xFF));
							}
							yield pl < pt ? l : t;
						}
						case 12 -> perChannel(l, t, tl, (a, b, c) -> a + b - c);
						case 13 -> perChannel(avg(l, t), tl, 0, (a, b, c) -> a + (a - b) / 2);
						default -> throw new AssertionError("predictor mode " + mode);
					};
				}
				int out = 0;
				for (int s = 0; s < 32; s += 8) {
					out |= ((((p[i] >>> s) & 0xFF) + ((prediction >>> s) & 0xFF)) & 0xFF) << s;
				}
				p[i] = out;
			}
		}
	}

	private interface ChannelOp {
		int apply(int a, int b, int c);
	}

	private static int perChannel(int a, int b, int c, ChannelOp op) {
		int out = 0;
		for (int s = 0; s < 32; s += 8) {
			int v = op.apply((a >>> s) & 0xFF, (b >>> s) & 0xFF, (c >>> s) & 0xFF);
			out |= Math.max(0, Math.min(255, v)) << s;
		}
		return out;
	}

	private static int avg(int a, int b) {
		int out = 0;
		for (int s = 0; s < 32; s += 8) {
			out |= ((((a >>> s) & 0xFF) + ((b >>> s) & 0xFF)) / 2) << s;
		}
		return out;
	}

	private static int prefixExtraBits(int prefix) {
		return prefix < 4 ? 0 : (prefix - 2) >> 1;
	}

	private static int prefixDecode(int prefix, int extra) {
		if (prefix < 4) {
			return prefix + 1;
		}
		int extraBits = (prefix - 2) >> 1;
		int offset = (2 + (prefix & 1)) << extraBits;
		return offset + extra + 1;
	}

	private static Huffman readCode(BitReader in, int alphabet) {
		int[] lengths = new int[alphabet];
		if (in.read(1) == 1) {
			int symbols = in.read(1) + 1;
			int first = in.read(1) == 1 ? in.read(8) : in.read(1);
			lengths[first] = 1;
			if (symbols == 2) {
				lengths[in.read(8)] = 1;
			}
			return new Huffman(lengths);
		}
		int[] lengthCodeLengths = new int[19];
		int count = in.read(4) + 4;
		for (int i = 0; i < count; i++) {
			lengthCodeLengths[CODE_LENGTH_ORDER[i]] = in.read(3);
		}
		Huffman lengthCode = new Huffman(lengthCodeLengths);
		int maxSymbol = alphabet;
		if (in.read(1) == 1) {
			maxSymbol = 2 + in.read(2 + 2 * in.read(3));
		}
		int previous = 8;
		for (int s = 0; s < alphabet && maxSymbol-- > 0; ) {
			int symbol = lengthCode.read(in);
			if (symbol < 16) {
				lengths[s++] = symbol;
				if (symbol != 0) {
					previous = symbol;
				}
			} else {
				int repeat = switch (symbol) {
					case 16 -> 3 + in.read(2);
					case 17 -> 3 + in.read(3);
					default -> 11 + in.read(7);
				};
				int value = symbol == 16 ? previous : 0;
				for (int k = 0; k < repeat; k++) {
					lengths[s++] = value;
				}
			}
		}
		return new Huffman(lengths);
	}

	/** A canonical prefix code read one bit at a time; a lone symbol costs no bits, per the spec. */
	private static final class Huffman {
		private final int[] lengths;
		private final int[] codes;
		private final int only;

		Huffman(int[] lengths) {
			this.lengths = lengths;
			this.codes = new int[lengths.length];
			int used = 0;
			int last = -1;
			int[] perLength = new int[16];
			for (int s = 0; s < lengths.length; s++) {
				if (lengths[s] > 0) {
					used++;
					last = s;
					perLength[lengths[s]]++;
				}
			}
			this.only = used == 1 ? last : -1;
			int[] next = new int[17];
			int code = 0;
			for (int bits = 1; bits <= 15; bits++) {
				code = (code + perLength[bits - 1]) << 1;
				next[bits] = code;
			}
			for (int s = 0; s < lengths.length; s++) {
				if (lengths[s] > 0) {
					codes[s] = next[lengths[s]]++;
				}
			}
		}

		int read(BitReader in) {
			if (only >= 0) {
				return only;
			}
			int code = 0;
			for (int length = 1; length <= 15; length++) {
				code = (code << 1) | in.read(1);
				for (int s = 0; s < lengths.length; s++) {
					if (lengths[s] == length && codes[s] == code) {
						return s;
					}
				}
			}
			throw new AssertionError("no symbol for code " + Integer.toBinaryString(code));
		}
	}

	private static final class BitReader {
		private final byte[] data;
		private long position;

		BitReader(byte[] data) {
			this.data = data;
		}

		int read(int bits) {
			int value = 0;
			for (int i = 0; i < bits; i++, position++) {
				int bit = (data[(int) (position >>> 3)] >>> (position & 7)) & 1;
				value |= bit << i;
			}
			return value;
		}
	}

	/** Each ANMF's x, y, width and height, in pixels. */
	private static List<int[]> anmfRects(byte[] file) {
		List<int[]> rects = new ArrayList<>();
		for (int pos = 12; pos < file.length; ) {
			int size = le32(file, pos + 4);
			if (ascii(file, pos).equals("ANMF")) {
				int at = pos + 8;
				rects.add(new int[] { le24(file, at) * 2, le24(file, at + 3) * 2, le24(file, at + 6) + 1,
						le24(file, at + 9) + 1 });
			}
			pos += 8 + ((size + 1) & ~1);
		}
		return rects;
	}

	private static String ascii(byte[] data, int at) {
		return new String(data, at, 4, StandardCharsets.US_ASCII);
	}

	private static int le32(byte[] d, int at) {
		return (d[at] & 0xFF) | ((d[at + 1] & 0xFF) << 8) | ((d[at + 2] & 0xFF) << 16) | ((d[at + 3] & 0xFF) << 24);
	}

	private static int le24(byte[] d, int at) {
		return (d[at] & 0xFF) | ((d[at + 1] & 0xFF) << 8) | ((d[at + 2] & 0xFF) << 16);
	}
}
