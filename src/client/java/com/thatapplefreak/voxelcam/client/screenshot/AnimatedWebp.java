package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A lossless animated WebP encoder, in plain Java, for the copy of an animation that gets shared.
 * The manager keeps the APNG ({@link AnimatedPng}); this exists because Discord, where these get
 * posted, shows only the first frame of an APNG but plays an animated WebP.
 *
 * <p>Deliberately the simple end of what lossless WebP (VP8L, RFC 9649) allows, and nothing that
 * trades correctness for size: one subtract-green transform, one set of prefix codes per frame, no
 * colour cache, and backward references only to the previous pixel and the pixel above — which is
 * what a Minecraft sky, a hotbar or a flat field is made of. Each frame is encoded whole. Smarter
 * compression (predictor transforms, a real LZ77 search, coding only what changed between frames)
 * is a follow-up, not a correctness question.
 *
 * <p>Pixels are ARGB ints, the same packing {@code NativeImage.getPixel} returns. Alpha is
 * dropped: a screenshot is opaque, and an opaque file is what every viewer handles the same way.
 */
public final class AnimatedWebp {

	/** VP8L stores each dimension minus one in 14 bits. */
	public static final int MAX_DIMENSION = 1 << 14;

	private final int width;
	private final int height;
	private final List<byte[]> frames = new ArrayList<>();
	private final List<Integer> delays = new ArrayList<>();

