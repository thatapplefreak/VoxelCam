package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class ScreenshotNamer {

	private ScreenshotNamer() {
	}

	public static File getScreenshotName(File screenshotsDir) {
		String name = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());

		int suffix = 1;
		while (true) {
			File candidate = new File(screenshotsDir, name + (suffix == 1 ? "" : "_" + suffix) + ".png");
			if (!candidate.exists()) {
				return candidate;
			}
			suffix++;
		}
	}
}
