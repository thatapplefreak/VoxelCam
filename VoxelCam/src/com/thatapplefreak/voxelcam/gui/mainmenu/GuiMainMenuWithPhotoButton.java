package com.thatapplefreak.voxelcam.gui.mainmenu;

import com.thatapplefreak.voxelcam.gui.manager.GuiScreenShotManager;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ResourceLocation;

public class GuiMainMenuWithPhotoButton extends GuiMainMenu {

	static ResourceLocation photoBtnPic = new ResourceLocation("voxelcam", "textures/camerabtn.png");

	private PhotoButton photoBtn;

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
