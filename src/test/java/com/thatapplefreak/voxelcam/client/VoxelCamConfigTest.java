package com.thatapplefreak.voxelcam.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thatapplefreak.voxelcam.client.screenshot.SortMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

	private Path configFile() {
		return dir.resolve("voxelcam.json");
	}

	@Test
	void roundTripsThroughDisk() {
		VoxelCamConfig original = new VoxelCamConfig();
		original.burstLength = 12;
		original.bigScreenshotSize = "4k";
		original.sortMode = SortMode.NAME_A_TO_Z.name();
		original.favoritesOnly = true;

		VoxelCamConfig.save(original, configFile());
		VoxelCamConfig read = VoxelCamConfig.load(configFile());

		assertEquals(original.burstLength, read.burstLength);
		assertEquals(original.bigScreenshotSize, read.bigScreenshotSize);
		assertEquals(original.sortMode, read.sortMode);
		assertEquals(original.favoritesOnly, read.favoritesOnly);
	}

	@Test
	void missingFileYieldsDefaults() {
		VoxelCamConfig config = VoxelCamConfig.load(configFile());

		VoxelCamConfig defaults = new VoxelCamConfig();
		assertEquals(defaults.burstLength, config.burstLength);
		assertEquals(defaults.bigScreenshotSize, config.bigScreenshotSize);
		assertEquals(defaults.sortMode, config.sortMode);
		assertFalse(config.favoritesOnly);
	}

	/** A file another program is mid-write to, or one hand-edited into something broken, must
	 * not take capture settings down with it — the manager still has to open. */
	@Test
	void corruptJsonFallsBackToDefaultsRatherThanThrowing() throws IOException {
		Files.writeString(configFile(), "{ this is not valid json", StandardCharsets.UTF_8);

		VoxelCamConfig config = VoxelCamConfig.load(configFile());

		assertEquals(new VoxelCamConfig().burstLength, config.burstLength);
	}

	@Test
	void literalJsonNullFallsBackToDefaults() throws IOException {
		Files.writeString(configFile(), "null", StandardCharsets.UTF_8);

		VoxelCamConfig config = VoxelCamConfig.load(configFile());

		assertEquals(new VoxelCamConfig().burstLength, config.burstLength);
	}

	@Test
	void saveCreatesTheConfigDirectoryIfItDoesNotExistYet() {
		Path nested = dir.resolve("nested").resolve("voxelcam.json");

		VoxelCamConfig.save(new VoxelCamConfig(), nested);

		assertTrue(Files.exists(nested));
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
