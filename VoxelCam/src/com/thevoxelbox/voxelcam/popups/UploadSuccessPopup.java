package com.thevoxelbox.voxelcam.popups;

import com.thevoxelbox.voxelcam.upload.imgur.ImgurDelete;

public class UploadSuccessPopup extends GuiDialogBox {
	private String deleteHash;
	private String url;

	private GuiButton btnView, btnClipboard;

	public UploadSuccessPopup(GuiScreen parentScreen, String windowTitle, String deleteHash, String url) {
		super(parentScreen, 300, 80, windowTitle);
		this.deleteHash = deleteHash;
		this.url = url;
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
	protected void onInitDialog() {
		btnCancel.displayString = "Undo";
		if (deleteHash == null) {
			btnCancel.enabled = false;
		}
		btnView = new GuiButton(100, dialogX + dialogWidth - 248, dialogY + dialogHeight - 22, 60, 20, "Open");
		buttonList.add(btnView);
		btnClipboard = new GuiButton(200, dialogX + dialogWidth - 186, dialogY + dialogHeight - 22, 60, 20, "Copy Link");
		buttonList.add(btnClipboard);
	}

	@Override
	public void onSubmit() {
	}

	@Override
	public boolean validateDialog() {
		return true;
	}

	@Override
	protected void drawDialog(int mouseX, int mouseY, float f) {
		drawCenteredString(fontRenderer, "Upload completed successfully", dialogX + (dialogWidth / 2), dialogY + 18, 0xFFFFAA00);
		drawCenteredString(fontRenderer, this.url, dialogX + (dialogWidth / 2), dialogY + 32, 0xFFFFFF55);
	}
}
