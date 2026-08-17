package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Replaces GLImageMemoryHandler's raw GL11 texture loading with
 * NativeImage/NativeImageBackedTexture, keyed by screenshot file.
 */
public final class ScreenshotTextureCache {

	private static final Map<File, Identifier> LOADED = new HashMap<>();
	private static int nextId = 0;

	private ScreenshotTextureCache() {
	}

	/** Returns the texture identifier for the given screenshot, loading it if necessary. Null on failure. */
	public static Identifier get(File screenshot) {
		if (screenshot == null) {
			return null;
		}
		return LOADED.computeIfAbsent(screenshot, ScreenshotTextureCache::load);
	}

	private static Identifier load(File screenshot) {
		try (NativeImage image = NativeImage.read(new java.io.FileInputStream(screenshot))) {
			Identifier id = Identifier.of(VoxelCamClient.MOD_ID, "screenshot_" + (nextId++));
			NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> screenshot.getName(), image);
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
			return id;
		} catch (IOException e) {
			VoxelCamClient.LOGGER.warn("Failed to load screenshot texture for {}", screenshot, e);
			return null;
		}
	}

	public static void release(File screenshot) {
		Identifier id = LOADED.remove(screenshot);
		if (id != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
		}
	}

	public static void releaseAll() {
		for (Identifier id : LOADED.values()) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
		}
		LOADED.clear();
	}
}
