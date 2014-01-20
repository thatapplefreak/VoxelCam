package com.thatapplefreak.voxelcam.gui.mainmenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class PhotoButton extends GuiButton {

	public PhotoButton(int xPos, int yPos) {
		super(111195, xPos, yPos, 47, 47, "");
	}

	@Override
	public void drawButton(Minecraft mc, int mouseX, int mouseY) {
		mc.getTextureManager().bindTexture(GuiMainMenuWithPhotoButton.photoBtnPic);
		if (mouseX >= this.field_146128_h && mouseY >= this.field_146129_i && mouseX < this.field_146128_h + this.field_146120_f && mouseY < this.field_146129_i + this.field_146121_g) {
			drawTexturedModalRect(this.field_146128_h, this.field_146129_i, 47, 0, this.field_146120_f, this.field_146121_g); // TODO
		} else {
			drawTexturedModalRect(this.field_146128_h, this.field_146129_i, 0, 0, this.field_146120_f, this.field_146121_g);
		}
	}

}