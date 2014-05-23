package com.thatapplefreak.voxelcam.imagehandle;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.client.Minecraft;

import com.thatapplefreak.voxelcam.VoxelCamCore;
import com.thevoxelbox.common.util.PrivateFields;

public abstract class ScreenshotNamer {

	public static File getScreenshotName() {
		
		int var3 = 1;

		while (true) {
			File var1 = new File(VoxelCamCore.getScreenshotsDir(), (var3 == 1 ? "" : "_" + var3) + ".png");

			if (!var1.exists()) {
				return var1;
			}

			++var3;
		}
	}
}
