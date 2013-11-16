package com.thatapplefreak.voxelcam.bigpicture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.src.Minecraft;

import com.thatapplefreak.voxelcam.LiteModVoxelCam;
import com.thevoxelbox.common.util.PrivateFields;

public abstract class ScreenshotNamer {

	public static File getScreenshotName(String s) {
		s = s
		.replaceAll("DATE()", new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()))
		.replaceAll("SERVER()", PrivateFields.currentServerData.get(Minecraft.getMinecraft()) != null ? PrivateFields.currentServerData.get(Minecraft.getMinecraft()).serverName : "NoServer")
		;
		
		int var3 = 1;

		while (true) {
			File var1 = new File(LiteModVoxelCam.getScreenshotsDir(), s + (var3 == 1 ? "" : "_" + var3) + ".png");

			if (!var1.exists()) {
				return var1;
			}

			++var3;
		}
	}
}
