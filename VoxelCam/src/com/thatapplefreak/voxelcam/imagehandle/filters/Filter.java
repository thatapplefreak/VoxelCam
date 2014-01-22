package com.thatapplefreak.voxelcam.imagehandle.filters;

import java.awt.image.BufferedImage;

public interface Filter {
	
	public abstract BufferedImage getFilteredImage(BufferedImage img);
	
}
