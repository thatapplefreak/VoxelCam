package com.thevoxelbox.voxelcam.popups;

import com.thevoxelbox.voxelcam.gui.GuiScreenShotManager;

public class RenamePopup extends GuiDialogBox {

	private GuiTextField renameBox;

	private String oldText;

	public RenamePopup(GuiScreen parentScreen, String oldName) {
		super(parentScreen, 200, 75, "Rename");
		this.oldText = oldName.replaceAll(".png", "");
	}

	@Override
	protected void onInitDialog() {
		renameBox = new GuiTextField(fontRenderer, width / 2 - (150 / 2), height / 2 - (16 / 2) - 8, 150, 16);
		renameBox.setText(oldText);
		renameBox.setFocused(true);
	}

	@Override
	public void onSubmit() {
		((GuiScreenShotManager) parentScreen).rename(renameBox.getText());
	}

	@Override
	public boolean validateDialog() {
		return true;
	}

	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		super.drawDialog(mouseX, mouseY, f);
		renameBox.drawTextBox();
	}

	@Override
	protected void mouseClickedEx(int mouseX, int mouseY, int button) {
		super.mouseClickedEx(mouseX, mouseY, button);
		renameBox.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void onKeyTyped(char keyChar, int keyCode) {
		renameBox.textboxKeyTyped(keyChar, keyCode);
	}

}