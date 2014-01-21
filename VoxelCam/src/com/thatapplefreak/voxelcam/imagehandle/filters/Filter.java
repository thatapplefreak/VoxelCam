package com.thatapplefreak.voxelcam.imagehandle.filters;

import java.awt.image.BufferedImage;

public abstract class Filter {
	
	private final BufferedImage image;
	
	public Filter(BufferedImage image) {
		this.image = image;
	}
	
	public BufferedImage filterImage() {
		return this.getFilteredImage(this.image);
	}

	protected abstract BufferedImage getFilteredImage(BufferedImage img);
	
}
