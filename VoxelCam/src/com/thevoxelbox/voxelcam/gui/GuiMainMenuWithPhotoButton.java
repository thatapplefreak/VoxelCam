package com.thevoxelbox.voxelcam.gui;

import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.Minecraft;
import net.minecraft.src.ResourceLocation;


public class GuiMainMenuWithPhotoButton extends GuiMainMenu {

	private static ResourceLocation photoBtnPic = new ResourceLocation("voxelcam", "textures/camerabtn.png");

	private PhotoButton photoBtn;

	private class PhotoButton extends GuiButton {

		public PhotoButton(int xPos, int yPos) {
			super(111195, xPos, yPos, 47, 47, "");
		}

		@Override
		public void drawButton(Minecraft mc, int mouseX, int mouseY) {
			mc.getTextureManager().bindTexture(photoBtnPic);
			if (mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height) {
				drawTexturedModalRect(this.xPosition, this.yPosition, 47, 0, this.width, this.height); // TODO
			} else {
				drawTexturedModalRect(this.xPosition, this.yPosition, 0, 0, this.width, this.height);
			}
		}

	}

	@Override
	public void initGui() {
		super.initGui();
		this.photoBtn = new PhotoButton(width - 52, 5);
		buttonList.add(photoBtn);
	}

	@Override
	protected void actionPerformed(GuiButton par1GuiButton) {
		super.actionPerformed(par1GuiButton);
		if (par1GuiButton.equals(photoBtn)) {
			mc.displayGuiScreen(new GuiScreenShotManager());
		}
	}

}
