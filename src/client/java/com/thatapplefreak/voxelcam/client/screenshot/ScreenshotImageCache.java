package com.thatapplefreak.voxelcam.client.screenshot;

import com.mojang.blaze3d.platform.NativeImage;
import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
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
 *
 * <p>Full-size entries are capped at {@link #MAX_FULL_SIZE} and evicted least recently
 * used first; thumbnails are not, because the list only asks for the rows on screen.
 */
public final class ScreenshotImageCache {

	/** A GPU-resident image and its pixel dimensions. */
	public record Loaded(Identifier id, int width, int height) {
	}

	private record Key(File file, boolean thumbnail) {
	}

	private record Decoded(Key key, NativeImage image, int generation, int claim) {
	}

	/** What {@link #uploadPending()} should do with one finished decode. */
	enum Disposition {
		UPLOAD,
		FAIL,
		DISCARD
	}

	/** Wide enough to stay sharp at high GUI scales; still ~1/400th of a full screenshot's pixels. */
	private static final int THUMBNAIL_WIDTH = 128;

	/**
	 * How many full-size previews may stay resident at once.
	 *
	 * <p>A full-size entry is expensive in a way a thumbnail is not: {@code DynamicTexture}
	 * keeps the decoded {@link NativeImage} alive for the texture's lifetime as well as the
	 * RGBA8 texture itself, so one 4K preview costs about 32 MiB of native heap and another
	 * 32 MiB of VRAM — neither of them on the Java heap, so no amount of GC pressure reclaims
	 * them. Thumbnails need no such cap: the list only asks for the rows the viewport shows,
	 * and at 128px wide they are ~1/400th of the pixels each.
	 *
	 * <p>Three rather than one because selection moves a row at a time: keeping the
	 * neighbours makes arrowing back and forth between two shots free, and three 4K previews
	 * are still under 100 MiB of each. Anything older is cheap to decode again.
	 */
	static final int MAX_FULL_SIZE = 3;

	private static final Map<Key, Loaded> LOADED = new HashMap<>();
	/**
	 * The files with a resident full-size preview, least recently used first. Touched only
	 * from the render thread, like {@link #IN_FLIGHT} — a loader thread never sees it, since
	 * an entry is only born once a decode has landed and been uploaded.
	 */
	private static final Set<File> FULL_SIZE_LRU = new LinkedHashSet<>();
	/**
	 * The claim number of the decode currently running for each key. A number rather than
	 * bare membership because {@link #release(File)} can drop a claim while its decode is
	 * still running and the key can then be asked for again: the result that eventually
	 * lands has to be told apart from the one the cache is now waiting for.
	 */
	private static final Map<Key, Integer> IN_FLIGHT = new HashMap<>();
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
	private static int nextClaim = 0;

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
			if (!thumbnail) {
				// Recency has to follow what is actually being drawn, not what was uploaded
				// last: decodes can land out of the order they were asked for, so without
				// this the preview on screen could be the eldest entry and get evicted by a
				// late arrival, only to be decoded again next frame.
				touchFullSize(file);
			}
			return ready;
		}
		if (!isLoading(file, thumbnail) && !FAILED.contains(key)) {
			int claim = nextClaim++;
			IN_FLIGHT.put(key, claim);
			submit(key, claim);
		}
		return null;
	}

	/**
	 * Marks this file's full-size preview the most recently used one.
	 *
	 * <p>The remove is load-bearing: {@code LinkedHashSet.add} leaves an element that is
	 * already present where it was, so without it the set would stay in first-upload order
	 * and "least recently used" would mean "oldest", which is a different thing.
	 */
	static void touchFullSize(File file) {
		FULL_SIZE_LRU.remove(file);
		FULL_SIZE_LRU.add(file);
	}

	/**
	 * Makes this file the most recently used full-size preview and returns the ones that no
	 * longer fit under {@link #MAX_FULL_SIZE}, least recently used first, for the caller to
	 * release. Returning them rather than releasing them here is what keeps this method free
	 * of {@code Minecraft} and so testable outside the game.
	 */
	static List<File> retainFullSize(File file) {
		touchFullSize(file);
		List<File> evicted = new ArrayList<>();
		Iterator<File> leastRecent = FULL_SIZE_LRU.iterator();
		while (FULL_SIZE_LRU.size() > MAX_FULL_SIZE) {
			evicted.add(leastRecent.next());
			leastRecent.remove();
		}
		return evicted;
	}

	/** Whether a decode is already running for this file at this size. */
	static boolean isLoading(File file, boolean thumbnail) {
		return IN_FLIGHT.containsKey(new Key(file, thumbnail));
	}

	public static boolean hasFailed(File file, boolean thumbnail) {
		return FAILED.contains(new Key(file, thumbnail));
	}

	private static void submit(Key key, int claim) {
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
				DECODED.add(new Decoded(key, image, submittedIn, claim));
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

	/**
	 * The render thread's finer form of the same question: it can also see whether the key
	 * still claims this particular decode, which a loader thread cannot, since
	 * {@link #IN_FLIGHT} is only ever touched from the render thread.
	 *
	 * <p>A claim is dropped by {@link #release(File)}, which the manager calls after a file
	 * has been deleted or renamed away. Uploading a result whose claim is gone would
	 * register a texture under a path that no longer exists, and since {@code release} can
	 * never be called for that path again and {@link #LOADED} is the only handle on a
	 * registered id, it would sit in {@code TextureManager} — with its full-size image —
	 * for the rest of the session. Recording a stale failure would poison a key that a
	 * later request may well be able to read.
	 */
	static Disposition dispositionOf(int decodeGeneration, boolean stillClaimed, boolean decodeSucceeded) {
		if (!stillClaimed) {
			return Disposition.DISCARD;
		}
		return dispositionOf(decodeGeneration, decodeSucceeded);
	}

	private static boolean stillClaimed(Decoded decoded) {
		Integer claim = IN_FLIGHT.get(decoded.key());
		return claim != null && claim.intValue() == decoded.claim();
	}

	/** Uploads images decoded since the last call. Must run on the render thread. */
	public static void uploadPending() {
		Decoded decoded;
		while ((decoded = DECODED.poll()) != null) {
			NativeImage image = decoded.image();
			Disposition disposition = dispositionOf(decoded.generation(), stillClaimed(decoded), image != null);
			if (disposition == Disposition.DISCARD) {
				// IN_FLIGHT is deliberately untouched: whatever entry the key holds now was
				// taken by a later request, and that live decode still owns it.
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
			if (!decoded.key().thumbnail()) {
				// After the register, not before: a register that threw left no texture to
				// account for, and claiming a slot for it would evict a live one for nothing.
				for (File stale : retainFullSize(decoded.key().file())) {
					Loaded dropped = LOADED.remove(new Key(stale, false));
					if (dropped != null) {
						Minecraft.getInstance().getTextureManager().release(dropped.id());
					}
				}
			}
		}
	}

	/**
	 * Drops every cached texture for one file (both sizes), e.g. after it is deleted.
	 *
	 * <p>Dropping the claim covers the case where the file is deleted or renamed while its
	 * decode is still running — a full-size PNG takes long enough that a confirmed delete
	 * easily beats it. That decode cannot be cancelled, but {@link #uploadPending()} will
	 * now free the image instead of registering a texture nothing can ever release. It is
	 * left to land there rather than freed on the loader thread the way a stale generation
	 * is: the manager is by definition still open on this path, so a sweep is one frame away.
	 */
	public static void release(File file) {
		for (boolean thumbnail : new boolean[] { true, false }) {
			Key key = new Key(file, thumbnail);
			Loaded loaded = LOADED.remove(key);
			if (loaded != null) {
				Minecraft.getInstance().getTextureManager().release(loaded.id());
			}
			FAILED.remove(key);
			IN_FLIGHT.remove(key);
		}
		FULL_SIZE_LRU.remove(file);
	}

	public static void releaseAll() {
		for (Loaded loaded : LOADED.values()) {
			Minecraft.getInstance().getTextureManager().release(loaded.id());
		}
		LOADED.clear();
		FAILED.clear();
		IN_FLIGHT.clear();
		FULL_SIZE_LRU.clear();
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
