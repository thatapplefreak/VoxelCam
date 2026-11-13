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
 * The encoder was checked bit-exact against libwebp (through Pillow) on noise, gradients, flat
 * fields and mixed scenes from 1x1 up to 854x480. That check needs Python, so what runs here is the
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
		assertEquals(0x02, file[20], "VP8X flags: animation, and nothing else");
		assertEquals(4, le24(file, 24), "canvas width - 1");
		assertEquals(2, le24(file, 27), "canvas height - 1");
		assertEquals(0, file[30 + 8 + 4] | file[30 + 8 + 5], "loop count 0: forever");

		List<Frame> frames = decodeAll(file);
		assertEquals(List.of(40, 1234), frames.stream().map(Frame::duration).toList());
		assertEquals(0x02, frames.get(0).flags(), "do not blend, do not dispose: each frame replaces the last");
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

	private record Frame(int[] pixels, int duration, int flags) {
	}

	private static List<Frame> decodeAll(byte[] file) {
		List<Frame> frames = new ArrayList<>();
		for (int pos = 12; pos < file.length; ) {
			String type = ascii(file, pos);
			int size = le32(file, pos + 4);
			if (type.equals("ANMF")) {
				int at = pos + 8;
				int w = le24(file, at + 6) + 1;
				int h = le24(file, at + 9) + 1;
				int duration = le24(file, at + 12);
				int flags = file[at + 15];
				assertEquals("VP8L", ascii(file, at + 16));
				int vp8lSize = le32(file, at + 20);
				byte[] vp8l = Arrays.copyOfRange(file, at + 24, at + 24 + vp8lSize);
				frames.add(new Frame(decodeVp8l(vp8l, w, h), duration, flags));
			}
			pos += 8 + ((size + 1) & ~1);
		}
		return frames;
	}

	private static final int[] CODE_LENGTH_ORDER = { 17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
	/** The first entries of RFC 9649's distance map, (xi, yi): all the encoder ever uses is 1 and 2. */
	private static final int[][] DISTANCE_MAP = { { 0, 1 }, { 1, 0 }, { 1, 1 }, { -1, 1 } };

	private static int[] decodeVp8l(byte[] data, int expectedWidth, int expectedHeight) {
		BitReader in = new BitReader(data);
		assertEquals(0x2F, in.read(8), "signature");
		int width = in.read(14) + 1;
		int height = in.read(14) + 1;
		assertEquals(expectedWidth, width);
		assertEquals(expectedHeight, height);
		in.read(1); // alpha hint
		assertEquals(0, in.read(3), "version");
		boolean subtractGreen = false;
		while (in.read(1) == 1) {
			int transform = in.read(2);
			assertEquals(2, transform, "only subtract-green is expected");
			subtractGreen = true;
		}
		assertEquals(0, in.read(1), "no colour cache");
		assertEquals(0, in.read(1), "no meta prefix codes");
		Huffman green = readCode(in, 256 + 24);
		Huffman red = readCode(in, 256);
		Huffman blue = readCode(in, 256);
		Huffman alpha = readCode(in, 256);
		Huffman distance = readCode(in, 40);

		int n = width * height;
		int[] pixels = new int[n];
		for (int i = 0; i < n; ) {
			int g = green.read(in);
			if (g < 256) {
				int r = red.read(in);
				int b = blue.read(in);
				int a = alpha.read(in);
				pixels[i++] = (a << 24) | (r << 16) | (g << 8) | b;
			} else {
				int lengthPrefix = g - 256;
				int length = prefixDecode(lengthPrefix, in.read(prefixExtraBits(lengthPrefix)));
				int distancePrefix = distance.read(in);
				int code = prefixDecode(distancePrefix, in.read(prefixExtraBits(distancePrefix)));
				int dist;
				if (code <= DISTANCE_MAP.length) {
					dist = Math.max(1, DISTANCE_MAP[code - 1][0] + DISTANCE_MAP[code - 1][1] * width);
				} else {
					dist = code - 120;
				}
				for (int k = 0; k < length; k++, i++) {
					pixels[i] = pixels[i - dist];
				}
			}
		}
		if (subtractGreen) {
			for (int i = 0; i < n; i++) {
				int p = pixels[i];
				int g = (p >>> 8) & 0xFF;
				int r = (((p >>> 16) & 0xFF) + g) & 0xFF;
				int b = ((p & 0xFF) + g) & 0xFF;
				pixels[i] = (p & 0xFF00FF00) | (r << 16) | b;
			}
		}
		return pixels;
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
