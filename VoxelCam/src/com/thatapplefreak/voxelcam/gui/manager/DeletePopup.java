package com.thatapplefreak.voxelcam.gui.manager;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.thatapplefreak.voxelcam.imagehandle.ScreenshotIncapable;
import com.thatapplefreak.voxelcam.io.VoxelCamIO;
import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class DeletePopup extends GuiDialogBox implements ScreenshotIncapable {

	public DeletePopup(GuiScreen parentScreen) {
		super(parentScreen, 200, 75, I18n.format("delete"));
	}

	@Override
	public void onSubmit() {
		VoxelCamIO.delete();
	}

	@Override
	protected void onInitDialog() {
		this.btnOk.displayString = I18n.format("yes");
		this.btnCancel.displayString = I18n.format("no");

	}

	@Override
	protected void onKeyTyped(char keyChar, int keyCode) {
		if (keyChar == 'y') {
			actionPerformed(btnOk);
		} else if (keyChar == 'n') {
			actionPerformed(btnCancel);
		}
	}

	@Override
	public boolean validateDialog() {
		return true;
	}

	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		super.drawDialog(mouseX, mouseY, f);
		drawCenteredString(fontRendererObj, I18n.format("areyousure") + "?", width / 2, height / 2 - 12, 0xffffff);
	}

}
