package com.thatapplefreak.voxelcam.imagehandle.metadata;

import java.io.File;
import java.util.HashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;

public abstract class MetaDataHandler {

	public static void writeMetaData(File screenshot) {
		Minecraft mc = Minecraft.getMinecraft();
		HashMap<String, String> map = new HashMap<String, String>();
		//Username
		map.put("username", "");
		//Biome Taken in
		map.put("biome", mc.theWorld != null ? mc.theWorld.getBiomeGenForCoords(new BlockPos(mc.thePlayer.chunkCoordX, mc.thePlayer.chunkCoordY, mc.thePlayer.chunkCoordZ)).biomeName : "menu");
		
		//TODO
	}
	
	public static void readMetaData(File screenshot, String key) {
		//TODO
	}
	
	
}