package com.thatapplefreak.voxelcam.client.screenshot;

import com.mojang.blaze3d.platform.NativeImage;
import com.thatapplefreak.voxelcam.client.VoxelCamClient;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

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

	private record Decoded(Key key, NativeImage image, int generation) {
	}

	/** What {@link #uploadPending()} should do with one finished decode. */
	enum Disposition {
		UPLOAD,
		FAIL,
		DISCARD
	}

	/** Wide enough to stay sharp at high GUI scales; still ~1/400th of a full screenshot's pixels. */
	private static final int THUMBNAIL_WIDTH = 128;

	private static final Map<Key, Loaded> LOADED = new HashMap<>();
	private static final Set<Key> IN_FLIGHT = new HashSet<>();
	private static final Set<Key> FAILED = new HashSet<>();
	private static final Queue<Decoded> DECODED = new ConcurrentLinkedQueue<>();

	/**
	 * Guards the handover of a finished decode: held while a loader thread decides whether
	 * to queue its result, and while {@link #releaseAll()} invalidates and drains the queue.
	 * Never held across a decode — the render thread must not end up waiting on file I/O.
	 */
	private static final Object HANDOVER = new Object();

	private static ExecutorService executor;
	private static int nextId = 0;
	private static int generation = 0;

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
		// Read on the render thread and captured, not read from inside the lambda: the
		// generation that matters is the one this decode was asked for, not whatever it
		// has become by the time a loader thread gets round to running.
		int submittedIn = generation;
		executor.execute(() -> {
			NativeImage image = decode(key);
			// Only the manager drains the queue, and it may never open again this session,
			// so a decode that outlived its generation has to free itself here rather than
			// wait for a sweep that will not come. Freeing off the render thread is safe:
			// NativeImage.close() is a native free, not a GL call, and decode() already
			// closes the full-size image on this thread when it downscales.
			synchronized (HANDOVER) {
				if (dispositionOf(submittedIn, image != null) == Disposition.DISCARD) {
					if (image != null) {
						image.close();
					}
					return;
				}
				DECODED.add(new Decoded(key, image, submittedIn));
			}
		});
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

	/**
	 * Decides what to do with a decode that has just landed. Asked twice: on the loader
	 * thread before the result is queued, and again on the render thread before it is
	 * uploaded, since the queue can be invalidated in between.
	 *
	 * <p>A decode already running on a loader thread cannot be cancelled, so
	 * {@link #releaseAll()} can only mark it: anything submitted under an older
	 * generation belongs to a cache that no longer exists. Uploading it would register a
	 * texture that the reopened cache's own decode is about to displace from
	 * {@link #LOADED} — and since {@code LOADED} is the only handle on a registered id,
	 * that texture would then be stranded in {@code TextureManager} for the rest of the
	 * session. Recording a stale failure would likewise re-poison {@link #FAILED} for a
	 * file the next open may well be able to read.
	 */
	static Disposition dispositionOf(int decodeGeneration, boolean decodeSucceeded) {
		if (decodeGeneration != generation) {
			return Disposition.DISCARD;
		}
		return decodeSucceeded ? Disposition.UPLOAD : Disposition.FAIL;
	}

	/** Uploads images decoded since the last call. Must run on the render thread. */
	public static void uploadPending() {
		Decoded decoded;
		while ((decoded = DECODED.poll()) != null) {
			NativeImage image = decoded.image();
			Disposition disposition = dispositionOf(decoded.generation(), image != null);
			if (disposition == Disposition.DISCARD) {
				// IN_FLIGHT is deliberately untouched: releaseAll() cleared it, so the key may
				// already have been re-submitted, and that live decode still owns the entry.
				if (image != null) {
					image.close();
				}
				continue;
			}
			IN_FLIGHT.remove(decoded.key());
			if (disposition == Disposition.FAIL) {
				FAILED.add(decoded.key());
				continue;
			}
			Identifier id = Identifier.fromNamespaceAndPath(VoxelCamClient.MOD_ID, "screenshot_" + (nextId++));
			int width = image.getWidth();
			int height = image.getHeight();
			// DynamicTexture takes ownership and closes the image itself — but only once it
			// exists, so before that, and if the register throws, freeing it is still on us.
			DynamicTexture texture = null;
			try {
				texture = new DynamicTexture(decoded.key().file()::getName, image);
				Minecraft.getInstance().getTextureManager().register(id, texture);
			} catch (Exception e) {
				VoxelCamClient.LOGGER.warn("Failed to upload screenshot {}", decoded.key().file(), e);
				if (texture != null) {
					texture.close();
				} else {
					image.close();
				}
				// Neither in flight nor loaded any more, so without this the next frame just
				// asks for the same decode again.
				FAILED.add(decoded.key());
				continue;
			}
			Loaded previous = LOADED.put(decoded.key(), new Loaded(id, width, height));
			if (previous != null) {
				Minecraft.getInstance().getTextureManager().release(previous.id());
			}
		}
	}

	/** Drops every cached texture for one file (both sizes), e.g. after it is deleted. */
	public static void release(File file) {
		for (boolean thumbnail : new boolean[] { true, false }) {
			Key key = new Key(file, thumbnail);
			Loaded loaded = LOADED.remove(key);
			if (loaded != null) {
				Minecraft.getInstance().getTextureManager().release(loaded.id());
			}
			FAILED.remove(key);
		}
	}

	public static void releaseAll() {
		for (Loaded loaded : LOADED.values()) {
			Minecraft.getInstance().getTextureManager().release(loaded.id());
		}
		LOADED.clear();
		FAILED.clear();
		IN_FLIGHT.clear();
		// Decodes still running on a loader thread outlive this call. Bumping the
		// generation under the handover lock is what makes each of them free its own
		// result; draining covers the ones that finished before the bump landed.
		synchronized (HANDOVER) {
			generation++;
			Decoded pending;
			while ((pending = DECODED.poll()) != null) {
				if (pending.image() != null) {
					pending.image().close();
				}
			}
		}
	}

	/** The generation decodes submitted from now on will carry. */
	static int generation() {
		return generation;
	}
}
