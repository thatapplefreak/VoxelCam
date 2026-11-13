package com.thatapplefreak.voxelcam.client.screenshot;

/** Operations on ARGB pixel arrays, row by row, shared by the share copy and the preview player. */
public final class ArgbImages {

	private ArgbImages() {
	}

	/**
	 * Area-averaged: each output pixel is the mean of the source pixels it covers, weighted by how
	 * much of each it covers. Smooth at any ratio, where nearest-neighbour would shimmer on the
	 * block edges that Minecraft is made of.
	 */
	public static int[] downscale(int[] argb, int width, int height, int newWidth, int newHeight) {
		int[] out = new int[newWidth * newHeight];
		double sx = (double) width / newWidth;
		double sy = (double) height / newHeight;
		for (int y = 0; y < newHeight; y++) {
			double y0 = y * sy;
			double y1 = y0 + sy;
			for (int x = 0; x < newWidth; x++) {
				double x0 = x * sx;
				double x1 = x0 + sx;
				double r = 0;
				double g = 0;
				double b = 0;
				double total = 0;
				for (int py = (int) y0; py < Math.min(height, Math.ceil(y1)); py++) {
					double wy = Math.min(py + 1, y1) - Math.max(py, y0);
					for (int px = (int) x0; px < Math.min(width, Math.ceil(x1)); px++) {
						double weight = wy * (Math.min(px + 1, x1) - Math.max(px, x0));
						int p = argb[py * width + px];
						r += ((p >>> 16) & 0xFF) * weight;
						g += ((p >>> 8) & 0xFF) * weight;
						b += (p & 0xFF) * weight;
						total += weight;
					}
				}
				out[y * newWidth + x] = 0xFF000000 | ((int) Math.round(r / total) << 16)
						| ((int) Math.round(g / total) << 8) | (int) Math.round(b / total);
			}
		}
		return out;
	}
}
