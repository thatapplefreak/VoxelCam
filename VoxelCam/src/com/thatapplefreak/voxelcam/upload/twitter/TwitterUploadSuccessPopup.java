package com.thatapplefreak.voxelcam.upload.twitter;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import twitter4j.TwitterException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.thatapplefreak.voxelcam.upload.imgur.ImgurDelete;
import com.thevoxelbox.common.util.BrowserOpener;
import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class TwitterUploadSuccessPopup extends GuiDialogBox {
	
	private final String url;
	private final long postID;
	
	private GuiButton btnView, btnClipboard;

	public TwitterUploadSuccessPopup(GuiScreen parentScreen, long postID, String url) {
		super(parentScreen, 320, 80, "Post to Twitter succeeded");
		this.postID = postID;
		this.url = url;
	}
	
	@Override
	protected void onInitDialog() {
		btnCancel.displayString = "Undo";
		btnView = new GuiButton(100, dialogX + dialogWidth - 248, dialogY + dialogHeight - 22, 60, 20, "Open");
		buttonList.add(btnView);
		btnClipboard = new GuiButton(200, dialogX + dialogWidth - 186, dialogY + dialogHeight - 22, 60, 20, "Copy Link");
		buttonList.add(btnClipboard);
	}
	
	@Override
	protected void actionPerformed(GuiButton guibutton) {
		if (guibutton.id == btnCancel.id) {
			new Thread("Twitter Post Undo Thread") {
				public void run() {
					try {
						TwitterHandler.twitter.destroyStatus(postID);
					} catch (TwitterException e) {
						e.printStackTrace();
					}
				}
			}.start();			
		} else if (guibutton.id == btnView.id) {
			BrowserOpener.openURLstringInBrowser(url);
		} else if (guibutton.id == btnClipboard.id) {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
		}

		super.actionPerformed(guibutton);
	}
	
	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		drawCenteredString(fontRenderer, "Post completed successfully", dialogX + (dialogWidth / 2), dialogY + 18, 0xFFFFAA00);
		drawCenteredString(fontRenderer, this.url, dialogX + (dialogWidth / 2), dialogY + 32, 0xFFFFFF55);
	}

	@Override
	public boolean validateDialog() {
		return true;
	}

	@Override
	public void onSubmit() {
	}
}
