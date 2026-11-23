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
 * <p>Lossless (VP8L, RFC 9649), with the parts of the format that pay for themselves on
 * Minecraft footage:
 * <ul>
 *   <li><b>Only what changed.</b> After the first, each frame is a {@link FramePatch}: the
 *       rectangle that differs from the frame before, unchanged pixels transparent and blended
 *       over it.</li>
 *   <li><b>Subtract-green and predictor transforms.</b> Each 16x16 block uses whichever of the
 *       spec's fourteen predictors leaves the smallest residuals — a sky gradient becomes runs of
 *       near-zero.</li>
 *   <li><b>LZ77 with a hash chain</b>, plus the two short-distance codes for "left" and "above".</li>
 *   <li><b>A colour cache</b>, so a colour seen recently costs an index rather than four
 *       symbols.</li>
 * </ul>
 * One prefix-code group per image; meta prefix codes and the colour-indexing and cross-colour
 * transforms are left out.
 *
 * <p>Pixels are ARGB ints, the packing {@code NativeImage.getPixels()} returns. Alpha is forced
 * opaque on the way in: a screenshot is opaque, and transparency is reserved for "unchanged".
 */
public final class AnimatedWebp {

	/** VP8L stores each dimension minus one in 14 bits. */
	public static final int MAX_DIMENSION = 1 << 14;

	private record Frame(byte[] vp8l, int x, int y, int width, int height, int delayMillis, boolean blend) {
	}

