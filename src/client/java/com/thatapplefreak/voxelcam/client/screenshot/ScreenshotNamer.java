package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ScreenshotNamer {

	// The default locale must not reach the filename: SimpleDateFormat renders the numeric
	// fields in that locale's own digit set (Arabic-Indic under fa-IR) and its own calendar
	// (year 2568 under th-TH), while the manager's friendly-label gate in
	// ScreenshotMetadata.displayName is an ASCII-only \d regex over the name. Locale.ROOT
	// pins the digits and a LocalDateTime pins the ISO chronology.
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

	private ScreenshotNamer() {
	}

	public static File getScreenshotName(File screenshotsDir) {
		String name = STAMP.format(LocalDateTime.now());

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
