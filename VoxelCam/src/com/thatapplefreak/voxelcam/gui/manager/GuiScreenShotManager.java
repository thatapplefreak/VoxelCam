package com.thatapplefreak.voxelcam.gui.manager;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import javax.imageio.ImageIO;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.thatapplefreak.voxelcam.VoxelCamCore;
import com.thatapplefreak.voxelcam.gui.editor.GuiEditScreenshot;
import com.thatapplefreak.voxelcam.imagehandle.GLImageMemoryHandler;
import com.thatapplefreak.voxelcam.imagehandle.ImageDrawer;
import com.thatapplefreak.voxelcam.imagehandle.ScreenshotIncapable;
import com.thatapplefreak.voxelcam.imagehandle.metadata.MetaDataHandler;
import com.thatapplefreak.voxelcam.io.VoxelCamIO;

/**
 * This Gui shows the player the screenshots he/she has taken and can
 * rename/delete/post to imgur,facebook,twitter
 * 
 * @author thatapplefreak
 * 
 */
public class GuiScreenShotManager extends GuiScreen implements ScreenshotIncapable {

	/**
	 * Frame for the currently displaying picture
	 */
	private ScalePhotoFrame frame;

	/**
	 * Selector for the user to pic the photo they want to view
	 */
	private PhotoSelector selector;

	/**
	 * Button to go to previous screen
	 */
	private GuiButton btnBack, btnRename, btnDelete, btnPost, btnOpenFolder, btnEditPicture;

	public SearchBar searchBar;

	private static final float frameScale = 22F / 30F;

	public GuiScreenShotManager() {
		frame = new ScalePhotoFrame(this, (int) (width - (width * (frameScale))), 10, frameScale, VoxelCamIO.getSelectedPhoto());
	}

	@SuppressWarnings("unchecked")
	@Override
	public void initGui() {

		searchBar = new SearchBar(fontRendererObj, 11, 14, 50, 13);

		selector = new PhotoSelector(this, 125);
		selector.registerScrollButtons(buttonList, 7, 8);
		

		btnBack = new GuiButton(0, 10, height - 30, 70, 20, I18n.format("back"));
		buttonList.add(btnBack);

		btnRename = new GuiButton(1, width - (70 * 3) - 5, height - 45, 70, 20, I18n.format("rename"));
		buttonList.add(btnRename);

		btnDelete = new GuiButton(2, width - (70 * 2) - 5, height - 45, 70, 20, I18n.format("delete"));
		buttonList.add(btnDelete);

		btnEditPicture = new GuiButton(3, width - (70 * 1) - 5, height - 45, 70, 20, I18n.format("edit"));
		buttonList.add(btnEditPicture);
		btnEditPicture.enabled = false;

		btnOpenFolder = new GuiButton(4, width - (70 * 3) - 5, height - 25, 140, 20, I18n.format("openscreenshotsfolder"));
		buttonList.add(btnOpenFolder);

		btnPost = new GuiButton(5, width - (70 * 1) - 5, height - 25, 70, 20, I18n.format("postto") + "...");
		buttonList.add(btnPost);

	}

	@Override
	public void drawScreen(int par1, int par2, float par3) {
		if (!mc.isSingleplayer()) {
			drawDefaultBackground();
		} else {
			drawBackground(0);
		}

		super.drawScreen(par1, par2, par3);
		frame.draw(par1, par2, par3);
		selector.drawScreen(par1, par2, par3);
		searchBar.drawTextBox();
	}

	private int updateTickCounter = 0;

	@Override
	public void updateScreen() {
		super.updateScreen();
		VoxelCamIO.updateScreenShotFilesList();
		if (frame.getPhoto() != VoxelCamIO.getSelectedPhoto()) {
			if (VoxelCamIO.getSelectedPhoto() == null) {
				frame.setPhoto(null);
			} else if (frame.getPhoto() == null && VoxelCamIO.getSelectedPhoto() != null) {
				frame.setPhoto(VoxelCamIO.getSelectedPhoto());
			} else if (!frame.getPhoto().equals(VoxelCamIO.getSelectedPhoto())) {
				frame.setPhoto(VoxelCamIO.getSelectedPhoto());
			}
		}
		frame.update((int) (btnPost.xPosition + 70 - (width * frameScale)), 13);
		selector.setDimensionsAndPosition(10, 28, frame.x, frame.y + frame.height);
		searchBar.setWidth(selector.right - selector.left - 2);
	}

	private ArrayList<File> getSortedScreenshots() {
		File[] filesInScreenshotDir = VoxelCamCore.getScreenshotsDir().listFiles();
		Arrays.sort(filesInScreenshotDir, new Comparator<File>() {
			@Override
			public int compare(File f, File g) {
				if (f.lastModified() > g.lastModified()) {
					return -1;
				} else if (f.lastModified() < g.lastModified()) {
					return 1;
				} else {
					return 0;
				}
			}
		});
		return new ArrayList<File>(Arrays.asList(filesInScreenshotDir));
	}

	public FontRenderer getFontRenderer(GuiScreenShotManager man) {
		return man.fontRendererObj;
	}

	@Override
	protected void actionPerformed(GuiButton btn) {
		if (btn == btnBack) {
			keyTyped('`', 1);
		} else if (btn == btnRename) {
			mc.displayGuiScreen(new RenamePopup(this, VoxelCamIO.getSelectedPhoto().getName()));
		} else if (btn == btnDelete) {
			mc.displayGuiScreen(new DeletePopup(this));
		} else if (btn == btnEditPicture) {
			mc.displayGuiScreen(new GuiEditScreenshot(this, VoxelCamIO.getSelectedPhoto()));
		} else if (btn == btnOpenFolder) {
			try {
				Desktop.getDesktop().browse(VoxelCamCore.getScreenshotsDir().toURI());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (btn == btnPost) {
			mc.displayGuiScreen(new PostPopup(this));
		}
	}

	public void enableButtons(boolean b) {
		if (b) {
			btnRename.enabled = true;
			btnDelete.enabled = true;
			btnEditPicture.enabled = true;
			btnPost.enabled = true;
		} else {
			btnRename.enabled = false;
			btnDelete.enabled = false;
			btnEditPicture.enabled = false;
			btnPost.enabled = false;
		}
		btnBack.enabled = true;
	}

	@Override
	protected void mouseClicked(int par1, int par2, int par3) {
		super.mouseClicked(par1, par2, par3);
		searchBar.mouseClicked(par1, par2, par3);
	}

	@Override
	protected void keyTyped(char par1, int par2) {
		if (par2 == 1) {
			GLImageMemoryHandler.requestImageFlush();
			this.mc.displayGuiScreen((GuiScreen) null);
			this.mc.setIngameFocus();
		}
		searchBar.textboxKeyTyped(par1, par2);
		if (!searchBar.isFocused()) {
			if (par1 == 'r') {
				actionPerformed(btnRename);
			} else if (par1 == 'd') {
				actionPerformed(btnDelete);
			} else if (par1 == 'p') {
				actionPerformed(btnPost);
			} else if (par2 == Keyboard.KEY_UP || par2 == Keyboard.KEY_W) {
				if (VoxelCamIO.selected > 0) {
					VoxelCamIO.selected--;
				}
			} else if (par2 == Keyboard.KEY_DOWN || par2 == Keyboard.KEY_S) {
				if (VoxelCamIO.selected < VoxelCamIO.getScreenShotFiles().size() - 1) {
					VoxelCamIO.selected++;
				}
			}
		}
	}

}
