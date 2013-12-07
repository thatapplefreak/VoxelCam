package com.thatapplefreak.voxelcam.gui.mainmenu;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

import com.mumfrey.liteloader.core.LiteLoader;
import com.thatapplefreak.voxelcam.LiteModVoxelCam;
import com.thatapplefreak.voxelcam.VoxelCamConfig;
import com.thevoxelbox.common.util.BrowserOpener;
import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class FirstRunPopup extends GuiDialogBox {

	private static final ResourceLocation avatarPNG = new ResourceLocation("voxelcam", "textures/avatar.png");
	
	private GuiButton forumLink;

	public FirstRunPopup(GuiScreen parentScreen) {
		super(parentScreen, 320, 150, "VoxelCam");
	}

	@Override
	protected void onInitDialog() {
		btnCancel.drawButton = false;
		btnOk.displayString = "OK";
		forumLink = new GuiButton(-111195, btnCancel.xPosition, btnCancel.yPosition, 60, 20, "More info");
		buttonList.add(forumLink);
	}

	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		try {
			drawString(fontRenderer, "Thank you for downloading VoxelCam version " + LiteLoader.getInstance().getMod("VoxelCam").getVersion() + " ", dialogX + 5, dialogY + 5, 0xFFFFFF);
		} catch (Exception e) {
		}
		drawString(fontRenderer, "Keybindings:", dialogX + 5, dialogY + 20, 0x990000);
		drawString(fontRenderer, "H - Open the screenshots manager while in game", dialogX + 10, dialogY + 30, 0x990000);
		drawString(fontRenderer, "Shift + F2 - Take a HUGE screenhsot", dialogX + 10, dialogY + 40, 0x990000);
		drawString(fontRenderer, "F7 - Open VoxelOptions", dialogX + 10, dialogY + 50, 0x990000);

		drawString(fontRenderer, "Developer:", dialogX + 5, dialogY + 70, 0x00FFFF);
		drawTexturedModalRect(avatarPNG, dialogX + 10, dialogY + 80, dialogX + 75, dialogY + 140, 0, 0, 259, 256);
		drawString(fontRenderer, "thatapplefreak", dialogX + 6, dialogY + 141, 0xFFFF00);

		drawString(fontRenderer, "Twitter: @xApplefreak", dialogX + 80, dialogY + 80, 0x4099FF);
		drawString(fontRenderer, "Reddit: thatapplefreak", dialogX + 80, dialogY + 90, 0xff4500);
		drawString(fontRenderer, "MinecraftForum: thatapplefreak", dialogX + 80, dialogY + 100, 0x80ba59);
	}
	
	@Override
	protected void actionPerformed(GuiButton guibutton) {
		if (guibutton.equals(forumLink)) {
			BrowserOpener.openURLstringInBrowser("http://bit.ly/16LXtjV");
		}
		super.actionPerformed(guibutton);
	}

	@Override
	public void onSubmit() {
		LiteModVoxelCam.getConfig().setProperty(VoxelCamConfig.FIRSTRUN, false);
	}

	@Override
	public boolean validateDialog() {
		return true;
	}

}