	public AnimatedWebp(int width, int height) {
		if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
			throw new IllegalArgumentException("WebP cannot hold a " + width + "x" + height + " image");
		}
		this.width = width;
		this.height = height;
	}

	/** Encodes one frame now, so only its compressed bytes are kept until {@link #finish()}. */
	public void frame(int[] argb, int delayMillis) {
		if (argb.length != width * height) {
			throw new IllegalArgumentException("Frame has " + argb.length + " pixels, the canvas " + width * height);
		}
		frames.add(encodeVp8l(argb, width, height));
		delays.add(Math.max(0, Math.min(0xFFFFFF, delayMillis)));
	}

	/** The whole file: RIFF, then VP8X, ANIM, and one ANMF per frame, looping forever. */
	public byte[] finish() {
		if (frames.isEmpty()) {
			throw new IllegalStateException("An animation needs at least one frame");
		}
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.writeBytes(ascii("WEBP"));

		byte[] vp8x = new byte[10];
		vp8x[0] = 0x02; // animation; no alpha, ICC, EXIF or XMP
		put24(vp8x, 4, width - 1);
		put24(vp8x, 7, height - 1);
		writeChunk(body, "VP8X", vp8x);

		byte[] anim = new byte[6]; // background colour 0 (unused: every frame covers the canvas), loop forever
		writeChunk(body, "ANIM", anim);

		for (int i = 0; i < frames.size(); i++) {
			ByteArrayOutputStream anmf = new ByteArrayOutputStream();
			byte[] header = new byte[16];
			put24(header, 0, 0); // x / 2
			put24(header, 3, 0); // y / 2
			put24(header, 6, width - 1);
			put24(header, 9, height - 1);
			put24(header, 12, delays.get(i));
			header[15] = 0x02; // do not blend: each frame replaces the last; no disposal
			anmf.writeBytes(header);
			writeChunk(anmf, "VP8L", frames.get(i));
			writeChunk(body, "ANMF", anmf.toByteArray());
		}

		byte[] payload = body.toByteArray();
		ByteArrayOutputStream file = new ByteArrayOutputStream(payload.length + 8);
		file.writeBytes(ascii("RIFF"));
		file.writeBytes(le32(payload.length));
		file.writeBytes(payload);
		return file.toByteArray();
	}

	// --- VP8L -----------------------------------------------------------------------------------

	private static final int GREEN_ALPHABET = 256 + 24; // literals, then length prefixes; no colour cache
	private static final int DISTANCE_ALPHABET = 40;
	private static final int MAX_CODE_LENGTH = 15;
	private static final int MAX_CODE_LENGTH_CODE_LENGTH = 7;
	private static final int MIN_MATCH = 3;
	private static final int MAX_MATCH = 4096;
	/** The VP8L distance codes for "the pixel above" and "the previous pixel" — the first two
	 * entries of the spec's 2D distance map, (0,1) and (1,0). */
	private static final int DISTANCE_CODE_ABOVE = 1;
	private static final int DISTANCE_CODE_PREVIOUS = 2;
	private static final int[] CODE_LENGTH_ORDER = { 17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };

	/** Package-private seam: one frame as a bare VP8L bitstream, the payload of a {@code VP8L} chunk. */
	static byte[] encodeVp8l(int[] argb, int width, int height) {
		int n = width * height;
		int[] pixels = new int[n];
		for (int i = 0; i < n; i++) {
			// Subtract green: red and blue usually track green, so the differences cluster near 0.
			int p = argb[i];
			int g = (p >>> 8) & 0xFF;
			int r = (((p >>> 16) & 0xFF) - g) & 0xFF;
			int b = ((p & 0xFF) - g) & 0xFF;
			pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
		}

		// Tokens: a literal pixel, or (length, distance code) copying from earlier in the frame.
		int[] tokens = new int[n * 2];
		int tokenCount = 0;
		int[] green = new int[GREEN_ALPHABET];
		int[] red = new int[256];
		int[] blue = new int[256];
		int[] alpha = new int[256];
		int[] distance = new int[DISTANCE_ALPHABET];
		for (int i = 0; i < n; ) {
			int previous = i >= 1 ? matchLength(pixels, i, i - 1, n) : 0;
			int above = i >= width ? matchLength(pixels, i, i - width, n) : 0;
			int length = Math.max(previous, above);
			if (length >= MIN_MATCH) {
				int code = above >= previous ? DISTANCE_CODE_ABOVE : DISTANCE_CODE_PREVIOUS;
				tokens[tokenCount++] = -length; // negative marks a copy
				tokens[tokenCount++] = code;
				green[256 + prefixOf(length)]++;
				distance[prefixOf(code)]++;
				i += length;
			} else {
				// Without its alpha byte, which is always 0xFF here: with it every literal would be
				// negative, which is how a copy is marked.
				int p = pixels[i] & 0x00FFFFFF;
				tokens[tokenCount++] = p;
				green[(p >>> 8) & 0xFF]++;
				red[(p >>> 16) & 0xFF]++;
				blue[p & 0xFF]++;
				alpha[0xFF]++;
				i++;
			}
		}

		BitWriter out = new BitWriter(n / 2 + 64);
		out.put(0x2F, 8);
		out.put(width - 1, 14);
		out.put(height - 1, 14);
		out.put(0, 1); // alpha_is_used
		out.put(0, 3); // version
		out.put(1, 1); // a transform follows
		out.put(2, 2); // SUBTRACT_GREEN
		out.put(0, 1); // no more transforms
		out.put(0, 1); // no colour cache
		out.put(0, 1); // no meta prefix codes: one group for the whole frame

		PrefixCode greenCode = writeCode(out, green);
		PrefixCode redCode = writeCode(out, red);
		PrefixCode blueCode = writeCode(out, blue);
		PrefixCode alphaCode = writeCode(out, alpha);
		PrefixCode distanceCode = writeCode(out, distance);

		for (int t = 0; t < tokenCount; t++) {
			int token = tokens[t];
			if (token < 0) {
				int length = -token;
				int code = tokens[++t];
				int lengthPrefix = prefixOf(length);
				greenCode.write(out, 256 + lengthPrefix);
				out.put(extraValueOf(length), extraBitsOf(lengthPrefix));
				int distancePrefix = prefixOf(code);
				distanceCode.write(out, distancePrefix);
				out.put(extraValueOf(code), extraBitsOf(distancePrefix));
			} else {
				greenCode.write(out, (token >>> 8) & 0xFF);
				redCode.write(out, (token >>> 16) & 0xFF);
				blueCode.write(out, token & 0xFF);
				alphaCode.write(out, 0xFF);
			}
		}
		return out.toByteArray();
	}

	private static int matchLength(int[] pixels, int at, int from, int n) {
		int max = Math.min(MAX_MATCH, n - at);
		int k = 0;
		while (k < max && pixels[at + k] == pixels[from + k]) {
			k++;
		}
		return k;
	}

	/** The LZ77 prefix symbol for a length or distance code {@code value} (1-based), per the spec. */
	static int prefixOf(int value) {
		int v = value - 1;
		if (v < 4) {
			return v;
		}
		int highest = 31 - Integer.numberOfLeadingZeros(v);
		int second = (v >>> (highest - 1)) & 1;
		return 2 * highest + second;
	}

	static int extraBitsOf(int prefix) {
		return prefix < 4 ? 0 : (prefix - 2) >> 1;
	}

	static int extraValueOf(int value) {
		int v = value - 1;
		if (v < 4) {
			return 0;
		}
		int bits = (31 - Integer.numberOfLeadingZeros(v)) - 1;
		return v & ((1 << bits) - 1);
	}

	// --- prefix codes ---------------------------------------------------------------------------

	/** A canonical prefix code, ready to write symbols with. */
	static final class PrefixCode {
		final int[] lengths;
		final int[] reversedCodes;
		/** One symbol in use: the decoder spends no bits on it, so neither may the encoder. */
		final boolean singleSymbol;

		PrefixCode(int[] lengths) {
			this.lengths = lengths;
			this.reversedCodes = canonicalReversed(lengths);
			int used = 0;
			for (int length : lengths) {
				if (length > 0) {
					used++;
				}
			}
			this.singleSymbol = used <= 1;
		}

		void write(BitWriter out, int symbol) {
			if (!singleSymbol) {
				out.put(reversedCodes[symbol], lengths[symbol]);
			}
		}
	}

	/**
	 * Writes the code for one alphabet's histogram and returns it. An alphabet with at most two
	 * symbols, all below 256, uses the spec's "simple" form; anything else the normal form, whose
	 * code lengths are themselves prefix-coded.
	 */
	private static PrefixCode writeCode(BitWriter out, int[] histogram) {
		List<Integer> used = new ArrayList<>(2);
		for (int s = 0; s < histogram.length && used.size() <= 2; s++) {
			if (histogram[s] > 0) {
				used.add(s);
			}
		}
		if (used.size() <= 2 && used.stream().allMatch(s -> s < 256)) {
			int[] lengths = new int[histogram.length];
			int first = used.isEmpty() ? 0 : used.get(0);
			out.put(1, 1); // simple
			out.put(used.size() == 2 ? 1 : 0, 1); // number of symbols - 1
			if (first < 2) {
				out.put(0, 1);
				out.put(first, 1);
			} else {
				out.put(1, 1);
				out.put(first, 8);
			}
			lengths[first] = 1;
			if (used.size() == 2) {
				out.put(used.get(1), 8);
				lengths[used.get(1)] = 1;
			}
			return new PrefixCode(lengths);
		}

		int[] lengths = limitedLengths(histogram, MAX_CODE_LENGTH);
		out.put(0, 1); // normal

		// Code lengths as tokens: 0–15 literally, 17 and 18 for runs of zeros (16 is not used).
		List<int[]> tokens = new ArrayList<>();
		int[] lengthHistogram = new int[19];
		for (int s = 0; s < lengths.length; ) {
			if (lengths[s] == 0) {
				int run = 0;
				while (s + run < lengths.length && lengths[s + run] == 0 && run < 138) {
					run++;
				}
				if (run >= 11) {
					tokens.add(new int[] { 18, run - 11 });
					lengthHistogram[18]++;
					s += run;
					continue;
				}
				if (run >= 3) {
					tokens.add(new int[] { 17, run - 3 });
					lengthHistogram[17]++;
					s += run;
					continue;
				}
			}
			tokens.add(new int[] { lengths[s], 0 });
			lengthHistogram[lengths[s]]++;
			s++;
		}

		int[] lengthCodeLengths = limitedLengths(lengthHistogram, MAX_CODE_LENGTH_CODE_LENGTH);
		int count = CODE_LENGTH_ORDER.length;
		while (count > 4 && lengthCodeLengths[CODE_LENGTH_ORDER[count - 1]] == 0) {
			count--;
		}
		out.put(count - 4, 4);
		for (int i = 0; i < count; i++) {
			out.put(lengthCodeLengths[CODE_LENGTH_ORDER[i]], 3);
		}
		out.put(0, 1); // max_symbol: the whole alphabet

		PrefixCode lengthCode = new PrefixCode(lengthCodeLengths);
		for (int[] token : tokens) {
			lengthCode.write(out, token[0]);
			if (token[0] == 17) {
				out.put(token[1], 3);
			} else if (token[0] == 18) {
				out.put(token[1], 7);
			}
		}
		return new PrefixCode(lengths);
	}

	/**
	 * Huffman code lengths no longer than {@code limit}. When the optimal tree is too deep, rare
	 * symbols are counted as if they were more common, doubling the floor until it fits — the
	 * same approach libwebp takes, and slightly suboptimal only in that rare case.
	 */
	static int[] limitedLengths(int[] histogram, int limit) {
		int[] lengths = new int[histogram.length];
		int used = 0;
		int only = -1;
		for (int s = 0; s < histogram.length; s++) {
			if (histogram[s] > 0) {
				used++;
				only = s;
			}
		}
		if (used == 0) {
			return lengths;
		}
		if (used == 1) {
			lengths[only] = 1;
			return lengths;
		}
		for (long floor = 1; ; floor *= 2) {
			int[] depths = huffmanDepths(histogram, floor);
			if (Arrays.stream(depths).max().orElse(0) <= limit) {
				return depths;
			}
		}
	}

	private static int[] huffmanDepths(int[] histogram, long floor) {
		int symbols = histogram.length;
		// Nodes 0..symbols-1 are leaves; internal nodes follow. parent[] lets depth be read back.
		int[] parent = new int[symbols * 2];
		Arrays.fill(parent, -1);
		long[] weight = new long[symbols * 2];
		PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0])
				: Long.compare(a[1], b[1]));
		for (int s = 0; s < symbols; s++) {
			if (histogram[s] > 0) {
				weight[s] = Math.max(histogram[s], floor);
				queue.add(new long[] { weight[s], s });
			}
		}
		int next = symbols;
		while (queue.size() > 1) {
			long[] a = queue.poll();
			long[] b = queue.poll();
			parent[(int) a[1]] = next;
			parent[(int) b[1]] = next;
			weight[next] = a[0] + b[0];
			queue.add(new long[] { weight[next], next });
			next++;
		}
		int[] depths = new int[symbols];
		for (int s = 0; s < symbols; s++) {
			if (histogram[s] > 0) {
				int depth = 0;
				for (int node = s; parent[node] >= 0; node = parent[node]) {
					depth++;
				}
				depths[s] = depth;
			}
		}
		return depths;
	}

	/** Canonical codes (shorter first, then by symbol), bit-reversed because VP8L reads codes
	 * most-significant bit first out of a least-significant-first stream. */
	private static int[] canonicalReversed(int[] lengths) {
		int[] countPerLength = new int[MAX_CODE_LENGTH + 1];
		for (int length : lengths) {
			if (length > 0) {
				countPerLength[length]++;
			}
		}
		int[] nextCode = new int[MAX_CODE_LENGTH + 2];
		int code = 0;
		for (int bits = 1; bits <= MAX_CODE_LENGTH; bits++) {
			code = (code + countPerLength[bits - 1]) << 1;
			nextCode[bits] = code;
		}
		int[] reversed = new int[lengths.length];
		for (int s = 0; s < lengths.length; s++) {
			int length = lengths[s];
			if (length > 0) {
				reversed[s] = Integer.reverse(nextCode[length]++) >>> (32 - length);
			}
		}
		return reversed;
	}

	/** Bits packed least-significant first, as VP8L reads them. */
	static final class BitWriter {
		private byte[] buffer;
		private int position;
		private long accumulator;
		private int pending;

		BitWriter(int capacity) {
			buffer = new byte[Math.max(16, capacity)];
		}

		void put(int value, int bits) {
			if (bits == 0) {
				return;
			}
			accumulator |= ((long) value & ((1L << bits) - 1)) << pending;
			pending += bits;
			while (pending >= 8) {
				if (position == buffer.length) {
					buffer = Arrays.copyOf(buffer, buffer.length * 2);
				}
				buffer[position++] = (byte) accumulator;
				accumulator >>>= 8;
				pending -= 8;
			}
		}

		byte[] toByteArray() {
			byte[] out = Arrays.copyOf(buffer, position + (pending > 0 ? 1 : 0));
			if (pending > 0) {
				out[position] = (byte) accumulator;
			}
			return out;
		}
	}

	// --- RIFF -----------------------------------------------------------------------------------

	private static void writeChunk(ByteArrayOutputStream out, String fourCc, byte[] data) {
		out.writeBytes(ascii(fourCc));
		out.writeBytes(le32(data.length));
		out.writeBytes(data);
		if ((data.length & 1) != 0) {
			out.write(0); // RIFF pads every chunk to an even length
		}
	}

	private static byte[] ascii(String s) {
		return s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
	}

	private static byte[] le32(int value) {
		return new byte[] { (byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24) };
	}

	private static void put24(byte[] target, int at, int value) {
		target[at] = (byte) value;
		target[at + 1] = (byte) (value >>> 8);
		target[at + 2] = (byte) (value >>> 16);
	}
}
