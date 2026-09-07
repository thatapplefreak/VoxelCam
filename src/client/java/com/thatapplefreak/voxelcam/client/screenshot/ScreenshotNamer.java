package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScreenshotNamer {

	// The default locale must not reach the filename: SimpleDateFormat renders the numeric
	// fields in that locale's own digit set (Arabic-Indic under fa-IR) and its own calendar
	// (year 2568 under th-TH), while the manager's friendly-label gate in
	// ScreenshotMetadata.displayName is an ASCII-only \d regex over the name. Locale.ROOT
	// pins the digits and a LocalDateTime pins the ISO chronology.
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

	// "_burst" rather than a bare digit suffix so this can never be confused with
	// getScreenshotName's own "_2", "_3" collision suffix in either direction: that one is
	// "_\d+" with no letters, this one always has "burst" between the underscore and the
	// digits. Deliberately no UNICODE_CHARACTER_CLASS — this pattern is asked to match names
	// this class itself produces, which are ASCII by construction (Locale.ROOT above).
	// Two groups: (1) everything before the tag, (2) the tag itself without its extension —
	// so a group rename can lift group(2) off the old name and attach it to the new one,
	// carrying a frame's position across untouched rather than renumbering it.
	private static final Pattern BURST_FRAME_SUFFIX = Pattern.compile("(.+)(_burst\\d+)\\.png$");

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

	/**
	 * The names one burst will write: the key under an ordinary capture name at index 0, then
	 * {@code count - 1} sub-frames derived from it.
	 *
	 * <p>Resolved in one synchronous pass on the render thread rather than one name per consumer a
	 * frame apart: {@link #getScreenshotName} probes for a free path without creating it, so two
	 * overlapping captures resolving names independently could pick the same one and clobber each
	 * other. The files are deliberately NOT created here to reserve them — {@link
	 * ScreenshotHandler#writeOrDiscard} keys its cleanup on whether the target already existed, so
	 * a pre-created placeholder would turn every failed encode into a 0-byte .png left in the
	 * folder, which is the exact thing that method exists to prevent. Serialising captures ({@code
	 * Burst.isBusy()} in every capture guard) is what closes the race instead.
	 *
	 * <p>Bounded by {@code Burst.MAX_LENGTH}: at most a handful of extra {@code File.exists()}
	 * probes, paid once per burst on the render thread, not per frame and not per row.
	 */
	public static List<File> reserveBurstNames(File screenshotsDir, int count) {
		List<File> names = new ArrayList<>(count);
		File key = getScreenshotName(screenshotsDir);
		names.add(key);

		String keyName = key.getName();
		int fileSuffix = 1;
		for (int index = 1; index < count; index++) {
			File candidate;
			do {
				candidate = new File(screenshotsDir, burstFrameName(keyName, fileSuffix));
				fileSuffix++;
			} while (candidate.exists());
			names.add(candidate);
		}
		return names;
	}

	/**
	 * The key screenshot a burst sub-frame belongs to, as a {@code .png} file name — or null if
	 * this is not one.
	 *
	 * <p>Takes the name rather than a {@code File} on purpose: the collapse this drives runs for
	 * every entry on every search keystroke and every window-resize event, so it may not stat
	 * anything, and a {@code String} parameter makes that unforgeable rather than merely tested.
	 * Called with an already-lowercased name, matching {@code
	 * VoxelCamIO.updateScreenShotFilesList}.
	 *
	 * <p>A hand-named {@code holiday_burst01.png} groups under {@code holiday.png} if that file
	 * happens to exist — accepted rather than guarded against, since requiring the key part to
	 * look like a capture name would break group rename (a renamed key is just whatever the
	 * player typed). The file is only ever hidden by this, never unreachable.
	 */
	public static String burstKeyName(String pngFileName) {
		Matcher matcher = BURST_FRAME_SUFFIX.matcher(pngFileName);
		return matcher.matches() ? matcher.group(1) + ".png" : null;
	}

	/**
	 * The {@code "_burstNN"} tag of a sub-frame's name, without its extension, or null if it is
	 * not one. Lets a group rename move a frame's position tag onto a new base name rather than
	 * recomputing it, so the frame keeps whatever index it was captured at.
	 */
	public static String burstFrameSuffix(String pngFileName) {
		Matcher matcher = BURST_FRAME_SUFFIX.matcher(pngFileName);
		return matcher.matches() ? matcher.group(2) : null;
	}

	/** The name of frame {@code index} (1-based; 0 is the key itself, named plainly) of the burst
	 * keyed by {@code keyName}. */
	public static String burstFrameName(String keyName, int index) {
		String base = keyName.endsWith(".png") ? keyName.substring(0, keyName.length() - 4) : keyName;
		return base + "_burst" + String.format(Locale.ROOT, "%02d", index) + ".png";
	}
}
