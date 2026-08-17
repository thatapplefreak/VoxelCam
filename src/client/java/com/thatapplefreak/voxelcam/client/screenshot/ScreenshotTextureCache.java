package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Replaces GLImageMemoryHandler's raw GL11 texture loading with
 * NativeImage/NativeImageBackedTexture, keyed by screenshot file.
 */
public final class ScreenshotTextureCache {

	/** A loaded screenshot texture and its pixel dimensions (needed to preserve aspect ratio). */
	public record Preview(Identifier id, int width, int height) {
	}

	private static final Map<File, Preview> LOADED = new HashMap<>();
	private static int nextId = 0;

	private ScreenshotTextureCache() {
	}

	/** Returns the loaded texture for the given screenshot, loading it if necessary. Null on failure. */
	public static Preview get(File screenshot) {
		if (screenshot == null) {
			return null;
		}
		// Failures are cached as null entries on purpose: get() runs every frame, so
		// retrying a corrupt file would re-read it and log a warning ~60x/second.
		if (LOADED.containsKey(screenshot)) {
			return LOADED.get(screenshot);
		}
		Preview preview = load(screenshot);
		LOADED.put(screenshot, preview);
		return preview;
	}

	private static Preview load(File screenshot) {
		// NativeImageBackedTexture takes ownership of the NativeImage and closes it in
		// its own close(); closing it here (e.g. try-with-resources) would leave the
		// texture holding a freed native pointer.
		try (InputStream in = new FileInputStream(screenshot)) {
			NativeImage image = NativeImage.read(in);
			Identifier id = Identifier.of(VoxelCamClient.MOD_ID, "screenshot_" + (nextId++));
			NativeImageBackedTexture texture = new NativeImageBackedTexture(screenshot::getName, image);
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
			return new Preview(id, image.getWidth(), image.getHeight());
		} catch (IOException e) {
			VoxelCamClient.LOGGER.warn("Failed to load screenshot texture for {}", screenshot, e);
			return null;
		}
	}

	public static void release(File screenshot) {
		Preview preview = LOADED.remove(screenshot);
		if (preview != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(preview.id());
		}
	}

	public static void releaseAll() {
		for (Preview preview : LOADED.values()) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(preview.id());
		}
		LOADED.clear();
	}
}
