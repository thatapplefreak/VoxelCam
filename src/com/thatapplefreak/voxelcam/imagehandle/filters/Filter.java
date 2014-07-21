package com.thatapplefreak.voxelcam.imagehandle.filters;

import java.awt.image.BufferedImage;

public interface Filter {
	
	public BufferedImage getFilteredImage(BufferedImage img);
	
}
