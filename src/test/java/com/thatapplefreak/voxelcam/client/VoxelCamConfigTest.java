package com.thatapplefreak.voxelcam.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatapplefreak.voxelcam.client.screenshot.SortMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link VoxelCamConfig#load()} needs {@code FabricLoader}, which is unavailable outside a real
 * mod environment — the package-private {@code load(Path)}/{@code save(Path, ...)} seams this
 * exercises instead are what make the actual read/write logic reachable here, the same "extract
 * a seam beside the untestable thing" pattern {@code ScreenshotHandler.writeOrDiscard} uses for
 * a GL-backed write.
 */
class VoxelCamConfigTest {

	@TempDir
	Path dir;

	/** {@code current()} is static and shared, so a test that leaves a setting flipped follows the
	 * next one in — and would leave every other suite's triggers switched off. */
	@AfterEach
	void settle() {
		VoxelCamConfig.forgetCurrent();
	}

	private Path configFile() {
		return dir.resolve("voxelcam.json");
	}

	@Test
	void roundTripsThroughDisk() {
		VoxelCamConfig original = new VoxelCamConfig();
		original.burstSeconds = 4;
		original.bigScreenshotSize = "4k";
		original.sortMode = SortMode.NAME_A_TO_Z.name();
		original.favoritesOnly = true;
		original.autoCaptureAdvancement = false;
		original.autoCaptureBossDefeat = false;
		original.autoCaptureDeath = false;
		original.autoCaptureNewDimension = false;
		original.autoCaptureCooldownSeconds = 42;

		VoxelCamConfig.save(original, configFile());
		VoxelCamConfig read = VoxelCamConfig.load(configFile());

		assertEquals(original.burstSeconds, read.burstSeconds);
		assertEquals(original.bigScreenshotSize, read.bigScreenshotSize);
		assertEquals(original.sortMode, read.sortMode);
		assertEquals(original.favoritesOnly, read.favoritesOnly);
		assertFalse(read.autoCaptureAdvancement);
		assertFalse(read.autoCaptureBossDefeat);
		assertFalse(read.autoCaptureDeath);
		assertFalse(read.autoCaptureNewDimension);
		assertEquals(42, read.autoCaptureCooldownSeconds);
	}

	@Test
	void missingFileYieldsDefaults() {
		VoxelCamConfig config = VoxelCamConfig.load(configFile());

		VoxelCamConfig defaults = new VoxelCamConfig();
		assertEquals(defaults.burstSeconds, config.burstSeconds);
		assertEquals(defaults.bigScreenshotSize, config.bigScreenshotSize);
		assertEquals(defaults.sortMode, config.sortMode);
		assertFalse(config.favoritesOnly);
		assertTrue(config.autoCaptureAdvancement, "the automatic triggers ship on");
		assertTrue(config.autoCaptureBossDefeat);
		assertTrue(config.autoCaptureDeath);
		assertTrue(config.autoCaptureNewDimension);
	}

	/**
	 * A {@code voxelcam.json} written before these fields existed has no key for them, and Gson
	 * leaves a field it finds no key for at whatever the constructor set — which is the whole reason
	 * the defaults are written as field initialisers rather than applied after the parse.
	 */
	@Test
	void aConfigWrittenBeforeTheseSettingsExistedKeepsTheirDefaults() throws IOException {
		Files.writeString(configFile(), "{ \"burstSeconds\": 4 }", StandardCharsets.UTF_8);

		VoxelCamConfig config = VoxelCamConfig.load(configFile());

		assertEquals(4, config.burstSeconds);
		assertTrue(config.autoCaptureAdvancement, "an older file must not read as every trigger off");
		assertEquals(new VoxelCamConfig().autoCaptureCooldownSeconds, config.autoCaptureCooldownSeconds);
	}

	/** The old {@code burstLength} counted frames; read as seconds, a saved 8 or 20 would be a
	 * nonsense length, so an upgraded file starts from the default instead. */
	@Test
	void anOldFrameCountIsNotReadAsSeconds() throws IOException {
		Files.writeString(configFile(), "{ \"burstLength\": 20 }", StandardCharsets.UTF_8);

		VoxelCamConfig config = VoxelCamConfig.load(configFile());

		assertEquals(new VoxelCamConfig().burstSeconds, config.burstSeconds);
	}

	/** A file another program is mid-write to, or one hand-edited into something broken, must
	 * not take capture settings down with it — the manager still has to open. */
	@Test
	void corruptJsonFallsBackToDefaultsRatherThanThrowing() throws IOException {
		Files.writeString(configFile(), "{ this is not valid json", StandardCharsets.UTF_8);

		VoxelCamConfig config = VoxelCamConfig.load(configFile());

		assertEquals(new VoxelCamConfig().burstSeconds, config.burstSeconds);
	}

	@Test
	void literalJsonNullFallsBackToDefaults() throws IOException {
		Files.writeString(configFile(), "null", StandardCharsets.UTF_8);

		VoxelCamConfig config = VoxelCamConfig.load(configFile());

		assertEquals(new VoxelCamConfig().burstSeconds, config.burstSeconds);
	}

	@Test
	void saveCreatesTheConfigDirectoryIfItDoesNotExistYet() {
		Path nested = dir.resolve("nested").resolve("voxelcam.json");

		VoxelCamConfig.save(new VoxelCamConfig(), nested);

		assertTrue(Files.exists(nested));
	}

	/**
	 * The trap this seam exists for. {@code saveCurrent()} builds a fresh object and has to copy
	 * every setting that has no session-static holder across from {@code current()} — miss one and
	 * toggling it in the settings screen writes its default straight back to disk, silently undoing
	 * what the player just did. {@code roundTripsThroughDisk} cannot catch that: it builds its
	 * object directly and never comes through here.
	 */
	@Test
	void snapshotKeepsTheSettingsThatHaveNoSessionStatic() {
		VoxelCamConfig live = VoxelCamConfig.current();
		live.favoritesOnly = true;
		live.autoCaptureAdvancement = false;
		live.autoCaptureBossDefeat = false;
		live.autoCaptureDeath = false;
		live.autoCaptureNewDimension = false;
		live.autoCaptureCooldownSeconds = 42;
		live.captureStatusHud = false;

		VoxelCamConfig snapshot = VoxelCamConfig.snapshot();

		assertTrue(snapshot.favoritesOnly);
		assertFalse(snapshot.autoCaptureAdvancement);
		assertFalse(snapshot.autoCaptureBossDefeat);
		assertFalse(snapshot.autoCaptureDeath);
		assertFalse(snapshot.autoCaptureNewDimension);
		assertEquals(42, snapshot.autoCaptureCooldownSeconds);
		assertFalse(snapshot.captureStatusHud);
	}

	@Test
	void parseSortModeRoundTripsAValidName() {
		assertEquals(SortMode.NAME_Z_TO_A, VoxelCamConfig.parseSortMode(SortMode.NAME_Z_TO_A.name()));
	}

	@Test
	void parseSortModeFallsBackToDateNewestForGarbage() {
		assertEquals(SortMode.DATE_NEWEST, VoxelCamConfig.parseSortMode("not a real sort mode"));
		assertEquals(SortMode.DATE_NEWEST, VoxelCamConfig.parseSortMode(null));
	}
}
