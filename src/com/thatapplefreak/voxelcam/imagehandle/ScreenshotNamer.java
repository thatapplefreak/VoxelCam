package com.thatapplefreak.voxelcam.imagehandle;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.thatapplefreak.voxelcam.VoxelCamCore;

public abstract class ScreenshotNamer {

	public static File getScreenshotName() {
		
		String name = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
		
		int var3 = 1;

		while (true) {
			File var1 = new File(VoxelCamCore.getScreenshotsDir(), name + (var3 == 1 ? "" : "_" + var3) + ".png");

			if (!var1.exists()) {
				return var1;
			}

			++var3;
		}
	}
}
