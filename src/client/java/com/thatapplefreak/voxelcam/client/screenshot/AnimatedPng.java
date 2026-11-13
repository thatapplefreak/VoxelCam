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
 * Turns a burst that is already on disk into one looping APNG beside its key. The first frame is
 * stored whole, as the {@code IDAT} every non-APNG reader shows; every later frame is only a
 * {@link FramePatch} — the rectangle that changed since the frame before, unchanged pixels inside
 * it transparent and blended over — in {@code fdAT}s. A burst's frames are a tenth of a second
 * apart, so a still scene costs little more than its first frame. The patches are RGBA, so the
 * whole file is (IHDR is shared); the first frame's alpha is all 255 and compresses to nothing.
 *
 * <p>Exported from the files rather than buffered while the burst runs, for three reasons the
 * frames themselves settle: their recorded {@link BurstFrame#offsetMillis()} is the only honest
 * frame timing (a burst skips any slot its in-flight cap cannot make), the frames finish writing
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

	/** Turns one frame's PNG bytes into ARGB pixels, row by row — {@code NativeImage} in the game,
	 * the JDK's decoder in tests, so this class never needs a running client. */
	@FunctionalInterface
	public interface PixelDecoder {
		int[] argb(byte[] png) throws IOException;
	}

	/** What an export wrote: the APNG the manager lists, and the WebP beside it for sharing — null
	 * only for a burst too large for WebP's 16384-pixel limit. */
	public record Exported(File apng, File webp) {
	}

	/**
	 * Writes the burst under {@code key} as a new APNG next to it, and an animated WebP of the
	 * same frames beside that ({@link #webpBeside}): the APNG is what the manager lists and plays,
	 * the WebP is what Share hands out, since Discord plays a WebP and shows an APNG as a still.
	 * Both go under names no file has yet, and atomically: a refusal partway through (frames of
	 * different sizes, a frame that went missing) leaves neither behind. Every frame is decoded
	 * once, one at a time, and fed to both encoders.
	 *
	 * @param frames the key's sub-frames, in any order; capture order comes from their tags.
	 */
	public static Exported export(File key, List<File> frames, PixelDecoder decoder) throws IOException {
		return export(key, frames, decoder, (done, total) -> {
		});
	}

	/** Hears how far an export has got, from the thread doing it, after each frame. */
	@FunctionalInterface
	public interface Progress {
		void framesDone(int done, int total);
	}

	/** {@link #export(File, List, PixelDecoder)}, reporting each finished frame to {@code progress}. */
	public static Exported export(File key, List<File> frames, PixelDecoder decoder, Progress progress)
			throws IOException {
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
		File webpTarget = webpBeside(target);
		Path dir = key.toPath().toAbsolutePath().getParent();
		Path tmp = Files.createTempFile(dir, "voxelcam-", ".png.tmp");
		Path webpTmp = Files.createTempFile(dir, "voxelcam-", ".webp.tmp");
		boolean webpWritten = false;
		try {
			try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp))) {
				Writer writer = null;
				AnimatedWebp webp = null;
				for (int i = 0; i < order.length; i++) {
					byte[] png = Files.readAllBytes(files.get(order[i]).toPath());
					byte[] ihdr = ihdrOf(png);
					int width = readInt(ihdr, 0);
					int height = readInt(ihdr, 4);
					if (writer == null) {
						writer = new Writer(out, width, height, order.length,
								carriedTags(tags.get(order[0]), order.length));
						if (width <= AnimatedWebp.MAX_DIMENSION && height <= AnimatedWebp.MAX_DIMENSION) {
							webp = new AnimatedWebp(width, height);
						}
					}
					int[] pixels = decoder.argb(png);
					writer.frame(pixels, delays[i]);
					if (webp != null) {
						webp.frame(pixels, delays[i]);
					}
					progress.framesDone(i + 1, order.length);
				}
				writer.finish();
				if (webp != null) {
					Files.write(webpTmp, webp.finish());
					webpWritten = true;
				}
			}
			// No REPLACE_EXISTING: targetFor() picked names that were free, and if something took
			// one since, failing is better than overwriting a file the player made. The WebP goes
			// first so that a failure leaves no APNG claiming a sharing copy it does not have.
			if (webpWritten) {
				Files.move(webpTmp, webpTarget.toPath(), StandardCopyOption.ATOMIC_MOVE);
			}
			try {
				Files.move(tmp, target.toPath(), StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				if (webpWritten) {
					Files.deleteIfExists(webpTarget.toPath());
				}
				throw e;
			}
		} finally {
			Files.deleteIfExists(tmp);
			Files.deleteIfExists(webpTmp);
		}
		return new Exported(target, webpWritten ? webpTarget : null);
	}

	/**
	 * The WebP sharing copy that belongs to an exported animation: the same name, {@code .webp}.
	 * Rename, delete and Share all look for it here, so it travels with its APNG.
	 */
	public static File webpBeside(File png) {
		String name = png.getName();
		String base = name.toLowerCase(Locale.ROOT).endsWith(".png") ? name.substring(0, name.length() - 4) : name;
		return new File(png.getAbsoluteFile().getParentFile(), base + ".webp");
	}

	/**
	 * Package-private seam: pixels in, bytes out, no files. Frames in display order, ARGB, one
	 * delay each.
	 *
	 * @throws IOException if a frame is not the canvas's size — a corrupt file is never the
	 * fallback.
	 */
	static byte[] assemble(List<int[]> frames, int width, int height, int[] delayMillis, Map<String, String> tags)
			throws IOException {
		if (frames.isEmpty() || frames.size() != delayMillis.length) {
			throw new IllegalArgumentException(frames.size() + " frames, " + delayMillis.length + " delays");
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer writer = new Writer(out, width, height, frames.size(), tags);
		for (int i = 0; i < frames.size(); i++) {
			writer.frame(frames.get(i), delayMillis[i]);
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

	/** {@code holiday.png} → {@code holiday_animation.png}, then {@code _animation_2} and up — the
	 * first name free for both the APNG and its WebP. */
	static File targetFor(File key) {
		String name = key.getName();
		String base = name.toLowerCase(Locale.ROOT).endsWith(".png") ? name.substring(0, name.length() - 4) : name;
		File dir = key.getAbsoluteFile().getParentFile();
		File candidate = new File(dir, base + SUFFIX + ".png");
		for (int n = 2; candidate.exists() || webpBeside(candidate).exists(); n++) {
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

	/**
	 * One frame of an animation as a standalone PNG any decoder can read, where on the canvas it
	 * goes, whether it blends over what is there, and how long it shows.
	 */
	public record Frame(byte[] png, int x, int y, int width, int height, boolean blend, int delayMillis) {
	}

	/**
	 * The reverse of {@link #assemble}: every frame of an APNG rebuilt as a plain PNG — the
	 * shared IHDR resized to the frame's rectangle, its data as one {@code IDAT}, IEND — so {@code
	 * NativeImage}, which only ever decodes the default image, can read each one. Pure bytes, no
	 * decode; the caller composites (see {@link FramePatch#composite}).
	 *
	 * <p>Frames are played as long as they never dispose (the only disposal this class writes):
	 * every patch lands on the canvas the last one left. Anything else — a foreign APNG that
	 * disposes, a rectangle off the canvas, something that is not an APNG at all — returns an
	 * empty list, and the caller shows the still it would have shown anyway.
	 */
	public static List<Frame> splitFrames(byte[] apng) {
		try {
			byte[] ihdr = ihdrOf(apng);
			int width = readInt(ihdr, 0);
			int height = readInt(ihdr, 4);

			List<Frame> frames = new ArrayList<>();
			ByteArrayOutputStream data = null;
			int delay = 0;
			int[] rect = null;
			boolean blend = false;
			boolean animated = false;
			int pos = SIGNATURE.length;
			while (pos + 12 <= apng.length) {
				int length = readInt(apng, pos);
				long end = (long) pos + 12 + length;
				if (length < 0 || end > apng.length) {
					return List.of();
				}
				String type = type(apng, pos);
				int dataStart = pos + 8;
				switch (type) {
					case "acTL" -> animated = true;
					case "fcTL" -> {
						if (length != 26) {
							return List.of();
						}
						int w = readInt(apng, dataStart + 4);
						int h = readInt(apng, dataStart + 8);
						int x = readInt(apng, dataStart + 12);
						int y = readInt(apng, dataStart + 16);
						if (w < 1 || h < 1 || x < 0 || y < 0 || (long) x + w > width || (long) y + h > height
								|| apng[dataStart + 24] != 0 || apng[dataStart + 25] > 1 || apng[dataStart + 25] < 0) {
							return List.of();
						}
						if (data != null) {
							frames.add(frame(ihdr, data, rect, blend, delay));
						}
						data = new ByteArrayOutputStream();
						rect = new int[] { x, y, w, h };
						blend = apng[dataStart + 25] == 1;
						delay = delayMillis(readShort(apng, dataStart + 20), readShort(apng, dataStart + 22));
					}
					// An IDAT before any fcTL is a poster outside the animation; it is skipped.
					case "IDAT" -> {
						if (data != null) {
							data.write(apng, dataStart, length);
						}
					}
					case "fdAT" -> {
						if (data == null || length < 4) {
							return List.of();
						}
						data.write(apng, dataStart + 4, length - 4);
					}
					default -> {
					}
				}
				if ("IEND".equals(type)) {
					break;
				}
				pos = (int) end;
			}
			if (!animated || data == null) {
				return List.of();
			}
			frames.add(frame(ihdr, data, rect, blend, delay));
			return frames;
		} catch (IOException e) {
			return List.of();
		}
	}

	/** {@code fcTL}'s fraction in milliseconds; a zero denominator means hundredths, per the spec. */
	private static int delayMillis(int numerator, int denominator) {
		int den = denominator == 0 ? 100 : denominator;
		return clampDelay(Math.round(numerator * 1000.0 / den));
	}

	private static Frame frame(byte[] canvasIhdr, ByteArrayOutputStream data, int[] rect, boolean blend, int delay) {
		byte[] ihdr = canvasIhdr.clone();
		putInt(ihdr, 0, rect[2]);
		putInt(ihdr, 4, rect[3]);
		return new Frame(standalone(ihdr, data.toByteArray()), rect[0], rect[1], rect[2], rect[3], blend, delay);
	}

	private static byte[] standalone(byte[] ihdr, byte[] zlib) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(zlib.length + 64);
		out.writeBytes(SIGNATURE);
		out.writeBytes(chunk("IHDR", ihdr));
		out.writeBytes(chunk("IDAT", zlib));
		out.writeBytes(chunk("IEND", new byte[0]));
		return out.toByteArray();
	}

	private static byte[] chunk(String type, byte[] data) {
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		byte[] out = new byte[12 + data.length];
		putInt(out, 0, data.length);
		System.arraycopy(typeBytes, 0, out, 4, 4);
		System.arraycopy(data, 0, out, 8, data.length);
		putInt(out, 8 + data.length, (int) crc.getValue());
		return out;
	}

	private static int readShort(byte[] data, int at) {
		return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
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

		/** Compressed data is cut into chunks of about this size, as a PNG's IDATs usually are. */
		private static final int CHUNK_BYTES = 256 * 1024;

		private final OutputStream out;
		private final int width;
		private final int height;
		private final int frameCount;
		private int framesWritten;
		private int sequence;
		private int[] previous;

		Writer(OutputStream out, int width, int height, int frameCount, Map<String, String> tags) throws IOException {
			this.out = out;
			this.width = width;
			this.height = height;
			this.frameCount = frameCount;
			out.write(SIGNATURE);
			writeChunk("IHDR", PngEncoder.ihdr(width, height, true));

			byte[] actl = new byte[8];
			putInt(actl, 0, frameCount);
			putInt(actl, 4, 0); // num_plays: 0 loops forever
			writeChunk("acTL", actl);
			for (Map.Entry<String, String> entry : tags.entrySet()) {
				out.write(PngTextChunk.iTXtChunk(entry.getKey(), entry.getValue()));
			}
		}

		void frame(int[] argb, int delayMillis) throws IOException {
			if (framesWritten == frameCount) {
				throw new IllegalStateException("acTL promised " + frameCount + " frames");
			}
			if (argb.length != width * height) {
				throw new IOException("Frame " + framesWritten + " is not the same size as the first");
			}
			int[] current = new int[argb.length];
			for (int i = 0; i < argb.length; i++) {
				current[i] = argb[i] | 0xFF000000;
			}
			boolean first = previous == null;
			FramePatch patch = first ? FramePatch.whole(current, width, height)
					: FramePatch.between(previous, current, width, height);

			byte[] fctl = new byte[26];
			putInt(fctl, 0, sequence++);
			putInt(fctl, 4, patch.width());
			putInt(fctl, 8, patch.height());
			putInt(fctl, 12, patch.x());
			putInt(fctl, 16, patch.y());
			putShort(fctl, 20, delayMillis);
			putShort(fctl, 22, 1000);
			fctl[24] = 0; // dispose_op NONE: each patch lands on the canvas the last one left
			// blend_op: SOURCE for the first frame, which replaces whatever is there; OVER for every
			// patch after it, so its transparent pixels leave the frame before showing.
			fctl[25] = (byte) (first ? 0 : 1);
			writeChunk("fcTL", fctl);

			ByteArrayOutputStream pending = new ByteArrayOutputStream(CHUNK_BYTES);
			PngEncoder.compressRows(patch.pixels(), patch.width(), patch.height(), true, (data, offset, length) -> {
				pending.write(data, offset, length);
				if (pending.size() >= CHUNK_BYTES) {
					writeData(pending.toByteArray(), first);
					pending.reset();
				}
			});
			if (pending.size() > 0) {
				writeData(pending.toByteArray(), first);
			}
			previous = current;
			framesWritten++;
		}

		/** Frame 0's data is plain IDAT; every later frame's is fdAT, behind a sequence number. */
		private void writeData(byte[] zlib, boolean first) throws IOException {
			if (first) {
				writeChunk("IDAT", zlib);
				return;
			}
			byte[] fdat = new byte[4 + zlib.length];
			putInt(fdat, 0, sequence++);
			System.arraycopy(zlib, 0, fdat, 4, zlib.length);
			writeChunk("fdAT", fdat);
		}

		void finish() throws IOException {
			if (framesWritten != frameCount) {
				throw new IllegalStateException("acTL promised " + frameCount + " frames, got " + framesWritten);
			}
			writeChunk("IEND", new byte[0]);
		}

		private void writeChunk(String type, byte[] data) throws IOException {
			out.write(chunk(type, data));
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
