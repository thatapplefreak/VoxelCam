package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads screenshot images off the render thread and uploads them as textures.
 *
 * <p>Decoding a full-size PNG takes long enough to drop frames, so the list
 * would stutter if it decoded thumbnails inline while scrolling. Files are read
 * and (for thumbnails) downscaled on a background thread; only the GL upload
 * happens on the render thread, via {@link #uploadPending()}.
 */
public final class ScreenshotImageCache {

	/** A GPU-resident image and its pixel dimensions. */
	public record Loaded(Identifier id, int width, int height) {
	}

	private record Key(File file, boolean thumbnail) {
	}

	private record Decoded(Key key, NativeImage image) {
	}

	/** Wide enough to stay sharp at high GUI scales; still ~1/400th of a full screenshot's pixels. */
	private static final int THUMBNAIL_WIDTH = 128;

	private static final Map<Key, Loaded> LOADED = new HashMap<>();
	private static final Set<Key> IN_FLIGHT = new HashSet<>();
	private static final Set<Key> FAILED = new HashSet<>();
	private static final Queue<Decoded> DECODED = new ConcurrentLinkedQueue<>();

	private static ExecutorService executor;
	private static int nextId = 0;

	private ScreenshotImageCache() {
	}

	/**
	 * Returns the image if it is ready, otherwise null and starts loading it.
	 * Callers should draw a placeholder while this returns null.
	 */
	public static Loaded get(File file, boolean thumbnail) {
		if (file == null) {
			return null;
		}
		Key key = new Key(file, thumbnail);
		Loaded ready = LOADED.get(key);
		if (ready != null) {
			return ready;
		}
		if (!IN_FLIGHT.contains(key) && !FAILED.contains(key)) {
			IN_FLIGHT.add(key);
			submit(key);
		}
		return null;
	}

	public static boolean hasFailed(File file, boolean thumbnail) {
		return FAILED.contains(new Key(file, thumbnail));
	}

	private static void submit(Key key) {
		if (executor == null) {
			executor = Executors.newFixedThreadPool(2, runnable -> {
				Thread thread = new Thread(runnable, "VoxelCam Image Loader");
				thread.setDaemon(true);
				return thread;
			});
		}
		executor.execute(() -> DECODED.add(new Decoded(key, decode(key))));
	}

	private static NativeImage decode(Key key) {
		try (InputStream in = new FileInputStream(key.file())) {
			NativeImage full = NativeImage.read(in);
			if (!key.thumbnail() || full.getWidth() <= THUMBNAIL_WIDTH) {
				return full;
			}
			int thumbHeight = Math.max(1, full.getHeight() * THUMBNAIL_WIDTH / full.getWidth());
			try (full) {
				NativeImage thumbnail = new NativeImage(THUMBNAIL_WIDTH, thumbHeight, false);
				full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), thumbnail);
				return thumbnail;
			}
		} catch (Exception e) {
			VoxelCamClient.LOGGER.warn("Failed to read screenshot {}", key.file(), e);
			return null;
		}
	}

	/** Uploads images decoded since the last call. Must run on the render thread. */
	public static void uploadPending() {
		Decoded decoded;
		while ((decoded = DECODED.poll()) != null) {
			IN_FLIGHT.remove(decoded.key());
			if (decoded.image() == null) {
				FAILED.add(decoded.key());
				continue;
			}
			NativeImage image = decoded.image();
			Identifier id = Identifier.of(VoxelCamClient.MOD_ID, "screenshot_" + (nextId++));
			// NativeImageBackedTexture takes ownership and closes the image itself.
			MinecraftClient.getInstance().getTextureManager()
					.registerTexture(id, new NativeImageBackedTexture(decoded.key().file()::getName, image));
			LOADED.put(decoded.key(), new Loaded(id, image.getWidth(), image.getHeight()));
		}
	}

	/** Drops every cached texture for one file (both sizes), e.g. after it is deleted. */
	public static void release(File file) {
		for (boolean thumbnail : new boolean[] { true, false }) {
			Key key = new Key(file, thumbnail);
			Loaded loaded = LOADED.remove(key);
			if (loaded != null) {
				MinecraftClient.getInstance().getTextureManager().destroyTexture(loaded.id());
			}
			FAILED.remove(key);
		}
	}

	public static void releaseAll() {
		for (Loaded loaded : LOADED.values()) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(loaded.id());
		}
		LOADED.clear();
		FAILED.clear();
		IN_FLIGHT.clear();
		// Anything decoded but never uploaded owns native memory that nothing else
		// will free, so close it here rather than leaking it.
		Decoded pending;
		while ((pending = DECODED.poll()) != null) {
			if (pending.image() != null) {
				pending.image().close();
			}
		}
	}
}
