package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * A scaled-down WebP of an exported animation, made on demand when the full-resolution sharing copy
 * is too big for Discord. Lossless coding cannot make a moving-camera burst much smaller than its
 * frames, so the only lossless lever left is fewer pixels: this re-renders the APNG's frames at a
 * smaller size, estimating the scale from the full copy's size and trying again, smaller, if the
 * estimate misses.
 *
 * <p>Written to a temporary folder under the animation's own name plus {@code _discord}, so an
 * upload or a saved copy carries a sensible file name, and deleted when the share popup closes.
 */
public final class SmallerShareCopy {

	/** Discord's upload limit without Nitro: 10 MB, counted in decimal to stay on the safe side. */
	public static final long DISCORD_LIMIT_BYTES = 10_000_000L;
	/** Aim this far under the limit, since the size is estimated before encoding. */
	static final double HEADROOM = 0.85;
	static final int MIN_WIDTH = 160;
	private static final int ATTEMPTS = 4;

	private SmallerShareCopy() {
	}

	public static boolean tooBigForDiscord(File file) {
		return file.isFile() && file.length() > DISCORD_LIMIT_BYTES;
	}

	/** {@code 12.3}: megabytes with one decimal, for the popup. */
	public static String megabytes(long bytes) {
		return String.format(Locale.ROOT, "%.1f", bytes / 1_000_000.0);
	}

	/**
	 * A WebP of {@code apng}'s frames that fits under {@code limitBytes}, in a new temp file, or an
	 * exception if even the smallest size tried does not fit.
	 *
	 * @param fullSize the full-resolution copy's size, to estimate the first scale from.
	 */
	public static File make(File apng, long fullSize, long limitBytes, AnimatedPng.PixelDecoder decoder)
			throws IOException {
		byte[] bytes = Files.readAllBytes(apng.toPath());
		List<AnimatedPng.Frame> frames = AnimatedPng.splitFrames(bytes);
		PngDimensions.Dimensions size = PngDimensions.read(apng);
		if (frames.isEmpty() || size == null) {
			throw new IOException(apng.getName() + " is not an animation this mod wrote");
		}

		double scale = scaleFor(fullSize, limitBytes);
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			int width = Math.min(size.width(), Math.max(MIN_WIDTH, evenRound(size.width() * scale)));
			int height = Math.max(2, evenRound(size.height() * (double) width / size.width()));
			byte[] webp = encode(frames, size.width(), size.height(), width, height, decoder);
			if (webp.length <= limitBytes) {
				return write(apng, webp);
			}
			if (width <= MIN_WIDTH) {
				break;
			}
			scale *= Math.sqrt(limitBytes * HEADROOM / webp.length);
		}
		throw new IOException("Could not fit " + apng.getName() + " under " + limitBytes + " bytes");
	}

	/** File size goes roughly with pixel count, so the side scales with the square root. */
	static double scaleFor(long fullSize, long limitBytes) {
		return Math.min(1.0, Math.sqrt(limitBytes * HEADROOM / Math.max(1, fullSize)));
	}

	private static int evenRound(double value) {
		return Math.max(2, (int) Math.floor(value / 2) * 2);
	}

	private static byte[] encode(List<AnimatedPng.Frame> frames, int fullWidth, int fullHeight, int width, int height,
			AnimatedPng.PixelDecoder decoder) throws IOException {
		int[] canvas = new int[fullWidth * fullHeight];
		AnimatedWebp webp = new AnimatedWebp(width, height);
		for (AnimatedPng.Frame frame : frames) {
			new FramePatch(frame.x(), frame.y(), frame.width(), frame.height(), decoder.argb(frame.png()))
					.composite(canvas, fullWidth, frame.blend());
			webp.frame(ArgbImages.downscale(canvas, fullWidth, fullHeight, width, height), frame.delayMillis());
		}
		return webp.finish();
	}

	private static File write(File apng, byte[] webp) throws IOException {
		String name = apng.getName();
		String base = name.toLowerCase(Locale.ROOT).endsWith(".png") ? name.substring(0, name.length() - 4) : name;
		Path dir = Files.createTempDirectory("voxelcam-share-");
		Path file = dir.resolve(base + "_discord.webp");
		Files.write(file, webp);
		dir.toFile().deleteOnExit();
		file.toFile().deleteOnExit();
		return file.toFile();
	}

	/** Removes a copy {@link #make} wrote, and the temp folder it sits in. */
	public static void discard(File copy) {
		if (copy == null) {
			return;
		}
		try {
			Files.deleteIfExists(copy.toPath());
			Files.deleteIfExists(copy.toPath().getParent());
		} catch (IOException ignored) {
			// deleteOnExit is the fallback.
		}
	}
}
