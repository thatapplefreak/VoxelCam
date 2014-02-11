package com.thatapplefreak.voxelcam.gui.mainmenu;

import org.lwjgl.opengl.GL11;

import com.thevoxelbox.common.util.AbstractionLayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

public class PhotoButton extends GuiButton {
	
	static ResourceLocation photoBtnPic = new ResourceLocation("voxelcam", "textures/photo.png");

	public PhotoButton(int xPos, int yPos) {
		super(111195, xPos, yPos, 20, 20, "");
		
	}
	
	@Override
	public void drawButton(Minecraft p_146112_1_, int p_146112_2_, int p_146112_3_) {
		super.drawButton(p_146112_1_, p_146112_2_, p_146112_3_);
		AbstractionLayer.bindTexture(photoBtnPic);
		drawTexturedModalRect(this.field_146128_h, this.field_146129_i, 0, 0, this.field_146120_f, this.field_146121_g);
		// TODO Why is this not drawing for some reason?
	}
}