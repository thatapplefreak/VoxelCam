package com.thatapplefreak.voxelcam.upload.imgur;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.thevoxelbox.common.util.BrowserOpener;
import com.thevoxelbox.common.util.gui.GuiDialogBox;

public class ImgurUploadSuccessPopup  extends GuiDialogBox {
	
	private final String deleteHash;
	private final String url;
	
	private GuiButton btnView, btnClipboard;

	public ImgurUploadSuccessPopup(GuiScreen parentScreen, String deleteHash, String url) {
		super(parentScreen, 300, 80, I18n.format("imguruploadsuccess"));
		this.deleteHash = deleteHash;
		this.url = url;
	}
	
	@Override
	protected void onInitDialog() {
		btnCancel.displayString = I18n.format("undo");
		btnView = new GuiButton(100, dialogX + dialogWidth - 248, dialogY + dialogHeight - 22, 60, 20, I18n.format("open"));
		buttonList.add(btnView);
		btnClipboard = new GuiButton(200, dialogX + dialogWidth - 186, dialogY + dialogHeight - 22, 60, 20, I18n.format("copylink"));
		buttonList.add(btnClipboard);
	}
	
	@Override
	protected void actionPerformed(GuiButton guibutton) {
		if (guibutton.id == btnCancel.id) {
			ImgurDelete deleter = new ImgurDelete(this.deleteHash);
			deleter.start(null);
		} else if (guibutton.id == btnView.id) {
			BrowserOpener.openURLstringInBrowser(url);
		} else if (guibutton.id == btnClipboard.id) {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
		}

		super.actionPerformed(guibutton);
	}
	
	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		drawCenteredString(fontRendererObj, I18n.format("uploadsuccess"), dialogX + (dialogWidth / 2), dialogY + 18, 0xFFFFAA00);
		drawCenteredString(fontRendererObj, this.url, dialogX + (dialogWidth / 2), dialogY + 32, 0xFFFFFF55);
	}

	@Override
	public boolean validateDialog() {
		return true;
	}

	@Override
	public void onSubmit() {
	}

}
