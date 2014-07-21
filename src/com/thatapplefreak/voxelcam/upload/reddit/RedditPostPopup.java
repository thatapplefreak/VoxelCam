package com.thatapplefreak.voxelcam.upload.reddit;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import com.thatapplefreak.voxelcam.imagehandle.ScreenshotIncapable;
import com.thatapplefreak.voxelcam.io.VoxelCamIO;
import com.thevoxelbox.common.util.AbstractionLayer;
import com.thevoxelbox.common.util.gui.GuiDialogBox;
import com.thevoxelbox.common.util.gui.GuiTextFieldEx;

public class RedditPostPopup extends GuiDialogBox implements IRedditPostCallback, ScreenshotIncapable {
	
	private static final ResourceLocation karmapony = new ResourceLocation("voxelcam", "textures/karmapony.png");
	
	private GuiTextFieldEx titleField, subredditField;
	
	private boolean failed = false;

	private boolean posting = false;;

	public RedditPostPopup(GuiScreen parentScreen) {
		super(parentScreen, 250, 130, I18n.format("postto") + " Reddit"); //TODO Translate
	}
	
	@Override
	protected void onInitDialog() {
		super.onInitDialog();
		btnOk.displayString = I18n.format("post");
		titleField = new GuiTextFieldEx(fontRendererObj, dialogX + 10, dialogY + 25, 230, 20, VoxelCamIO.getSelectedPhoto().getName().replaceAll(".png", ""));
		subredditField = new GuiTextFieldEx(fontRendererObj, dialogX + 10, dialogY + 70, 230, 20, "minecraft");
	}
	
	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		titleField.setVisible(!posting);
		subredditField.setVisible(!posting);
		if (!posting) {
			if (failed) {
				drawCenteredString(fontRendererObj, I18n.format("failedtopost"), dialogX + dialogWidth / 2, dialogY + dialogHeight - 35, 0xFF0000);
			}
			drawString(fontRendererObj, I18n.format("posttitle") + ":", dialogX + 10, dialogY + 10, 0xFFFFFF);
			titleField.drawTextBox();
			drawString(fontRendererObj, I18n.format("subreddit") + ":", dialogX + 10,	dialogY + 55, 0xFFFFFF);
			subredditField.drawTextBox();
		} else {
			drawCenteredString(fontRendererObj, I18n.format("posting") + "...", dialogX + dialogWidth / 2, dialogY + dialogHeight / 2 - 10, 0xFFFFFF);
		}
		
		if (subredditField.getText().equals("mylittlepony")) { //EE
			setTexMapSize(180);
			drawTexturedModalRect(karmapony, width - 150, height - 130, width, height, 0, 0, 180, 163);
		}
	}
	
	@Override
	protected void mouseClickedEx(int mouseX, int mouseY, int button) {
		super.mouseClickedEx(mouseX, mouseY, button);
		titleField.mouseClicked(mouseX, mouseY, button);
		subredditField.mouseClicked(mouseX, mouseY, button);
	}
	
	@Override
	protected void onKeyTyped(char keyChar, int keyCode) {
		super.onKeyTyped(keyChar, keyCode);
		titleField.textboxKeyTyped(keyChar, keyCode);
		subredditField.textboxKeyTyped(keyChar, keyCode);
	}
	
	/**
	 * Handle a button event
	 * 
	 * @param guibutton Button or control which sourced the event
	 */
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

	@Override
	public void onSubmit() {
		posting = true;
		RedditHandler.doRedditPost(titleField.getText(), subredditField.getText(), VoxelCamIO.getSelectedPhoto(), this);
	}

	@Override
	public boolean validateDialog() {
		return true;
	}

	@Override
	public void onPostSuccess(String postUrl) {
		AbstractionLayer.getMinecraft().displayGuiScreen(new RedditPostSuccessPopup(getParentScreen(), postUrl));
	}

	@Override
	public void onPostFailure() {
		posting = false;
		failed = true;
	}

}
