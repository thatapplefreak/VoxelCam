package com.thatapplefreak.voxelcam.gui.manager;

import net.minecraft.client.gui.GuiScreen;

import com.thatapplefreak.voxelcam.imagehandle.ScreenshotIncapable;
import com.thatapplefreak.voxelcam.lang.Translator;
import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class DeletePopup extends GuiDialogBox implements ScreenshotIncapable {

	public DeletePopup(GuiScreen parentScreen) {
		super(parentScreen, 200, 75, Translator.translate("delete"));
	}

	@Override
	public void onSubmit() {
		((GuiScreenShotManager) getParentScreen()).delete();
	}

	@Override
	protected void onInitDialog() {
		this.btnOk.displayString = Translator.translate("yes");
		this.btnCancel.displayString = Translator.translate("no");

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
		drawCenteredString(fontRendererObj, Translator.translate("areyousure") + "?", width / 2, height / 2 - 12, 0xffffff);
	}

}
