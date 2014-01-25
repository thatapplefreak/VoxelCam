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

import org.lwjgl.input.Keyboard;

import com.thatapplefreak.voxelcam.LiteModVoxelCam;
import com.thatapplefreak.voxelcam.gui.editor.GuiEditScreenshot;
import com.thatapplefreak.voxelcam.imagehandle.GLImageMemoryHandler;
import com.thatapplefreak.voxelcam.imagehandle.ImageDrawer;

/**
 * This Gui shows the player the screenshots he/she has taken and can
 * rename/delete/post to imgur,facebook,twitter
 * 
 * @author thatapplefreak
 * 
 */
public class GuiScreenShotManager extends GuiScreen {

	/**
	 * List of all PNG files in the screenshot directory
	 */
	private static ArrayList<File> screenShotFiles;

	/**
	 * Frame for the currently displaying picture
	 */
	private ScalePhotoFrame frame;

	/**
	 * Selector for the user to pic the photo they want to view
	 */
	private PhotoSelector selector;

	/**
	 * Index of the currently selected photo
	 */
	public static int selected = 0;

	/**
	 * Button to go to previous screen
	 */
	private GuiButton btnBack, btnRename, btnDelete, btnPost, btnOpenFolder, btnEditPicture;

	public SearchBar searchBar;

	private static final float frameScale = 22F / 30F;

	/**
	 * This makes it so it auto selects the first pic only the first time it is
	 * initialized
	 */
	private boolean firstInit = true;

	public GuiScreenShotManager() {
		setScreenShotFiles(new ArrayList<File>(Arrays.asList(LiteModVoxelCam.getScreenshotsDir().listFiles())));
		for (int i = getScreenShotFiles().size() - 1; i >= 0; i--) {
			if (!getScreenShotFiles().get(i).getName().endsWith(".png")) {
				getScreenShotFiles().remove(i); // Remove all files that are not
												// png images
			}
		}
		if (getScreenShotFiles().size() == 0) {
			getScreenShotFiles().add(null);
		}

		frame = new ScalePhotoFrame(this, (int) (width - (width * (frameScale))), 10, frameScale, getScreenShotFiles().get(selected));
	}

	@SuppressWarnings("unchecked")
	@Override
	public void initGui() {
		if (firstInit) {
			selected = 0;
			firstInit = false;

			searchBar = new SearchBar(fontRendererObj, 11, 14, 50, 13);

			selector = new PhotoSelector(this, 125);
			selector.registerScrollButtons(buttonList, 7, 8);
		}

		btnBack = new GuiButton(0, 10, height - 30, 70, 20, "Back");
		buttonList.add(btnBack);

		btnRename = new GuiButton(1, width - (70 * 3) - 5, height - 45, 70, 20, "Rename");
		buttonList.add(btnRename);

		btnDelete = new GuiButton(2, width - (70 * 2) - 5, height - 45, 70, 20, "Delete");
		buttonList.add(btnDelete);

		btnEditPicture = new GuiButton(3, width - (70 * 1) - 5, height - 45, 70, 20, "Edit");
		buttonList.add(btnEditPicture);

		btnOpenFolder = new GuiButton(4, width - (70 * 3) - 5, height - 25, 140, 20, "Open Screenshots Folder");
		buttonList.add(btnOpenFolder);

		btnPost = new GuiButton(5, width - (70 * 1) - 5, height - 25, 70, 20, "Post to...");
		buttonList.add(btnPost);

	}

	@Override
	public void drawScreen(int par1, int par2, float par3) {
		if (!mc.isSingleplayer()) {
			drawDefaultBackground();
		} else {
			func_146270_b(0);
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
		// panoramaRenderer.updatePanorama();
		setScreenShotFiles(getSortedScreenshots());
		if (updateTickCounter % 30 == 0) { // Update every 1.5 seconds
			for (int i = getScreenShotFiles().size() - 1; i >= 0; i--) {
				if (!getScreenShotFiles().get(i).getName().endsWith(".png")) {
					getScreenShotFiles().remove(i); // Remove all files that are
													// not
													// png images
				}
			}
			if (getScreenShotFiles().isEmpty()) {
				getScreenShotFiles().add(null);
			}
		}
		for (int i = getScreenShotFiles().size() - 1; i >= 0; i--) {
			if (getScreenShotFiles().get(i) != null && !getScreenShotFiles().get(i).getName().toLowerCase().contains(searchBar.getText().toLowerCase())) {
				getScreenShotFiles().remove(i);
			}
		}
		if (selected > getScreenShotFiles().size()) {
			selected = getScreenShotFiles().size() - 1;
		}
		if (frame.getPhoto() != getScreenShotFiles().get(selected)) {
			if (getScreenShotFiles().get(selected) == null) {
				frame.setPhoto(null);
			} else if (frame.getPhoto() == null && getScreenShotFiles().get(selected) != null) {
				frame.setPhoto(getScreenShotFiles().get(selected));
			} else if (!frame.getPhoto().equals(getScreenShotFiles().get(selected))) {
				frame.setPhoto(getScreenShotFiles().get(selected));
			}
		}
		frame.update((int) (btnPost.field_146128_h + 70 - (width * frameScale)), 13);
		selector.setDimensionsAndPosition(10, 28, frame.x, frame.y + frame.height);
		searchBar.setWidth(selector.right - selector.left - 2);
	}

	private ArrayList<File> getSortedScreenshots() {
		File[] filesInScreenshotDir = LiteModVoxelCam.getScreenshotsDir().listFiles();
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

	public void selectPhotoIndex(int i) {
		selected = i;
	}

	public boolean getSelected(int i) {
		return i == selected;
	}

	public FontRenderer getFontRenderer(GuiScreenShotManager man) {
		return man.fontRendererObj;
	}

	@Override
	protected void actionPerformed(GuiButton btn) {
		if (btn == btnBack) {
			keyTyped('`', 1);
		} else if (btn == btnRename) {
			mc.displayGuiScreen(new RenamePopup(this, getScreenShotFiles().get(selected).getName()));
		} else if (btn == btnDelete) {
			mc.displayGuiScreen(new DeletePopup(this));
		} else if (btn == btnEditPicture) {
			mc.displayGuiScreen(new GuiEditScreenshot(this, getSelectedPhoto()));
		} else if (btn == btnOpenFolder) {
			try {
				Desktop.getDesktop().browse(LiteModVoxelCam.getScreenshotsDir().toURI());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (btn == btnPost) {
			mc.displayGuiScreen(new PostPopup(this));
		}
	}

	public void rename(String string) {
		getScreenShotFiles().get(selected).renameTo(new File(LiteModVoxelCam.getScreenshotsDir(), string + ".png"));
	}

	public void delete() {
		GLImageMemoryHandler.requestImageRemovalFromMem(getScreenShotFiles().get(selected));
		getScreenShotFiles().get(selected).delete(); // EXTERMINATE!
		getScreenShotFiles().remove(selected); // remove refrence
		if (selected > 0) {
			selected--;
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

	public static ArrayList<File> getScreenShotFiles() {
		return screenShotFiles;
	}

	private void setScreenShotFiles(ArrayList<File> screenShotFiles) {
		GuiScreenShotManager.screenShotFiles = screenShotFiles;
	}

	public static File getSelectedPhoto() {
		return getScreenShotFiles().get(selected);
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
				if (selected > 0) {
					selected--;
				}
			} else if (par2 == Keyboard.KEY_DOWN || par2 == Keyboard.KEY_S) {
				if (selected < getScreenShotFiles().size() - 1) {
					selected++;
				}
			}
		}
	}

}
