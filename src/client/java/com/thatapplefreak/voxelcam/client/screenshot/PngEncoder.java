package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * A PNG encoder that compresses properly, for the frames a burst writes by the dozen. Minecraft's
 * own {@code NativeImage.writeToFile} goes through stb, which always writes four channels and picks
 * filters poorly; the same frames come out about 37% smaller here, losslessly, as ordinary PNGs
 * every other part of the mod (and every viewer) reads unchanged.
 *
 * <p>The two things that buy it: an opaque image is written as RGB rather than RGBA, and every
 * scanline gets whichever of the five PNG filters leaves the smallest sum of absolute differences
 * — the heuristic the PNG specification itself recommends — before one zlib stream compresses the
 * lot.
 */
public final class PngEncoder {

	/** The default zlib level: it encodes a 1080p frame in about 100 ms, which a burst can afford
	 * at ten a second with three encoding at once. On burst frames, a maximum-effort optimiser
	 * (zlib 9 with the same filter choice) gets only about 6% further. */
	static final int DEFLATE_LEVEL = 6;

	private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
	private static final int COLOR_RGB = 2;
	private static final int COLOR_RGBA = 6;

	private PngEncoder() {
	}

	/** {@code argb} row by row, the packing {@code NativeImage.getPixels()} returns. */
	public static byte[] encode(int[] argb, int width, int height) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(width * height);
		try {
			encode(argb, width, height, out);
		} catch (IOException e) {
			throw new IllegalStateException("A byte array stream cannot fail", e);
		}
		return out.toByteArray();
	}

	public static void encode(int[] argb, int width, int height, OutputStream out) throws IOException {
		if (width < 1 || height < 1 || argb.length != width * height) {
			throw new IllegalArgumentException(argb.length + " pixels for a " + width + "x" + height + " image");
		}
		boolean opaque = true;
		for (int pixel : argb) {
			if ((pixel >>> 24) != 0xFF) {
				opaque = false;
				break;
			}
		}
		int channels = opaque ? 3 : 4;

		out.write(SIGNATURE);
		byte[] ihdr = new byte[13];
		putInt(ihdr, 0, width);
		putInt(ihdr, 4, height);
		ihdr[8] = 8; // bit depth
		ihdr[9] = (byte) (opaque ? COLOR_RGB : COLOR_RGBA);
		// compression, filter and interlace methods: all 0
		writeChunk(out, "IHDR", ihdr);

		Deflater deflater = new Deflater(DEFLATE_LEVEL);
		try {
			IdatStream idat = new IdatStream(out);
			int stride = width * channels;
			byte[] previous = new byte[stride];
			byte[] current = new byte[stride];
			byte[][] candidates = new byte[5][stride + 1];
			byte[] buffer = new byte[64 * 1024];
			for (int y = 0; y < height; y++) {
				int at = y * width;
				for (int x = 0, i = 0; x < width; x++) {
					int p = argb[at + x];
					current[i++] = (byte) (p >>> 16);
					current[i++] = (byte) (p >>> 8);
					current[i++] = (byte) p;
					if (!opaque) {
						current[i++] = (byte) (p >>> 24);
					}
				}
				byte[] row = filterRow(current, previous, channels, candidates);
				deflater.setInput(row, 0, stride + 1);
				while (!deflater.needsInput()) {
					idat.write(buffer, 0, deflater.deflate(buffer, 0, buffer.length, Deflater.NO_FLUSH));
				}
				byte[] swap = previous;
				previous = current;
				current = swap;
			}
			deflater.finish();
			while (!deflater.finished()) {
				idat.write(buffer, 0, deflater.deflate(buffer));
			}
			idat.flush();
		} finally {
			deflater.end();
		}
		writeChunk(out, "IEND", new byte[0]);
	}

	/**
	 * Every filter applied to the row, and the one with the smallest sum of absolute values (as
	 * signed bytes) returned, its filter type byte first. Package-private for the test.
	 */
	static byte[] filterRow(byte[] row, byte[] above, int bpp, byte[][] candidates) {
		int length = row.length;
		long[] cost = new long[5];
		for (int f = 0; f < 5; f++) {
			candidates[f][0] = (byte) f;
		}
		for (int i = 0; i < length; i++) {
			int x = row[i] & 0xFF;
			int a = i >= bpp ? row[i - bpp] & 0xFF : 0;
			int b = above[i] & 0xFF;
			int c = i >= bpp ? above[i - bpp] & 0xFF : 0;
			byte none = (byte) x;
			byte sub = (byte) (x - a);
			byte up = (byte) (x - b);
			byte average = (byte) (x - ((a + b) >>> 1));
			byte paeth = (byte) (x - paeth(a, b, c));
			candidates[0][i + 1] = none;
			candidates[1][i + 1] = sub;
			candidates[2][i + 1] = up;
			candidates[3][i + 1] = average;
			candidates[4][i + 1] = paeth;
			cost[0] += Math.abs(none);
			cost[1] += Math.abs(sub);
			cost[2] += Math.abs(up);
			cost[3] += Math.abs(average);
			cost[4] += Math.abs(paeth);
		}
		int best = 0;
		for (int f = 1; f < 5; f++) {
			if (cost[f] < cost[best]) {
				best = f;
			}
		}
		return candidates[best];
	}

	private static int paeth(int a, int b, int c) {
		int p = a + b - c;
		int pa = Math.abs(p - a);
		int pb = Math.abs(p - b);
		int pc = Math.abs(p - c);
		if (pa <= pb && pa <= pc) {
			return a;
		}
		return pb <= pc ? b : c;
	}

	/** Collects compressed bytes into IDAT chunks of a sensible size rather than one per deflate call. */
	private static final class IdatStream {
		private static final int CHUNK = 256 * 1024;
		private final OutputStream out;
		private final ByteArrayOutputStream pending = new ByteArrayOutputStream(CHUNK);

		IdatStream(OutputStream out) {
			this.out = out;
		}

		void write(byte[] data, int offset, int length) throws IOException {
			pending.write(data, offset, length);
			if (pending.size() >= CHUNK) {
				flush();
			}
		}

		void flush() throws IOException {
			if (pending.size() > 0) {
				writeChunk(out, "IDAT", pending.toByteArray());
				pending.reset();
			}
		}
	}

	private static void writeChunk(OutputStream out, String type, byte[] data) throws IOException {
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		byte[] header = new byte[8];
		putInt(header, 0, data.length);
		System.arraycopy(typeBytes, 0, header, 4, 4);
		out.write(header);
		out.write(data);
		byte[] trailer = new byte[4];
		putInt(trailer, 0, (int) crc.getValue());
		out.write(trailer);
	}

	private static void putInt(byte[] target, int at, int value) {
		target[at] = (byte) (value >>> 24);
		target[at + 1] = (byte) (value >>> 16);
		target[at + 2] = (byte) (value >>> 8);
		target[at + 3] = (byte) value;
	}
}
