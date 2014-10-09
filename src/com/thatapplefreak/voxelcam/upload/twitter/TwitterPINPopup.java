package com.thatapplefreak.voxelcam.upload.twitter;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import com.thatapplefreak.voxelcam.upload.twitter.TwitterHandler.TwitterOauthGrabber;
import com.thevoxelbox.common.util.BrowserOpener;
import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class TwitterPINPopup extends GuiDialogBox {

	private GuiTextField pinBox;

	private boolean b = false;

	public TwitterPINPopup(GuiScreen parentScreen) {
		super(parentScreen, 200, 75, I18n.format("pleaseenterpin"));
	}

	@Override
	protected void onInitDialog() {
		pinBox = new GuiTextField(0xFFFFFF, fontRendererObj, width / 2 - (150 / 2), height / 2 - (16 / 2) - 8, 150, 16);
		BrowserOpener.openURLstringInBrowser(TwitterHandler.requestToken.getAuthorizationURL());
	}

	@Override
	public void onSubmit() {
		TwitterOauthGrabber grabber = TwitterHandler.getAGrabber(pinBox.getText(), this);
		new Thread(grabber).start();
	}

	@Override
	public boolean validateDialog() {
		return pinBox.getText().length() == 7;
	}

	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		super.drawDialog(mouseX, mouseY, f);
		pinBox.drawTextBox();
	}

	@Override
	protected void mouseClickedEx(int mouseX, int mouseY, int button) {
		super.mouseClickedEx(mouseX, mouseY, button);
		pinBox.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void onKeyTyped(char keyChar, int keyCode) {
		pinBox.textboxKeyTyped(keyChar, keyCode);
	}

	@Override
	protected void actionPerformed(GuiButton guibutton) {
		if (guibutton.id == this.btnCancel.id) {
			this.dialogResult = DialogResult.Cancel;
			this.closeDialog();
		}
		if (guibutton.id == this.btnOk.id) {
			if (this.validateDialog()) {
				this.dialogResult = DialogResult.OK;
				this.onSubmit();
			}
		}
	}

	public void goToPostGUI() {
		mc.displayGuiScreen(new TwitterPostPopup(getParentScreen()));
	}
}
