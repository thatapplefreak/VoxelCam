package com.thatapplefreak.voxelcam.client.screenshot;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A requested oversized-capture size: either fixed pixel dimensions or a multiple
 * of whatever the window happens to be. Multiples are kept unresolved until the
 * capture so a window resize does not leave the setting describing a stale aspect
 * ratio, which would silently re-frame the shot.
 */
public final class BigScreenshotSize {

	/** Nothing useful renders below this, and {@code Framebuffer.initFbo} dislikes tiny sizes. */
	private static final int MIN_EDGE = 16;

	private static final Pattern ABSOLUTE = Pattern.compile("(\\d{1,6})[x*](\\d{1,6})");
	private static final Pattern MULTIPLE = Pattern.compile("(\\d{1,2})x");

	private static final Map<String, BigScreenshotSize> PRESETS = new LinkedHashMap<>();

	static {
		PRESETS.put("2x", ofMultiple(2));
		PRESETS.put("4x", ofMultiple(4));
		PRESETS.put("8x", ofMultiple(8));
		PRESETS.put("hd", named("hd", 1280, 720));
		PRESETS.put("fhd", named("fhd", 1920, 1080));
		PRESETS.put("4k", named("4k", 3840, 2160));
		PRESETS.put("8k", named("8k", 7680, 4320));
		// The pre-2.0 settings panel's largest preset, kept for continuity.
		PRESETS.put("imax", named("imax", 10000, 7000));
	}

	public static final BigScreenshotSize DEFAULT = PRESETS.get("2x");

	private final String token;
	private final int multiple;
	private final int width;
	private final int height;

	private BigScreenshotSize(String token, int multiple, int width, int height) {
		this.token = token;
		this.multiple = multiple;
		this.width = width;
		this.height = height;
	}

	private static BigScreenshotSize ofMultiple(int multiple) {
		return new BigScreenshotSize(multiple + "x", multiple, 0, 0);
	}

	private static BigScreenshotSize ofPixels(int width, int height) {
		return new BigScreenshotSize(width + "x" + height, 0, width, height);
	}

	/** A preset reports the name it was asked for rather than the pixels behind it. */
	private static BigScreenshotSize named(String token, int width, int height) {
		return new BigScreenshotSize(token, 0, width, height);
	}

	/** @return the parsed size, or null if the token is not a preset, a multiple or WxH. */
	public static BigScreenshotSize parse(String input) {
		String token = input.trim().toLowerCase();

		BigScreenshotSize preset = PRESETS.get(token);
		if (preset != null) {
			return preset;
		}

		// WxH before Nx, so "2x2" is two pixels square rather than a mangled multiple.
		Matcher absolute = ABSOLUTE.matcher(token);
		if (absolute.matches()) {
			return ofPixels(Integer.parseInt(absolute.group(1)), Integer.parseInt(absolute.group(2)));
		}

		Matcher multiple = MULTIPLE.matcher(token);
		if (multiple.matches()) {
			int factor = Integer.parseInt(multiple.group(1));
			return factor >= 1 ? ofMultiple(factor) : null;
		}

		return null;
	}

	public static Iterable<String> tokens() {
		return PRESETS.keySet();
	}

	/** How the size was asked for — "4k", "2x", "1000x1000". */
	public String token() {
		return token;
	}

	/**
	 * Turns the setting into the dimensions to render at, clamped to what the GPU
	 * will actually allocate. The clamp is ours to apply: {@code Framebuffer.initFbo}
	 * throws above {@code getMaxTextureSize()}, and {@code WindowFramebuffer} does not
	 * override {@code resize}, so its forgiving size search never runs on this path.
	 */
	public Resolved resolve(Window window) {
		int wanted = multiple > 0 ? window.getWidth() * multiple : width;
		int wantedHeight = multiple > 0 ? window.getHeight() * multiple : height;

		int max = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();
		// Both axes shrink by the same factor. Clamping them independently would turn a 16:9
		// request into a square whenever only one axis overflowed, re-framing the shot — the
		// exact thing asking for a multiple of the window is meant to avoid.
		double scale = Math.min(1.0, Math.min((double) max / wanted, (double) max / wantedHeight));
		int clampedWidth = Math.clamp(Math.round(wanted * scale), MIN_EDGE, max);
		int clampedHeight = Math.clamp(Math.round(wantedHeight * scale), MIN_EDGE, max);

		return new Resolved(clampedWidth, clampedHeight,
				clampedWidth != wanted || clampedHeight != wantedHeight, max);
	}

	public record Resolved(int width, int height, boolean clamped, int maxTextureSize) {
	}
}
