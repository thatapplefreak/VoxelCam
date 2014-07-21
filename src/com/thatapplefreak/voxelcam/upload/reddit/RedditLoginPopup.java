package com.thatapplefreak.voxelcam.upload.reddit;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.thatapplefreak.voxelcam.VoxelCamConfig;
import com.thatapplefreak.voxelcam.VoxelCamCore;
import com.thatapplefreak.voxelcam.imagehandle.ScreenshotIncapable;
import com.thevoxelbox.common.util.AbstractionLayer;
import com.thevoxelbox.common.util.gui.GuiDialogBox;
import com.thevoxelbox.common.util.gui.GuiTextFieldEx;
import com.thevoxelbox.common.util.gui.GuiDialogBox.DialogResult;

public class RedditLoginPopup extends GuiDialogBox implements ILoginCallback, ScreenshotIncapable {
	
	private boolean failed = false;
	private boolean loggingIn = false;
	
	private GuiTextFieldEx usernameField,passwordField;

	public RedditLoginPopup(GuiScreen parentScreen) {
		super(parentScreen, 200, 100, I18n.format("pleaseloginto") + " Reddit"); //TODO Translate
	}
	
	@Override
	protected void onInitDialog() {
		super.onInitDialog();
		btnOk.displayString = I18n.format("login"); //TODO Traslate
		usernameField = new GuiTextFieldEx(fontRendererObj, dialogX + 65, dialogY + 18, 130, 15, VoxelCamCore.getConfig().getStringProperty(VoxelCamConfig.REDDITUSERNAME));
		passwordField = new GuiTextFieldEx(fontRendererObj, dialogX + 65, dialogY + 48, 130, 15, VoxelCamCore.getConfig().getStringProperty(VoxelCamConfig.REDDITPASSWORD));
		usernameField.setFocused(true);
	}
	
	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		super.drawDialog(mouseX, mouseY, f);
		if (!loggingIn) {
			usernameField.setVisible(true);
			passwordField.setVisible(true);
			if (failed) {
				drawCenteredString(fontRendererObj, I18n.format("loginfailpleasetryagain"), dialogX + dialogWidth / 2, dialogY + 4, 0xFF0000);
			}
			drawString(fontRendererObj, I18n.format("username") + ":", dialogX + 5, dialogY + 20,	0xFFFFFF);
			drawString(fontRendererObj, I18n.format("password") + ":", dialogX + 5, dialogY + 50,	0xFFFFFF);
		} else {
			usernameField.setVisible(false);
			passwordField.setVisible(false);
			drawCenteredString(fontRendererObj, I18n.format("loggingin") + "...", dialogX + dialogWidth / 2, dialogY + dialogHeight / 2 - 10, 0xFFFFFF);
		}
		usernameField.drawTextBox();
		passwordField.drawTextBox();
	}
	
	@Override
	public void onLoginSuccess() {
		VoxelCamCore.getConfig().setProperty(VoxelCamConfig.REDDITUSERNAME, usernameField.getText());
		VoxelCamCore.getConfig().setProperty(VoxelCamConfig.REDDITPASSWORD, passwordField.getText());
		AbstractionLayer.getMinecraft().displayGuiScreen(new RedditPostPopup(getParentScreen()));
	}
	
	@Override
	public void onLoginFailure() {
		loggingIn = false;
		failed = true;
	}
	
	/**
	 * Handle a button event
	 * 
	 * @param guibutton Button or control which sourced the event
	 */
	@Override
 	protected void actionPerformed(GuiButton guibutton) {
		if (guibutton.id == this.btnCancel.id) {
			dialogResult = DialogResult.Cancel;
			closeDialog();
		}
		if (guibutton.id == this.btnOk.id) {
			if (validateDialog()) {
				dialogResult = DialogResult.OK;
				onSubmit();
			}
		}
	}
	
	@Override
	protected void onKeyTyped(char keyChar, int keyCode) {
		super.onKeyTyped(keyChar, keyCode);
		usernameField.textboxKeyTyped(keyChar, keyCode);
		passwordField.textboxKeyTyped(keyChar, keyCode);
	}
	
	@Override
	protected void mouseClickedEx(int mouseX, int mouseY, int button) {
		super.mouseClickedEx(mouseX, mouseY, button);
		usernameField.mouseClicked(mouseX, mouseY, button);
		passwordField.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void onSubmit() {
		loggingIn = true;
		RedditHandler.login(usernameField.getText(), passwordField.getText(), this);
	}

	@Override
	public boolean validateDialog() {
		return true;
	}

}
