package com.thatapplefreak.voxelcam.gui.editor;

import java.awt.image.BufferedImage;

import com.thevoxelbox.common.util.gui.AdvancedDrawGui;

public class GuiEditScreenshot extends AdvancedDrawGui {

	/**
	 * This is the raw data for the Screenshot before undergoing changes
	 */
	private final BufferedImage uneditedScreenshot;
	
	/**
	 * This is the image created by adding the users edits
	 */
	private BufferedImage editedScreenshot;
	
	public GuiEditScreenshot(BufferedImage screenshot) {
		this.uneditedScreenshot = screenshot;
		
	}
	
	
}
