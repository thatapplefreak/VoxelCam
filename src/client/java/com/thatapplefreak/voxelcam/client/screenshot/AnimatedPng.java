package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Turns a burst that is already on disk into one looping APNG beside its key — harvest and
 * reframe, no decode and no re-encode. Every frame of a burst shares one IHDR, and a PNG's
 * {@code IDAT} payloads are just slices of one zlib stream, so each frame's compressed pixels
 * move across as they are: the first frame's {@code IDAT}s verbatim (making it the default
 * image every non-APNG reader shows), every later frame's as {@code fdAT}s, which are {@code
 * IDAT} payloads behind a sequence number.
 *
 * <p>Exported from the files rather than buffered while the burst runs, for three reasons the
 * frames themselves settle: their recorded {@link BurstFrame#offsetMillis()} is the only honest
 * frame timing (a burst is paced by its in-flight cap, not a timer), the frames finish writing
 * out of order while APNG's sequence numbers are strictly monotonic, and holding a burst's worth
 * of full-resolution {@code NativeImage}s off-heap is the very cost {@code Burst.MAX_IN_FLIGHT}
 * exists to bound. Read one frame at a time, the export holds one compressed PNG on the heap.
 *
 * <p>The result is an ordinary {@code .png}: browsers animate it, everything else shows the
 * first frame, and {@code PngDimensions}, the manager and the catbox upload all treat it as the
 * screenshot it also is.
 */
public final class AnimatedPng {

	/** How many frames the file holds; also what marks a file as an animation in the manager. */
	public static final String FRAMES_KEY = "voxelcam:animationframes";

	/** For frames that carry no offsets to time them by: hand-named {@code _burstNN} files. */
	static final int FALLBACK_DELAY_MILLIS = 50;
	/** Browsers treat a near-zero delay as "as fast as possible" or bump it to 100ms, depending on
	 * which; two frames captured on the same millisecond are held long enough to be seen instead. */
	static final int MIN_DELAY_MILLIS = 20;
	/** {@code fcTL}'s delay numerator is an unsigned 16-bit field, over a denominator of 1000. */
	static final int MAX_DELAY_MILLIS = 0xFFFF;

	private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
	private static final String SUFFIX = "_animation";

	private AnimatedPng() {
	}

	/**
	 * Writes the burst under {@code key} as a new APNG next to it, under a name no file has yet.
	 * Atomic: a refusal partway through (frames of different sizes, a frame that went missing)
	 * leaves nothing behind.
	 *
	 * @param frames the key's sub-frames, in any order; capture order comes from their tags.
	 * @return the file written.
	 */
	public static File export(File key, List<File> frames) throws IOException {
		List<File> files = new ArrayList<>(frames.size() + 1);
		files.add(key);
		files.addAll(frames);

		List<Map<String, String>> tags = new ArrayList<>(files.size());
		for (File file : files) {
			tags.add(PngTextChunk.read(file));
		}
		int[] order = captureOrder(files, tags);
		int[] offsets = offsetsInOrder(tags, order);
		int[] delays = offsets != null ? delaysFor(offsets) : uniformDelays(order.length);

		File target = targetFor(key);
		Path tmp = Files.createTempFile(key.toPath().toAbsolutePath().getParent(), "voxelcam-", ".png.tmp");
		try {
			try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp))) {
				Writer writer = null;
				for (int i = 0; i < order.length; i++) {
					byte[] png = Files.readAllBytes(files.get(order[i]).toPath());
					if (writer == null) {
						writer = new Writer(out, ihdrOf(png), order.length, carriedTags(tags.get(order[0]), order.length));
					}
					writer.frame(png, delays[i]);
				}
				writer.finish();
			}
			// No REPLACE_EXISTING: targetFor() picked a free name, and if something took it since,
			// failing is better than overwriting a file the player made.
			Files.move(tmp, target.toPath(), StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(tmp);
		}
		return target;
	}

	/**
	 * Package-private seam: pure bytes in, bytes out, as {@link PngTextChunk#embed(byte[], Map)}
	 * sits beside its file overload. Frames in display order, one delay each.
	 *
	 * @throws IOException if the frames do not all share one IHDR — a corrupt file is never the
	 * fallback.
	 */
	static byte[] assemble(List<byte[]> framePngs, int[] delayMillis, Map<String, String> tags) throws IOException {
		if (framePngs.isEmpty() || framePngs.size() != delayMillis.length) {
			throw new IllegalArgumentException(framePngs.size() + " frames, " + delayMillis.length + " delays");
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer writer = new Writer(out, ihdrOf(framePngs.get(0)), framePngs.size(), tags);
		for (int i = 0; i < framePngs.size(); i++) {
			writer.frame(framePngs.get(i), delayMillis[i]);
		}
		writer.finish();
		return out.toByteArray();
	}

	/**
	 * Indices into {@code files} in capture order: by each frame's embedded burst index when every
	 * frame has one — which, after a "Set as key" swap, is not filename order — else by name with
	 * the key first, the only order an untagged group has.
	 */
	static int[] captureOrder(List<File> files, List<Map<String, String>> tags) {
		Integer[] order = new Integer[files.size()];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		if (allTagged(tags)) {
			Arrays.sort(order, Comparator.comparingInt(i -> BurstFrame.fromTags(tags.get(i)).index()));
		} else {
			// The key stays first; its frames' names already sort by their _burstNN suffix.
			Arrays.sort(order, 1, order.length, Comparator.comparing(i -> files.get(i).getName()));
		}
		return Arrays.stream(order).mapToInt(Integer::intValue).toArray();
	}

	/** Each frame's offset from the key, in display order, or null if any frame is untagged. */
	private static int[] offsetsInOrder(List<Map<String, String>> tags, int[] order) {
		if (!allTagged(tags)) {
			return null;
		}
		int[] offsets = new int[order.length];
		for (int i = 0; i < order.length; i++) {
			offsets[i] = BurstFrame.fromTags(tags.get(order[i])).offsetMillis();
		}
		return offsets;
	}

	private static boolean allTagged(List<Map<String, String>> tags) {
		return tags.stream().allMatch(t -> BurstFrame.fromTags(t) != null);
	}

	/**
	 * How long each frame stays up: until the next frame was captured, so the animation plays at
	 * the speed it happened. The last frame has no next one and is held for the average gap before
	 * the loop starts over, so the seam is not a stutter.
	 *
	 * @param offsets capture offsets from the key, in display order.
	 */
	static int[] delaysFor(int[] offsets) {
		if (offsets.length < 2) {
			return uniformDelays(offsets.length);
		}
		int[] delays = new int[offsets.length];
		for (int i = 0; i < offsets.length - 1; i++) {
			delays[i] = clampDelay(offsets[i + 1] - offsets[i]);
		}
		long span = (long) offsets[offsets.length - 1] - offsets[0];
		delays[offsets.length - 1] = clampDelay(Math.round((double) span / (offsets.length - 1)));
		return delays;
	}

	private static int[] uniformDelays(int count) {
		int[] delays = new int[count];
		Arrays.fill(delays, FALLBACK_DELAY_MILLIS);
		return delays;
	}

	private static int clampDelay(long millis) {
		return (int) Math.max(MIN_DELAY_MILLIS, Math.min(MAX_DELAY_MILLIS, millis));
	}

	/**
	 * The key frame's capture context and provenance travel with the animation, so it can say
	 * where it was filmed the way its frames can. Its burst position and starred flag do not: the
	 * animation is not a frame of anything, and starring a frame is not starring its export.
	 */
	static Map<String, String> carriedTags(Map<String, String> first, int frameCount) {
		Map<String, String> carried = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : first.entrySet()) {
			String key = entry.getKey();
			if (!key.startsWith("voxelcam:burst") && !Favorite.STARRED_KEY.equals(key) && !FRAMES_KEY.equals(key)) {
				carried.put(key, entry.getValue());
			}
		}
		carried.put(FRAMES_KEY, Integer.toString(frameCount));
		return carried;
	}

	/** {@code holiday.png} → {@code holiday_animation.png}, then {@code _animation_2} and up. */
	static File targetFor(File key) {
		String name = key.getName();
		String base = name.toLowerCase(Locale.ROOT).endsWith(".png") ? name.substring(0, name.length() - 4) : name;
		File dir = key.getAbsoluteFile().getParentFile();
		File candidate = new File(dir, base + SUFFIX + ".png");
		for (int n = 2; candidate.exists(); n++) {
			candidate = new File(dir, base + SUFFIX + "_" + n + ".png");
		}
		return candidate;
	}

	/** The frame count an exported animation recorded, or 0 for anything else. */
	public static int frameCount(Map<String, String> tags) {
		try {
			return Math.max(0, Integer.parseInt(tags.getOrDefault(FRAMES_KEY, "0")));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static byte[] ihdrOf(byte[] png) throws IOException {
		requireSignature(png);
		if (png.length < SIGNATURE.length + 8 + 13 || readInt(png, SIGNATURE.length) != 13
				|| !"IHDR".equals(type(png, SIGNATURE.length))) {
			throw new IOException("Not a PNG: no IHDR first");
		}
		return Arrays.copyOfRange(png, SIGNATURE.length + 8, SIGNATURE.length + 8 + 13);
	}

	private static void requireSignature(byte[] png) throws IOException {
		if (png.length < SIGNATURE.length || !Arrays.equals(png, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length)) {
			throw new IOException("Not a PNG: bad signature");
		}
	}

	/**
	 * Streams the chunks in the order the APNG spec fixes: IHDR, then {@code acTL} and every
	 * other ancillary chunk ahead of the first {@code IDAT}, then per frame an {@code fcTL}
	 * followed by its image data, then IEND. Frame 0's {@code fcTL} comes before its {@code
	 * IDAT}, which is what makes the default image the animation's first frame rather than a
	 * still poster outside the count. {@code fcTL} and {@code fdAT} share one sequence counter.
	 */
	private static final class Writer {

		private final OutputStream out;
		private final byte[] ihdr;
		private final int frameCount;
		private int framesWritten;
		private int sequence;

		Writer(OutputStream out, byte[] ihdr, int frameCount, Map<String, String> tags) throws IOException {
			this.out = out;
			this.ihdr = ihdr;
			this.frameCount = frameCount;
			out.write(SIGNATURE);
			writeChunk("IHDR", ihdr);

			byte[] actl = new byte[8];
			putInt(actl, 0, frameCount);
			putInt(actl, 4, 0); // num_plays: 0 loops forever
			writeChunk("acTL", actl);
			for (Map.Entry<String, String> entry : tags.entrySet()) {
				out.write(PngTextChunk.iTXtChunk(entry.getKey(), entry.getValue()));
			}
		}

		void frame(byte[] png, int delayMillis) throws IOException {
			if (framesWritten == frameCount) {
				throw new IllegalStateException("acTL promised " + frameCount + " frames");
			}
			if (!Arrays.equals(ihdrOf(png), ihdr)) {
				throw new IOException("Frame " + framesWritten + " is not the same size or format as the first");
			}
			int width = readInt(ihdr, 0);
			int height = readInt(ihdr, 4);

			byte[] fctl = new byte[26];
			putInt(fctl, 0, sequence++);
			putInt(fctl, 4, width);
			putInt(fctl, 8, height);
			putInt(fctl, 12, 0); // x_offset
			putInt(fctl, 16, 0); // y_offset
			putShort(fctl, 20, delayMillis);
			putShort(fctl, 22, 1000);
			fctl[24] = 0; // dispose_op NONE: the next frame covers this one completely anyway
			fctl[25] = 0; // blend_op SOURCE: replace, never composite over the previous frame
			writeChunk("fcTL", fctl);

			boolean sawData = false;
			int pos = SIGNATURE.length;
			while (pos + 12 <= png.length) {
				int length = readInt(png, pos);
				long end = (long) pos + 12 + length;
				if (length < 0 || end > png.length) {
					throw new IOException("Frame " + framesWritten + " is truncated");
				}
				String type = type(png, pos);
				if ("IDAT".equals(type)) {
					sawData = true;
					if (framesWritten == 0) {
						out.write(png, pos, (int) (end - pos));
					} else {
						byte[] fdat = new byte[4 + length];
						putInt(fdat, 0, sequence++);
						System.arraycopy(png, pos + 8, fdat, 4, length);
						writeChunk("fdAT", fdat);
					}
				} else if ("IEND".equals(type)) {
					break;
				}
				pos = (int) end;
			}
			if (!sawData) {
				throw new IOException("Frame " + framesWritten + " has no image data");
			}
			framesWritten++;
		}

		void finish() throws IOException {
			if (framesWritten != frameCount) {
				throw new IllegalStateException("acTL promised " + frameCount + " frames, got " + framesWritten);
			}
			writeChunk("IEND", new byte[0]);
		}

		private void writeChunk(String type, byte[] data) throws IOException {
			byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
			byte[] length = new byte[4];
			putInt(length, 0, data.length);
			CRC32 crc = new CRC32();
			crc.update(typeBytes);
			crc.update(data);
			byte[] crcBytes = new byte[4];
			putInt(crcBytes, 0, (int) crc.getValue());
			out.write(length);
			out.write(typeBytes);
			out.write(data);
			out.write(crcBytes);
		}
	}

	private static String type(byte[] png, int chunkStart) {
		return new String(png, chunkStart + 4, 4, StandardCharsets.US_ASCII);
	}

	private static int readInt(byte[] data, int at) {
		return ((data[at] & 0xFF) << 24) | ((data[at + 1] & 0xFF) << 16)
				| ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
	}

	private static void putInt(byte[] target, int at, int value) {
		target[at] = (byte) (value >>> 24);
		target[at + 1] = (byte) (value >>> 16);
		target[at + 2] = (byte) (value >>> 8);
		target[at + 3] = (byte) value;
	}

	private static void putShort(byte[] target, int at, int value) {
		target[at] = (byte) (value >>> 8);
		target[at + 1] = (byte) value;
	}
}
