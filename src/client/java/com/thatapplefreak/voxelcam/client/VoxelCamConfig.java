package com.thatapplefreak.voxelcam.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class VoxelCamConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxelcam.json");

	public boolean firstRun = true;
	public int photoWidth = 1920;
	public int photoHeight = 1080;

	public boolean autoUpload = false;
	public boolean autoUploadImgur = false;
	public boolean autoUploadDropbox = false;
	public boolean autoUploadGoogleDrive = false;

	/** Register a free anonymous app at https://api.imgur.com/oauth2/addclient and paste its Client ID here. */
	public String imgurClientId = "";

	public static VoxelCamConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (var reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				VoxelCamConfig config = GSON.fromJson(reader, VoxelCamConfig.class);
				if (config != null) {
					return config;
				}
			} catch (IOException e) {
				VoxelCamClient.LOGGER.warn("Failed to read voxelcam.json, using defaults", e);
			}
		}
		return new VoxelCamConfig();
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (var writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			VoxelCamClient.LOGGER.warn("Failed to write voxelcam.json", e);
		}
	}
}
