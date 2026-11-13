package com.thatapplefreak.voxelcam.client.screenshot;

import com.mojang.blaze3d.platform.NativeImage;
import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Plays an exported burst animation in the manager's preview. {@link ScreenshotImageCache} decodes
 * through {@code NativeImage}, which, like every PNG decoder that does not know APNG, sees only the
 * first frame; this splits the file back into its frames ({@link AnimatedPng#splitFrames}),
 * decodes each patch, composites it onto the canvas, and hands out whichever frame is due.
 *
 * <p>One animation at a time — the selected one — since only the preview plays. Its frames are
 * scaled down to {@link #MAX_DIMENSION} as they are decoded: the preview is never larger than that
 * on screen, and the 50 full-size frames of a five-second 4K burst would be over 1.5 GiB of native
 * memory and as much VRAM, where scaled they are about 120 MiB of each at most.
 *
 * <p>Everything here except the decode runs on the render thread. A decode that finishes for a
 * file no longer wanted (the selection moved, or the manager closed) frees its own images.
 */
public final class AnimationPreview {

	/** The largest side a preview frame keeps; the preview pane is smaller than this in practice. */
	static final int MAX_DIMENSION = 1024;

	private record Playing(File file, List<ScreenshotImageCache.Loaded> frames, int[] delays, int totalMillis,
			long startedAtMillis) {
	}

	private record Decoded(File file, List<NativeImage> images, int[] delays) {
	}

	private static final Object HANDOVER = new Object();

	/** How far the decode for {@link #wanted} has got, for the loading bar; written by the loader
	 * thread, read by the render thread. */
	private static volatile int framesLoaded;
	private static volatile int framesToLoad;

	private static ExecutorService executor;
	/** The file whose frames are loading or loaded; null when nothing is wanted. */
	private static File wanted;
	/** Bumped whenever {@link #wanted} changes, so a decode for an earlier wish knows it is stale. */
	private static int token;
	private static Decoded pending;
	private static Playing playing;
	/** A file whose split or decode failed: shown as its still, and not retried until reopened. */
	private static File failed;
	private static int nextId;

	private AnimationPreview() {
	}

	/**
	 * The frame of {@code file}'s animation that is due at {@code nowMillis}, or null while it is
	 * still loading (or cannot be played) — the caller draws the ordinary still meanwhile, which is
	 * the same first frame, so the switch to playing is seamless.
	 */
	public static ScreenshotImageCache.Loaded frameAt(File file, long nowMillis) {
		if (file == null) {
			return null;
		}
		if (!file.equals(wanted)) {
			want(file);
		}
		takePending();
		if (playing == null || !playing.file().equals(file)) {
			return null;
		}
		return playing.frames().get(frameIndexAt(playing.delays(), playing.totalMillis(),
				nowMillis - playing.startedAtMillis()));
	}

	/**
	 * One PNG's pixels as ARGB, row by row — the {@link AnimatedPng.PixelDecoder} the export hands
	 * its WebP encoder. {@code NativeImage} because it is the decoder the game ships, and this runs
	 * on the export's IO thread, never the render thread.
	 */
	public static int[] argbOf(byte[] png) throws java.io.IOException {
		try (NativeImage image = NativeImage.read(png)) {
			return image.getPixels();
		}
	}

	/**
	 * How far {@code file}'s frames have got, 0 to 1, while they are still being decoded for the
	 * preview — or -1 when nothing is loading for it: playing already, not an animation, or failed.
	 */
	public static float loadingProgress(File file) {
		if (file == null || !file.equals(wanted) || file.equals(failed)
				|| (playing != null && playing.file().equals(file))) {
			return -1F;
		}
		int total = framesToLoad;
		return total <= 0 ? 0F : Math.min(1F, framesLoaded / (float) total);
	}

	/** How many frames are loaded and playing for {@code file}; 0 while loading or for a still. */
	public static int playingFrameCount(File file) {
		return playing != null && playing.file().equals(file) ? playing.frames().size() : 0;
	}

	/**
	 * Which frame shows {@code elapsedMillis} into a loop with these delays. Package-private seam:
	 * the one piece of playback with logic in it, and it needs no textures.
	 */
	static int frameIndexAt(int[] delays, int totalMillis, long elapsedMillis) {
		if (totalMillis <= 0) {
			return 0;
		}
		long t = Math.floorMod(elapsedMillis, (long) totalMillis);
		for (int i = 0; i < delays.length; i++) {
			t -= delays[i];
			if (t < 0) {
				return i;
			}
		}
		return delays.length - 1;
	}

	/** Stops whatever is playing once the preview moves to something that is not an animation. */
	public static void showNothing() {
		if (wanted != null || playing != null) {
			releaseAll();
		}
	}

	/** Frees everything; the manager calls this on close. */
	public static void releaseAll() {
		synchronized (HANDOVER) {
			wanted = null;
			token++;
			if (pending != null) {
				pending.images().forEach(NativeImage::close);
				pending = null;
			}
		}
		stopPlaying();
		failed = null;
	}

	private static void want(File file) {
		stopPlaying();
		int myToken;
		framesLoaded = 0;
		framesToLoad = 0;
		synchronized (HANDOVER) {
			wanted = file;
			myToken = ++token;
			if (pending != null) {
				pending.images().forEach(NativeImage::close);
				pending = null;
			}
		}
		if (file.equals(failed)) {
			return;
		}
		if (executor == null) {
			executor = Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "VoxelCam Animation Loader");
				thread.setDaemon(true);
				return thread;
			});
		}
		executor.execute(() -> decode(file, myToken));
	}

	private static void decode(File file, int myToken) {
		List<NativeImage> images = new ArrayList<>();
		int[] delays = null;
		try {
			byte[] apng = Files.readAllBytes(file.toPath());
			List<AnimatedPng.Frame> frames = AnimatedPng.splitFrames(apng);
			PngDimensions.Dimensions size = PngDimensions.read(file);
			delays = new int[frames.size()];
			framesToLoad = frames.size();
			// Every frame after the first is a patch, so the preview keeps the canvas the way a
			// browser does and snapshots it after each one — scaled down in Java first, so only the
			// preview-sized pixels go through NativeImage one at a time.
			int[] canvas = size == null ? null : new int[size.width() * size.height()];
			int[] scaledSize = size == null ? null : previewSize(size.width(), size.height());
			for (int i = 0; i < frames.size() && canvas != null; i++) {
				if (!stillWanted(myToken)) {
					break;
				}
				AnimatedPng.Frame frame = frames.get(i);
				new FramePatch(frame.x(), frame.y(), frame.width(), frame.height(), argbOf(frame.png()))
						.composite(canvas, size.width(), frame.blend());
				int[] small = scaledSize[0] == size.width() ? canvas
						: ArgbImages.downscale(canvas, size.width(), size.height(), scaledSize[0], scaledSize[1]);
				images.add(imageOf(small, scaledSize[0], scaledSize[1]));
				delays[i] = frame.delayMillis();
				framesLoaded = i + 1;
			}
		} catch (Exception e) {
			VoxelCamClient.LOGGER.warn("Failed to read animation {}", file, e);
			images.forEach(NativeImage::close);
			images.clear();
		}
		synchronized (HANDOVER) {
			if (myToken != token) {
				images.forEach(NativeImage::close);
				return;
			}
			// An empty result is a still or a failure: posted anyway, so the render thread stops
			// waiting on this file and does not ask for it again.
			pending = new Decoded(file, images, delays == null ? new int[0] : delays);
		}
	}

	private static boolean stillWanted(int myToken) {
		synchronized (HANDOVER) {
			return myToken == token;
		}
	}

	private static NativeImage imageOf(int[] argb, int width, int height) {
		NativeImage image = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setPixel(x, y, argb[y * width + x]);
			}
		}
		return image;
	}

	/** The preview's frame size: the canvas, scaled so its longer side is at most {@link
	 * #MAX_DIMENSION}. */
	static int[] previewSize(int width, int height) {
		int largest = Math.max(width, height);
		if (largest <= MAX_DIMENSION) {
			return new int[] { width, height };
		}
		return new int[] { Math.max(1, width * MAX_DIMENSION / largest), Math.max(1, height * MAX_DIMENSION / largest) };
	}

	/** Uploads a finished decode, on the render thread. */
	private static void takePending() {
		Decoded decoded;
		synchronized (HANDOVER) {
			decoded = pending;
			pending = null;
		}
		if (decoded == null) {
			return;
		}
		if (decoded.images().size() < 2) {
			// Not something to play: let the still stand, and do not try this file again.
			decoded.images().forEach(NativeImage::close);
			failed = decoded.file();
			return;
		}
		List<ScreenshotImageCache.Loaded> frames = new ArrayList<>(decoded.images().size());
		int total = 0;
		for (int i = 0; i < decoded.images().size(); i++) {
			NativeImage image = decoded.images().get(i);
			Identifier id = Identifier.fromNamespaceAndPath(VoxelCamClient.MOD_ID, "animation_" + (nextId++));
			int width = image.getWidth();
			int height = image.getHeight();
			try {
				Minecraft.getInstance().getTextureManager().register(id,
						new DynamicTexture(decoded.file()::getName, image));
			} catch (Exception e) {
				VoxelCamClient.LOGGER.warn("Failed to upload animation frame {} of {}", i, decoded.file(), e);
				image.close();
				for (int rest = i + 1; rest < decoded.images().size(); rest++) {
					decoded.images().get(rest).close();
				}
				frames.forEach(f -> Minecraft.getInstance().getTextureManager().release(f.id()));
				failed = decoded.file();
				return;
			}
			frames.add(new ScreenshotImageCache.Loaded(id, width, height));
			total += decoded.delays()[i];
		}
		playing = new Playing(decoded.file(), frames, decoded.delays(), total, Util.getMillis());
	}

	private static void stopPlaying() {
		if (playing != null) {
			for (ScreenshotImageCache.Loaded frame : playing.frames()) {
				Minecraft.getInstance().getTextureManager().release(frame.id());
			}
			playing = null;
		}
	}
}
