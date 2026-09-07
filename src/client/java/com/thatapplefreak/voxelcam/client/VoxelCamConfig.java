package com.thatapplefreak.voxelcam.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the handful of settings worth keeping between sessions: burst length and
 * big-screenshot size — both set from the capture menu's outer ring, or {@code /bigscreenshot}
 * for the size — plus the manager's sort mode and favourites-only filter. Session-only used to
 * be a deliberate choice with nothing worth remembering; the outer ring is what changed that.
 *
 * <p>{@link #favoritesOnly} is the odd field out. The other three mirror an existing
 * session-static holder ({@link Burst#getLength()}, {@link BigScreenshot#getSize()}, {@link
 * SortMode#current()}), but favourites-only has always lived as a plain instance field on {@code
 * GuiScreenShotManager} — this is its only persistent home, and the manager reads and writes it
 * directly (see {@code GuiScreenShotManager.init()}/{@code toggleFavoritesOnly()}) rather than
 * keeping a second copy in sync.
 *
 * <p>Deliberately does not save itself from {@link Burst#setLength}, {@link
 * BigScreenshot#setSize}, or {@link SortMode#setCurrent}: those three stay side-effect-free and
 * unit-testable with no client the way they already were. {@link #saveCurrent()} is called
 * explicitly from every place a setting actually changes at the player's hand instead — the
 * capture menu's ring, {@code BigScreenshotCommand}, the manager's sort button and favourites
 * toggle — all of which already need a live client to run at all.
 */
public final class VoxelCamConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static volatile VoxelCamConfig current = new VoxelCamConfig();

	public int burstLength = Burst.DEFAULT_LENGTH;
	public String bigScreenshotSize = BigScreenshotSize.DEFAULT.token();
	public String sortMode = SortMode.DATE_NEWEST.name();
	public boolean favoritesOnly = false;
	public boolean autoCaptureAdvancement = true;
	public boolean autoCaptureBossDefeat = true;
	public boolean autoCaptureDeath = true;
	public boolean autoCaptureNewDimension = true;
	public int autoCaptureCooldownSeconds = AutoCapture.DEFAULT_COOLDOWN_SECONDS;

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
		Burst.setLength(current.burstLength);
		BigScreenshotSize size = BigScreenshotSize.parse(current.bigScreenshotSize);
		if (size != null) {
			BigScreenshot.setSize(size);
		}
		SortMode.setCurrent(parseSortMode(current.sortMode));
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
	 * tag embed.
	 */
	public static void saveCurrent() {
		VoxelCamConfig snapshot = snapshot();
		current = snapshot;

		Path path = configPath();
		Util.ioPool().execute(() -> save(snapshot, path));
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
		snapshot.burstLength = Burst.getLength();
		snapshot.bigScreenshotSize = BigScreenshot.getSize().token();
		snapshot.sortMode = SortMode.current().name();
		snapshot.favoritesOnly = current.favoritesOnly;
		snapshot.autoCaptureAdvancement = current.autoCaptureAdvancement;
		snapshot.autoCaptureBossDefeat = current.autoCaptureBossDefeat;
		snapshot.autoCaptureDeath = current.autoCaptureDeath;
		snapshot.autoCaptureNewDimension = current.autoCaptureNewDimension;
		snapshot.autoCaptureCooldownSeconds = current.autoCaptureCooldownSeconds;
		return snapshot;
	}

	/** Test-only reset: {@link #current} is static and outlives any one test. Public only because
	 * the settings it now carries are read from other packages, so the suites that mutate them live
	 * there too — nothing in the mod calls this. */
	public static void forgetCurrent() {
		current = new VoxelCamConfig();
	}

	/** Package-private seam, mirrored with {@link #load(Path)}. */
	static void save(VoxelCamConfig config, Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			VoxelCamClient.LOGGER.warn("Failed to write voxelcam.json", e);
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
