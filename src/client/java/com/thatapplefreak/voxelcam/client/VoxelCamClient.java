package com.thatapplefreak.voxelcam.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxelCamClient implements ClientModInitializer {

	public static final String MOD_ID = "voxelcam";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("VoxelCam initializing");
	}
}