	private final int width;
	private final int height;
	private final List<Frame> frames = new ArrayList<>();
	/** The canvas as the last frame left it, for the next frame's difference. */
	private int[] previous;

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
		int delay = Math.max(0, Math.min(0xFFFFFF, delayMillis));
		int[] current = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			current[i] = argb[i] | 0xFF000000;
		}
		boolean first = previous == null;
		FramePatch patch = first ? FramePatch.whole(current, width, height)
				: FramePatch.between(previous, current, width, height);
		frames.add(new Frame(encodeVp8l(patch.pixels(), patch.width(), patch.height()),
				patch.x(), patch.y(), patch.width(), patch.height(), delay, !first));
		previous = current;
	}

	/** The whole file: RIFF, then VP8X, ANIM, and one ANMF per frame, looping forever. */
	public byte[] finish() {
		if (frames.isEmpty()) {
			throw new IllegalStateException("An animation needs at least one frame");
		}
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.writeBytes(ascii("WEBP"));

		byte[] vp8x = new byte[10];
		// Animation, and alpha: the patches use transparency for "unchanged", even though the
		// composited canvas is always opaque.
		vp8x[0] = 0x02 | 0x10;
		put24(vp8x, 4, width - 1);
		put24(vp8x, 7, height - 1);
		writeChunk(body, "VP8X", vp8x);

		byte[] anim = new byte[6]; // background colour 0 (the first frame covers it), loop forever
		writeChunk(body, "ANIM", anim);

		for (Frame frame : frames) {
			ByteArrayOutputStream anmf = new ByteArrayOutputStream();
			byte[] header = new byte[16];
			put24(header, 0, frame.x() / 2);
			put24(header, 3, frame.y() / 2);
			put24(header, 6, frame.width() - 1);
			put24(header, 9, frame.height() - 1);
			put24(header, 12, frame.delayMillis());
			// Bit 1 set means "do not blend": the first frame replaces whatever is there; every
			// patch after it blends, so its transparent pixels leave the frame before showing.
			// Never dispose, so each patch lands on the canvas the last one left.
			header[15] = (byte) (frame.blend() ? 0x00 : 0x02);
			anmf.writeBytes(header);
			writeChunk(anmf, "VP8L", frame.vp8l());
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

	private static final int LITERAL_GREEN = 256;
	private static final int LENGTH_PREFIXES = 24;
	private static final int DISTANCE_ALPHABET = 40;
	private static final int MAX_CODE_LENGTH = 15;
	private static final int MAX_CODE_LENGTH_CODE_LENGTH = 7;
	private static final int MIN_MATCH = 3;
	private static final int MAX_MATCH = 4096;
	/** The largest distance a distance code can carry once the 120 short codes are skipped. */
	private static final int MAX_DISTANCE = (1 << 20) - 120;
	/** The VP8L distance codes for "the pixel above" and "the previous pixel" — the first two
	 * entries of the spec's 2D distance map, (0,1) and (1,0). Any other distance is sent as itself
	 * plus 120, which every decoder reads the same way, rather than through the rest of the map. */
	private static final int DISTANCE_CODE_ABOVE = 1;
	private static final int DISTANCE_CODE_PREVIOUS = 2;
	private static final int[] CODE_LENGTH_ORDER = { 17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };

	/** 16x16 blocks: one predictor mode per block, small enough to follow a horizon. */
	static final int PREDICTOR_BITS = 4;
	static final int PREDICTOR_MODES = 14;
	/** A 1024-colour cache. */
	static final int CACHE_BITS = 10;
	private static final int HASH_BITS = 16;
	/** How many earlier positions with the same two-pixel hash the match search tries. */
	private static final int CHAIN_LIMIT = 24;

	private static final int TRANSFORM_PREDICTOR = 0;
	private static final int TRANSFORM_SUBTRACT_GREEN = 2;

	/** Package-private seam: one image as a bare VP8L bitstream, the payload of a {@code VP8L} chunk. */
	static byte[] encodeVp8l(int[] argb, int width, int height) {
		int n = width * height;
		boolean alphaUsed = false;
		int[] pixels = new int[n];
		for (int i = 0; i < n; i++) {
			// Subtract green: red and blue usually track green, so the differences cluster near 0.
			int p = argb[i];
			alphaUsed |= (p >>> 24) != 0xFF;
			int g = (p >>> 8) & 0xFF;
			int r = (((p >>> 16) & 0xFF) - g) & 0xFF;
			int b = ((p & 0xFF) - g) & 0xFF;
			pixels[i] = (p & 0xFF00FF00) | (r << 16) | b;
		}

		BitWriter out = new BitWriter(n + 64);
		out.put(0x2F, 8);
		out.put(width - 1, 14);
		out.put(height - 1, 14);
		out.put(alphaUsed ? 1 : 0, 1);
		out.put(0, 3); // version

		// Transforms are written in the order they were applied; the decoder undoes them in
		// reverse, so it predicts first and adds green back last.
		out.put(1, 1);
		out.put(TRANSFORM_SUBTRACT_GREEN, 2);

		int[] modes = choosePredictors(pixels, width, height);
		int blocksWide = subSampled(width, PREDICTOR_BITS);
		int blocksHigh = subSampled(height, PREDICTOR_BITS);
		int[] modeImage = new int[modes.length];
		for (int i = 0; i < modes.length; i++) {
			modeImage[i] = 0xFF000000 | (modes[i] << 8);
		}
		out.put(1, 1);
		out.put(TRANSFORM_PREDICTOR, 2);
		out.put(PREDICTOR_BITS - 2, 3);
		writeImage(out, modeImage, blocksWide, false, 0);

		out.put(0, 1); // no more transforms
		writeImage(out, residuals(pixels, width, height, modes), width, true, CACHE_BITS);
		return out.toByteArray();
	}

	private static int subSampled(int size, int bits) {
		return (size + (1 << bits) - 1) >> bits;
	}

	// --- predictors -----------------------------------------------------------------------------

	/** Per block, the mode whose residuals have the smallest total magnitude. */
	static int[] choosePredictors(int[] pixels, int width, int height) {
		int blocksWide = subSampled(width, PREDICTOR_BITS);
		int blocksHigh = subSampled(height, PREDICTOR_BITS);
		int[] modes = new int[blocksWide * blocksHigh];
		int size = 1 << PREDICTOR_BITS;
		long[] cost = new long[PREDICTOR_MODES];
		for (int by = 0; by < blocksHigh; by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				Arrays.fill(cost, 0);
				int yEnd = Math.min(height, (by + 1) * size);
				int xEnd = Math.min(width, (bx + 1) * size);
				for (int y = by * size; y < yEnd; y++) {
					for (int x = bx * size; x < xEnd; x++) {
						if (x == 0 || y == 0) {
							continue; // the edges ignore the mode
						}
						int i = y * width + x;
						int pixel = pixels[i];
						for (int mode = 0; mode < PREDICTOR_MODES; mode++) {
							cost[mode] += magnitude(subtract(pixel, predict(mode, pixels, i, width)));
						}
					}
				}
				int best = 0;
				for (int mode = 1; mode < PREDICTOR_MODES; mode++) {
					if (cost[mode] < cost[best]) {
						best = mode;
					}
				}
				modes[by * blocksWide + bx] = best;
			}
		}
		return modes;
	}

	/** The image the decoder sees before it undoes the predictor: each pixel minus its prediction,
	 * with the spec's fixed rules for the first row and column. */
	static int[] residuals(int[] pixels, int width, int height, int[] modes) {
		int blocksWide = subSampled(width, PREDICTOR_BITS);
		int[] out = new int[pixels.length];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int i = y * width + x;
				int prediction;
				if (i == 0) {
					prediction = 0xFF000000;
				} else if (y == 0) {
					prediction = pixels[i - 1];
				} else if (x == 0) {
					prediction = pixels[i - width];
				} else {
					int mode = modes[(y >> PREDICTOR_BITS) * blocksWide + (x >> PREDICTOR_BITS)];
					prediction = predict(mode, pixels, i, width);
				}
				out[i] = subtract(pixels[i], prediction);
			}
		}
		return out;
	}

	/**
	 * The fourteen predictors of RFC 9649 §4.1, for a pixel not on the first row or column. TR of
	 * the rightmost column is the first pixel of the current row, which is what {@code i - width
	 * + 1} addresses there anyway.
	 */
	static int predict(int mode, int[] p, int i, int width) {
		int l = p[i - 1];
		int t = p[i - width];
		int tl = p[i - width - 1];
		int tr = p[i - width + 1];
		return switch (mode) {
			case 0 -> 0xFF000000;
			case 1 -> l;
			case 2 -> t;
			case 3 -> tr;
			case 4 -> tl;
			case 5 -> average2(average2(l, tr), t);
			case 6 -> average2(l, tl);
			case 7 -> average2(l, t);
			case 8 -> average2(tl, t);
			case 9 -> average2(t, tr);
			case 10 -> average2(average2(l, tl), average2(t, tr));
			case 11 -> select(l, t, tl);
			case 12 -> clampAddSubtractFull(l, t, tl);
			case 13 -> clampAddSubtractHalf(average2(l, t), tl);
			default -> throw new IllegalArgumentException("predictor mode " + mode);
		};
	}

	private static int average2(int a, int b) {
		return (((a ^ b) & 0xFEFEFEFE) >>> 1) + (a & b);
	}

	private static int select(int l, int t, int tl) {
		int pL = 0;
		int pT = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			int cl = (l >>> shift) & 0xFF;
			int ct = (t >>> shift) & 0xFF;
			int ctl = (tl >>> shift) & 0xFF;
			int estimate = cl + ct - ctl;
			pL += Math.abs(estimate - cl);
			pT += Math.abs(estimate - ct);
		}
		return pL < pT ? l : t;
	}

	private static int clampAddSubtractFull(int a, int b, int c) {
		int out = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			int v = ((a >>> shift) & 0xFF) + ((b >>> shift) & 0xFF) - ((c >>> shift) & 0xFF);
			out |= Math.clamp(v, 0, 255) << shift;
		}
		return out;
	}

	private static int clampAddSubtractHalf(int a, int b) {
		int out = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			out |= Math.clamp(ca + (ca - cb) / 2, 0, 255) << shift;
		}
		return out;
	}

	/** Per channel, {@code a - b} modulo 256. */
	private static int subtract(int a, int b) {
		int out = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			out |= ((((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF)) & 0xFF) << shift;
		}
		return out;
	}

	/** How far a residual is from zero, each channel read as a signed byte. */
	private static int magnitude(int residual) {
		return Math.abs((byte) residual) + Math.abs((byte) (residual >>> 8))
				+ Math.abs((byte) (residual >>> 16)) + Math.abs((byte) (residual >>> 24));
	}

	// --- entropy-coded images -------------------------------------------------------------------

	/**
	 * One image's colour-cache header, its five prefix codes and its LZ77 token stream. {@code
	 * main} adds the meta-prefix bit the spec gives only the ARGB image itself (always "none"
	 * here); the predictor's mode image has no such bit.
	 */
	private static void writeImage(BitWriter out, int[] pixels, int width, boolean main, int cacheBits) {
		int n = pixels.length;
		int cacheSize = cacheBits > 0 ? 1 << cacheBits : 0;
		if (cacheBits > 0) {
			out.put(1, 1);
			out.put(cacheBits, 4);
		} else {
			out.put(0, 1);
		}
		if (main) {
			out.put(0, 1); // no meta prefix codes: one group for the whole image
		}

		// Tokens, three ints each: kind, then two values. A literal is (pixel), a cache hit
		// (index), a copy (length, distance code).
		int[] kinds = new int[n];
		int[] first = new int[n];
		int[] second = new int[n];
		int count = 0;
		int[] green = new int[LITERAL_GREEN + LENGTH_PREFIXES + cacheSize];
		int[] red = new int[256];
		int[] blue = new int[256];
		int[] alpha = new int[256];
		int[] distance = new int[DISTANCE_ALPHABET];

		int[] cache = cacheSize > 0 ? new int[cacheSize] : null;
		int[] head = new int[1 << HASH_BITS];
		Arrays.fill(head, -1);
		int[] chain = new int[n];

		for (int i = 0; i < n; ) {
			int bestLength = 0;
			int bestDistance = 0;
			// The two cheapest distances first: they have codes of their own.
			if (i >= 1) {
				bestLength = matchLength(pixels, i, i - 1, n);
				bestDistance = 1;
			}
			if (i >= width) {
				int length = matchLength(pixels, i, i - width, n);
				if (length > bestLength) {
					bestLength = length;
					bestDistance = width;
				}
			}
			if (i + 1 < n && bestLength < MAX_MATCH) {
				int tries = 0;
				for (int candidate = head[hash(pixels, i)]; candidate >= 0 && tries < CHAIN_LIMIT;
						candidate = chain[candidate], tries++) {
					int d = i - candidate;
					if (d > MAX_DISTANCE) {
						break;
					}
					int length = matchLength(pixels, i, candidate, n);
					if (length > bestLength) {
						bestLength = length;
						bestDistance = d;
						if (length == MAX_MATCH) {
							break;
						}
					}
				}
			}

			int advance;
			if (bestLength >= MIN_MATCH) {
				int code = distanceCode(bestDistance, width);
				kinds[count] = 2;
				first[count] = bestLength;
				second[count++] = code;
				green[LITERAL_GREEN + prefixOf(bestLength)]++;
				distance[prefixOf(code)]++;
				advance = bestLength;
			} else {
				int pixel = pixels[i];
				int key = cache != null ? cacheKey(pixel, cacheBits) : -1;
				if (cache != null && cache[key] == pixel) {
					kinds[count] = 1;
					first[count++] = key;
					green[LITERAL_GREEN + LENGTH_PREFIXES + key]++;
				} else {
					kinds[count] = 0;
					first[count++] = pixel;
					green[(pixel >>> 8) & 0xFF]++;
					red[(pixel >>> 16) & 0xFF]++;
					blue[pixel & 0xFF]++;
					alpha[pixel >>> 24]++;
				}
				advance = 1;
			}
			// The decoder adds every pixel it produces to the cache, copied ones included, so
			// the encoder's cache has to see exactly the same sequence.
			for (int k = 0; k < advance; k++) {
				int at = i + k;
				if (cache != null) {
					cache[cacheKey(pixels[at], cacheBits)] = pixels[at];
				}
				if (at + 1 < n) {
					int h = hash(pixels, at);
					chain[at] = head[h];
					head[h] = at;
				}
			}
			i += advance;
		}

		PrefixCode greenCode = writeCode(out, green);
		PrefixCode redCode = writeCode(out, red);
		PrefixCode blueCode = writeCode(out, blue);
		PrefixCode alphaCode = writeCode(out, alpha);
		PrefixCode distanceCode = writeCode(out, distance);

		for (int t = 0; t < count; t++) {
			switch (kinds[t]) {
				case 0 -> {
					int pixel = first[t];
					greenCode.write(out, (pixel >>> 8) & 0xFF);
					redCode.write(out, (pixel >>> 16) & 0xFF);
					blueCode.write(out, pixel & 0xFF);
					alphaCode.write(out, pixel >>> 24);
				}
				case 1 -> greenCode.write(out, LITERAL_GREEN + LENGTH_PREFIXES + first[t]);
				default -> {
					int length = first[t];
					int code = second[t];
					int lengthPrefix = prefixOf(length);
					greenCode.write(out, LITERAL_GREEN + lengthPrefix);
					out.put(extraValueOf(length), extraBitsOf(lengthPrefix));
					int distancePrefix = prefixOf(code);
					distanceCode.write(out, distancePrefix);
					out.put(extraValueOf(code), extraBitsOf(distancePrefix));
				}
			}
		}
	}

	/** The spec's colour-cache hash: a multiplicative hash, top bits. */
	static int cacheKey(int argb, int cacheBits) {
		return (0x1E35A7BD * argb) >>> (32 - cacheBits);
	}

	private static int hash(int[] pixels, int i) {
		int h = pixels[i] * 0x9E3779B1 ^ pixels[i + 1] * 0x85EBCA77;
		return h >>> (32 - HASH_BITS);
	}

	/** Left and above have short codes of their own; everything else is its distance plus 120. */
	static int distanceCode(int distance, int width) {
		if (distance == 1) {
			return DISTANCE_CODE_PREVIOUS;
		}
		if (distance == width) {
			return DISTANCE_CODE_ABOVE;
		}
		return distance + 120;
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
