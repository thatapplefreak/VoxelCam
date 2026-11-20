package com.thatapplefreak.voxelcam.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.thatapplefreak.voxelcam.client.screenshot.AutoCapture;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshotSize;
import com.thatapplefreak.voxelcam.client.screenshot.Burst;
import com.thatapplefreak.voxelcam.client.screenshot.SortMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persists the handful of settings worth keeping between sessions: burst length and
 * big-screenshot size — both set from the capture menu's outer ring, or {@code /bigscreenshot}
 * for the size — plus the manager's sort mode and favourites-only filter. Session-only used to
 * be a deliberate choice with nothing worth remembering; the outer ring is what changed that.
 *
 * <p>{@link #favoritesOnly} is the odd field out. The other three mirror an existing
 * session-static holder ({@link Burst#getSeconds()}, {@link BigScreenshot#getSize()}, {@link
 * SortMode#current()}), but favourites-only has always lived as a plain instance field on {@code
 * GuiScreenShotManager} — this is its only persistent home, and the manager reads and writes it
 * directly (see {@code GuiScreenShotManager.init()}/{@code toggleFavoritesOnly()}) rather than
 * keeping a second copy in sync.
 *
 * <p>Deliberately does not save itself from {@link Burst#setSeconds}, {@link
 * BigScreenshot#setSize}, or {@link SortMode#setCurrent}: those three stay side-effect-free and
 * unit-testable with no client the way they already were. {@link #saveCurrent()} is called
 * explicitly from every place a setting actually changes at the player's hand instead — the
 * capture menu's ring, {@code BigScreenshotCommand}, the manager's sort button and favourites
 * toggle — all of which already need a live client to run at all.
 */
public final class VoxelCamConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static volatile VoxelCamConfig current = new VoxelCamConfig();

	private static final AtomicLong SAVE_SEQUENCE = new AtomicLong();
	private static final Object SAVE_LOCK = new Object();
	/** Guarded by {@link #SAVE_LOCK}. */
	private static long lastSavedSequence;

	/** Seconds, under a new key: the old key {@code burstLength} counted frames, and reading an old
	 * frame count as seconds would be wrong, so an old file falls back to the default instead. */
	public int burstSeconds = Burst.DEFAULT_SECONDS;
	public String bigScreenshotSize = BigScreenshotSize.DEFAULT.token();
	public String sortMode = SortMode.DATE_NEWEST.name();
	public boolean favoritesOnly = false;
	public boolean autoCaptureAdvancement = true;
	public boolean autoCaptureBossDefeat = true;
	public boolean autoCaptureDeath = true;
	public boolean autoCaptureNewDimension = true;
	public int autoCaptureCooldownSeconds = AutoCapture.DEFAULT_COOLDOWN_SECONDS;
	/** Whether {@code CaptureStatusHud} draws at all. On by default: with chat gone it is the only
	 * capture feedback there is. */
	public boolean captureStatusHud = true;

	public static VoxelCamConfig current() {
		return current;
	}

	/**
	 * Reads {@code voxelcam.json} (or falls back to defaults if it does not exist or cannot be
	 * read) and applies the three session-static settings. Called once, from {@code
	 * VoxelCamClient.onInitializeClient} — deliberately does not itself call {@link
	 * #saveCurrent()}: this is restoring what was already on disk, not a change worth writing
	 * straight back.
	 */
	public static void load() {
		current = load(configPath());
		applyToSession(current);
	}

	/**
	 * Package-private seam: the half of {@link #load()} that needs no {@code FabricLoader}, and
	 * the half that runs inside {@code onInitializeClient}, where a throw is a crash at launch.
	 * Nothing has been saved by then, so the file that caused it is still there next launch.
	 */
	static void applyToSession(VoxelCamConfig config) {
		Burst.setSeconds(config.burstSeconds);
		// A field initialiser only covers a missing key. "bigScreenshotSize": null parses,
		// and Gson writes that null over the default, so it is as unparseable as any other
		// token and leaves the size where it was. parseSortMode guards its field the same way.
		BigScreenshotSize size = config.bigScreenshotSize == null ? null : BigScreenshotSize.parse(config.bigScreenshotSize);
		if (size != null) {
			BigScreenshot.setSize(size);
		}
		SortMode.setCurrent(parseSortMode(config.sortMode));
	}

	/**
	 * Package-private seam: {@code FabricLoader} is unavailable in plain JUnit, so the path this
	 * reads from is injectable — the same "extract a seam beside the untestable thing" pattern
	 * {@code ScreenshotHandler.writeOrDiscard} uses for a GL-backed write.
	 */
	static VoxelCamConfig load(Path path) {
		if (Files.exists(path)) {
			try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				VoxelCamConfig loaded = GSON.fromJson(reader, VoxelCamConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException | JsonParseException e) {
				VoxelCamClient.LOGGER.warn("Failed to read voxelcam.json, using defaults", e);
			}
		}
		return new VoxelCamConfig();
	}

	/**
	 * Snapshots the three session-static settings, plus whatever {@link #favoritesOnly} the
	 * caller has already written onto {@link #current()}, and writes them to disk on {@code
	 * Util.ioPool()} — fire-and-forget, since losing a settings write is a shame, not a failure
	 * worth surfacing, the same call {@code ScreenshotHandler.embedMetadata} makes for a failed
	 * tag embed. The pool runs these tasks in no particular order, which {@link #saveIfNewest}
	 * is there to make harmless.
	 */
	public static void saveCurrent() {
		VoxelCamConfig snapshot = snapshot();
		current = snapshot;

		Path path = configPath();
		long sequence = nextSaveSequence();
		Util.ioPool().execute(() -> saveIfNewest(snapshot, path, sequence));
	}

	/** The order the changes were made in, taken on the thread that made them: the pool gives no
	 * order to the writes, so each one has to carry its own. */
	static long nextSaveSequence() {
		return SAVE_SEQUENCE.incrementAndGet();
	}

	/**
	 * Package-private seam: the write {@link #saveCurrent()} hands to the pool, callable from a
	 * test with threads and an order of its own choosing.
	 *
	 * <p>{@code Util.ioPool()} has several threads and every change is its own task, so two
	 * quick clicks are two writes that may run at once or the wrong way round. The lock lets one
	 * through at a time, and a snapshot older than the one already on disk is dropped: the file
	 * has to end up holding the player's last click, whichever task ran last.
	 */
	static void saveIfNewest(VoxelCamConfig config, Path path, long sequence) {
		synchronized (SAVE_LOCK) {
			if (sequence <= lastSavedSequence) {
				return;
			}
			// Only once it is on disk: an older snapshot is better than none if this one fails.
			if (save(config, path)) {
				lastSavedSequence = sequence;
			}
		}
	}

	/**
	 * The one place every field has to be accounted for, split out from {@link #saveCurrent()} as a
	 * seam because it is the half worth testing and the half that needs no {@code FabricLoader}.
	 *
	 * <p>Two kinds of field meet here. The ones mirroring a session-static are read back off it;
	 * the ones that live only on {@link #current} — {@link #favoritesOnly} and the auto-capture
	 * settings, none of which has a holder to mirror — are copied across. Forgetting one of the
	 * second kind writes its default straight back to disk the next time anything else is saved,
	 * silently undoing whatever the player just toggled. {@code roundTripsThroughDisk} cannot catch
	 * that: it builds its object directly and never comes through here, which is exactly why
	 * {@code snapshotKeepsSettingsThatHaveNoSessionStatic} does.
	 */
	static VoxelCamConfig snapshot() {
		VoxelCamConfig snapshot = new VoxelCamConfig();
		snapshot.burstSeconds = Burst.getSeconds();
		snapshot.bigScreenshotSize = BigScreenshot.getSize().token();
		snapshot.sortMode = SortMode.current().name();
		snapshot.favoritesOnly = current.favoritesOnly;
		snapshot.autoCaptureAdvancement = current.autoCaptureAdvancement;
		snapshot.autoCaptureBossDefeat = current.autoCaptureBossDefeat;
		snapshot.autoCaptureDeath = current.autoCaptureDeath;
		snapshot.autoCaptureNewDimension = current.autoCaptureNewDimension;
		snapshot.autoCaptureCooldownSeconds = current.autoCaptureCooldownSeconds;
		snapshot.captureStatusHud = current.captureStatusHud;
		return snapshot;
	}

	/** Test-only reset: {@link #current} is static and outlives any one test. Public only because
	 * the settings it now carries are read from other packages, so the suites that mutate them live
	 * there too — nothing in the mod calls this. */
	public static void forgetCurrent() {
		current = new VoxelCamConfig();
	}

	/**
	 * Package-private seam, mirrored with {@link #load(Path)}.
	 *
	 * <p>Written to a temp file beside the target and moved over it, the way {@code
	 * PngTextChunk.embed} rewrites a screenshot. Opening voxelcam.json itself truncates it
	 * first, so a crash mid-write left half a file, and {@link #load(Path)} answers a file it
	 * cannot parse with defaults that the next change then writes over every setting.
	 *
	 * <p>The temp file has one fixed name, not {@code Files.createTempFile}'s random one: that
	 * creates its file owner-only, and the move would carry those permissions onto a config
	 * file other tools may read. One name is safe because the lock allows one write at a time,
	 * and a leftover from a crash is overwritten by the next save where random names pile up.
	 *
	 * @return whether the file now holds {@code config}.
	 */
	static boolean save(VoxelCamConfig config, Path path) {
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		synchronized (SAVE_LOCK) {
			try {
				Files.createDirectories(path.getParent());
				try (var writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
					GSON.toJson(config, writer);
				}
				moveIntoPlace(tmp, path);
				return true;
			} catch (IOException | JsonIOException e) {
				VoxelCamClient.LOGGER.warn("Failed to write voxelcam.json", e);
				return false;
			} finally {
				try {
					Files.deleteIfExists(tmp);
				} catch (IOException e) {
					VoxelCamClient.LOGGER.warn("Failed to remove {} after writing voxelcam.json", tmp, e);
				}
			}
		}
	}

	/** Atomic where the filesystem can do it. One that cannot still gets a whole file moved
	 * over the old one, which is no worse than the write this replaced. */
	private static void moveIntoPlace(Path tmp, Path path) throws IOException {
		try {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Package-private seam, unit-testable directly (unlike {@link #load()}, which needs {@code
	 * FabricLoader}): the default sort mode rather than throwing or leaving the caller with a
	 * null — every config written before this field existed, or edited by hand into something
	 * invalid, simply falls back to it. The same tolerance {@code CaptureContext.fromTags} shows
	 * a malformed tag.
	 */
	static SortMode parseSortMode(String name) {
		try {
			return SortMode.valueOf(name);
		} catch (IllegalArgumentException | NullPointerException e) {
			return SortMode.DATE_NEWEST;
		}
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("voxelcam.json");
	}
}
