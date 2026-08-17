package com.thatapplefreak.voxelcam.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxelCamClient implements ClientModInitializer {

	public static final String MOD_ID = "voxelcam";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of(MOD_ID, "voxelcam"));

	private static VoxelCamConfig config;
	private static KeyBinding openScreenshotManagerKey;

	@Override
	public void onInitializeClient() {
		config = VoxelCamConfig.load();

		openScreenshotManagerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.voxelcam.openscreenshotmanager",
				InputUtil.Type.KEYSYM,
				InputUtil.GLFW_KEY_H,
				CATEGORY));

		LOGGER.info("VoxelCam initializing");
	}

	public static VoxelCamConfig getConfig() {
		return config;
	}

	public static KeyBinding getOpenScreenshotManagerKey() {
		return openScreenshotManagerKey;
	}
}
