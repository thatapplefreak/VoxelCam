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
		if (mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height) {
			drawTexturedModalRect(this.xPosition, this.yPosition, 47, 0, this.width, this.height); // TODO
		} else {
			drawTexturedModalRect(this.xPosition, this.yPosition, 0, 0, this.width, this.height);
		}
	}

}