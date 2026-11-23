package com.thatapplefreak.voxelcam.client.screenshot;

/**
 * One animation frame as a patch over the frame before it: the smallest rectangle holding every
 * pixel that changed, its corner rounded down to even coordinates, with the unchanged pixels
 * inside it fully transparent so they blend away. A burst's frames are a tenth of a second apart,
 * so most of each one is the frame before, and both animation formats store only this.
 *
 * <p>Even coordinates because WebP's ANMF stores the offset halved; APNG would take any offset,
 * and sharing one rule keeps the two files' frames identical. A frame that changed nothing is a
 * single transparent pixel at the corner, since neither format has an empty frame.
 *
 * @param pixels ARGB, row by row, {@code width * height} of them.
 */
public record FramePatch(int x, int y, int width, int height, int[] pixels) {

	/** The whole frame, for the first one, which has nothing to patch over. */
	public static FramePatch whole(int[] argb, int width, int height) {
		return new FramePatch(0, 0, width, height, argb);
	}

	/** {@code after} as a patch over {@code before}; both canvas-sized and fully opaque. */
	public static FramePatch between(int[] before, int[] after, int width, int height) {
		int minX = width;
		int minY = height;
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < height; y++) {
			int row = y * width;
			for (int x = 0; x < width; x++) {
				if (before[row + x] != after[row + x]) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = y;
				}
			}
		}
		if (maxX < 0) {
			return new FramePatch(0, 0, 1, 1, new int[] { 0 });
		}
		int x0 = minX & ~1;
		int y0 = minY & ~1;
		int w = maxX - x0 + 1;
		int h = maxY - y0 + 1;
		int[] patch = new int[w * h];
		for (int y = 0; y < h; y++) {
			int from = (y0 + y) * width + x0;
			for (int x = 0; x < w; x++) {
				int pixel = after[from + x];
				patch[y * w + x] = pixel == before[from + x] ? 0 : pixel;
			}
		}
		return new FramePatch(x0, y0, w, h, patch);
	}

	/**
	 * Lays this patch onto {@code canvas} the way both formats' "blend over" does for the only two
	 * alphas a patch holds: transparent keeps what is there, opaque replaces it. Anything in
	 * between is mixed properly, for a file this mod did not write.
	 */
	public void composite(int[] canvas, int canvasWidth, boolean blend) {
		for (int row = 0; row < height; row++) {
			int to = (y + row) * canvasWidth + x;
			for (int col = 0; col < width; col++) {
				int pixel = pixels[row * width + col];
				int alpha = pixel >>> 24;
				if (!blend || alpha == 0xFF) {
					canvas[to + col] = pixel;
				} else if (alpha != 0) {
					canvas[to + col] = over(pixel, canvas[to + col]);
				}
			}
		}
	}

	private static int over(int top, int bottom) {
		int a = top >>> 24;
		int out = 0xFF000000;
		for (int shift = 0; shift < 24; shift += 8) {
			int t = (top >>> shift) & 0xFF;
			int b = (bottom >>> shift) & 0xFF;
			out |= ((t * a + b * (255 - a) + 127) / 255) << shift;
		}
		return out;
	}
}
