package com.thatapplefreak.voxelcam.imagehandle.metadata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.minecraft.client.Minecraft;

import com.mumfrey.liteloader.core.LiteLoader;

public abstract class MetaDataHandler {

	public static void writeMetaData(File screenshot) {
		Minecraft mc = Minecraft.getMinecraft();
		HashMap<String, String> map = new HashMap<String, String>();
		//Username
		map.put("username", "");
		//Biome Taken in
		map.put("biome", mc.theWorld != null ? mc.theWorld.getBiomeGenForCoords(mc.thePlayer.chunkCoordX, mc.thePlayer.chunkCoordZ).biomeName : "menu");
		
		//TODO
	}
	
	public static void readMetaData(File screenshot, String key) {
		//TODO
	}
	
	
}