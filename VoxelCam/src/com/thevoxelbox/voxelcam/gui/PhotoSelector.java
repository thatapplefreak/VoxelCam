package com.thevoxelbox.voxelcam.gui;

import java.io.File;
import java.text.SimpleDateFormat;

import net.minecraft.src.Tessellator;

import org.lwjgl.opengl.GL11;


public class PhotoSelector extends GuiTextSlot {
	final GuiScreenShotManager parent;
	SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm aa");

	public PhotoSelector(GuiScreenShotManager parent, int listWidth) {
		super(listWidth, parent.height, 32, (parent.height - 55) + 4, 10, 35);
		this.parent = parent;
	}

	@Override
	protected int getContentHeight() {
		return (this.getSize()) * 35 + 1;
	}

	@Override
	protected int getSize() {
		return GuiScreenShotManager.getScreenShotFiles().size();
	}

	@Override
	protected boolean isSelected(int i) {
		return parent.getSelected(i);
	}

	@Override
	protected void drawBackground() {
	}

	@Override
	protected void drawSlot(int i, int j, int k, int l, Tessellator tessellator) {
		File pic = GuiScreenShotManager.getScreenShotFiles().get(i);
		if (pic != null) {
			GL11.glEnable(GL11.GL_BLEND);
			parent.getFontRenderer(parent).drawString(parent.getFontRenderer(parent).trimStringToWidth(pic.getName().replace(".png", ""), listWidth - 10), 13, k + 2, 0xFFFFFF);
			parent.getFontRenderer(parent).drawString(parent.getFontRenderer(parent).trimStringToWidth(sdf.format(pic.lastModified()), listWidth - 10), 13, k + 12, 0xCCCCCC);
		}
	}

	@Override
	protected void elementClicked(int i, boolean flag) {
		parent.selectPhotoIndex(i);
	}

	public void setDimensionsAndPosition(int x, int y, int x2, int y2) {
		this.left = x;
		this.top = y;
		this.right = x2;
		this.bottom = y2;
		this.listWidth = x2 - x + 7;
	}
}