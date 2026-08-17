package com.thatapplefreak.voxelcam.client;

import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

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

		ClientTickEvents.END_CLIENT_TICK.register(VoxelCamClient::onEndTick);

		LOGGER.info("VoxelCam initializing");
	}

	private static void onEndTick(MinecraftClient client) {
		while (openScreenshotManagerKey.wasPressed()) {
			if (client.currentScreen == null) {
				File screenshotsDir = new File(client.runDirectory, ScreenshotRecorder.SCREENSHOTS_DIRECTORY);
				client.setScreen(new GuiScreenShotManager(screenshotsDir));
			}
		}
	}

	public static VoxelCamConfig getConfig() {
		return config;
	}

	public static KeyBinding getOpenScreenshotManagerKey() {
		return openScreenshotManagerKey;
	}
}
