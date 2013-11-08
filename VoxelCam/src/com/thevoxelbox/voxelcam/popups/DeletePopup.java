package com.thevoxelbox.voxelcam.popups;

import net.minecraft.src.GuiScreen;

import com.thevoxelbox.common.util.gui.GuiDialogBox;
import com.thevoxelbox.voxelcam.gui.GuiScreenShotManager;

public class DeletePopup extends GuiDialogBox {

	public DeletePopup(GuiScreen parentScreen) {
		super(parentScreen, 200, 75, "Delete");
	}

	@Override
	public void onSubmit() {
		((GuiScreenShotManager) parentScreen).delete();
	}

	@Override
	protected void onInitDialog() {
		this.btnOk.displayString = "Yes";
		this.btnCancel.displayString = "No";

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
		drawCenteredString(fontRenderer, "Are You Sure?", width / 2, height / 2 - 12, 0xffffff);
	}

}
