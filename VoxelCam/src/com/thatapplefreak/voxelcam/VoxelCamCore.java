package com.thatapplefreak.voxelcam;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.mumfrey.liteloader.Configurable;
import com.mumfrey.liteloader.InitCompleteListener;
import com.mumfrey.liteloader.RenderListener;
import com.mumfrey.liteloader.Tickable;
import com.mumfrey.liteloader.core.LiteLoader;
import com.mumfrey.liteloader.modconfig.ConfigPanel;
import com.mumfrey.liteloader.util.ModUtilities;
import com.thatapplefreak.voxelcam.gui.mainmenu.FirstRunPopup;
import com.thatapplefreak.voxelcam.gui.mainmenu.GuiMainMenuWithPhotoButton;
import com.thatapplefreak.voxelcam.gui.manager.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.gui.settings.GuiVoxelCamSettingsPanel;
import com.thatapplefreak.voxelcam.imagehandle.BigScreenshotTaker;
import com.thatapplefreak.voxelcam.imagehandle.ScreenshotIncapable;
import com.thatapplefreak.voxelcam.imagehandle.ScreenshotTaker;
import com.thevoxelbox.common.gui.SettingsPanelManager;
import com.thevoxelbox.common.status.StatusMessage;
import com.thevoxelbox.common.status.StatusMessageManager;
import com.thevoxelbox.common.util.AbstractionLayer;
import com.thevoxelbox.common.util.ChatMessageBuilder;

/**
 * Main hook class for VoxelCam
 * 
 * @author thatapplefreak
 * 
 */
public class VoxelCamCore implements Tickable, InitCompleteListener, RenderListener, Configurable {

	/**
	 * This is the configuration file for the mod
	 */
	private static VoxelCamConfig config = new VoxelCamConfig();

	/**
	 * This is the directory minecraft stores screenshots in
	 */
	private static File screenshotsDir;

	/**
	 * This is a list of the keys that VoxelCam listens to that are currently in
	 * the down state
	 */
	private static HashSet<Integer> heldKeys = new HashSet<Integer>();

	/**
	 * If the mod VoxelMenu is installed this will be true, adds soft dependancy
	 * on VoxelMenu
	 */
	public static boolean voxelMenuExists = false;
	
	public static boolean screenshotIsSaving = false;
	
	private static StatusMessage savingStatusMessage;

	/**
	 * Initialize the mod
	 */
	@Override
	public void init(File configPath) {
		screenshotsDir = new File(LiteLoader.getGameDirectory(), "/screenshots");
		if (!screenshotsDir.exists()) {
			screenshotsDir.mkdir(); // Make sure that the screenshots directory
									// is there, if not, create it
		}

		// Register the Keys that VoxelCam uses
		LiteLoader.getInput().registerKeyBinding(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER);

		// Add the configuation panel to VoxelCommons awareness
		SettingsPanelManager.addSettingsPanel("Camera", GuiVoxelCamSettingsPanel.class);
	}

	@Override
	public void upgradeSettings(String version, File configPath, File oldConfigPath) {
	}

	/**
	 * This method is called 20 times per second during the game
	 */
	@Override
	public void onTick(Minecraft minecraft, float partialTicks, boolean inGame, boolean clock) {
		// Tell the bigscreenshot taker that the next tick has happend
		BigScreenshotTaker.onTick();
		// Check to see if the user wants to open the screenshot manager
		if (isKeyDown(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER.getKeyCode()) && !isKeyDown(Keyboard.KEY_F3)) {
			if (!heldKeys.contains(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER.getKeyCode())) {
				if (minecraft.currentScreen instanceof GuiMainMenu || minecraft.currentScreen == null) {
					if (!screenshotIsSaving) {
						minecraft.displayGuiScreen(new GuiScreenShotManager());
					} else {
						ChatMessageBuilder cmb = new ChatMessageBuilder();
						cmb.append("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
						cmb.append(" " + I18n.format("savingpleasewait"));
						cmb.showChatMessageIngame();
					}
				} else if (minecraft.currentScreen instanceof GuiScreenShotManager) {
					// Dont turn the screenshot manager off if the user is
					// typing into the searchbar
					if (!((GuiScreenShotManager) minecraft.currentScreen).searchBar.isFocused()) {
						minecraft.setIngameFocus();
					}
				}
				heldKeys.add(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER.getKeyCode());
			}
		} else {
			heldKeys.remove(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER.getKeyCode());
		}
		
		
		//Status Message
		if (minecraft.inGameHasFocus && !minecraft.gameSettings.showDebugInfo) {
			savingStatusMessage.setText(I18n.format("savingscreenshot") + " (" + ScreenshotTaker.getSavePercent() + "%) " + (ScreenshotTaker.isWritingToFile() ? I18n.format("writing") + "..." : ""));
			savingStatusMessage.setVisible(screenshotIsSaving);
		}
	}

	/**
	 * Wrapper for the LWJGL functions to deal with mouse button bindings or
	 * invalid values
	 */
	public static boolean isKeyDown(int keyCode) {
		try {
			if (keyCode < 0) { // If the code is less than 0 it is probably the
								// mouse
				return Mouse.isButtonDown(keyCode + 100);
			}
			return Keyboard.isKeyDown(keyCode);
		} catch (Exception ex) {
			return false;
		}
	}

	/**
	 * Get the configuration
	 */
	public static VoxelCamConfig getConfig() {
		return config;
	}

	/**
	 * Get the minecraft screenshot directiory
	 */
	public static File getScreenshotsDir() {
		return screenshotsDir;
	}

	@Override
	public void onRender() {
	}

	/**
	 * Called immediately before the current GUI is rendered
	 * 
	 * @param currentScreen
	 *            Current screen (if any)
	 */
	@Override
	public void onRenderGui(GuiScreen currentScreen) {
		// If VoxelMenu does not exist modify the Main Menu with the PhotoButton
		if (!voxelMenuExists && currentScreen != null) {
			if (currentScreen instanceof GuiMainMenu && !(currentScreen instanceof GuiMainMenuWithPhotoButton)) {
				Minecraft.getMinecraft().displayGuiScreen(new GuiMainMenuWithPhotoButton());
			}
		}

		// If this is the users first time running the mod show a welcome screen
		if (currentScreen != null && config.getBoolProperty(VoxelCamConfig.FIRSTRUN) && !(currentScreen instanceof FirstRunPopup)) {
			Minecraft.getMinecraft().displayGuiScreen(new FirstRunPopup(currentScreen));
		}
	}

	@Override
	public void onRenderWorld() {
	}

	@Override
	public void onSetupCameraTransform() {
	}

	/**
	 * Tell Liteloader the class of the settings panel
	 */
	@Override
	public Class<? extends ConfigPanel> getConfigPanelClass() {
		return GuiVoxelCamSettingsPanel.class;
	}
	
	public static void screenshotListener(Minecraft minecraft) {
		int key = minecraft.gameSettings.keyBindScreenshot.getKeyCode();
		if (isKeyDown(key)) {
			if (!(minecraft.currentScreen instanceof ScreenshotIncapable)) {
				if (!heldKeys.contains(key)) {
					if (isKeyDown(Keyboard.KEY_LSHIFT) || isKeyDown(Keyboard.KEY_RSHIFT)) {
						heldKeys.add(key);
						BigScreenshotTaker.run();
						return;
					}
					heldKeys.add(key);
					ScreenshotTaker.capture(minecraft.displayWidth, minecraft.displayHeight);
				}
			}
		} else {
			heldKeys.remove(key);
		}
	}

	@Override
	public void onInitCompleted(Minecraft minecraft, LiteLoader loader) {
		
		// Look for VoxelMenu
		try {
			Class<? extends GuiMainMenu> customMainMenuClass = (Class<? extends GuiMainMenu>) Class.forName("com.thevoxelbox.voxelmenu.GuiMainMenuVoxelBox");
			Method mRegisterCustomScreen = customMainMenuClass.getDeclaredMethod("registerCustomScreen", String.class, Class.class, String.class);
			mRegisterCustomScreen.invoke(null, "right", GuiScreenShotManager.class, I18n.format("screenshots"));
			Class<? extends GuiMainMenu> ingameGuiClass = (Class<? extends GuiMainMenu>) Class.forName("com.thevoxelbox.voxelmenu.ingame.GuiIngameMenu");
			mRegisterCustomScreen = ingameGuiClass.getDeclaredMethod("registerCustomScreen", String.class, Class.class, String.class);
			mRegisterCustomScreen.invoke(null, "", GuiScreenShotManager.class, I18n.format("screenshots"));
			voxelMenuExists = true;
		} catch (ClassNotFoundException ex) { // This means VoxelMenu does not
												// exist
			voxelMenuExists = false;
		} catch (Exception e) {
			e.printStackTrace();
		}
				
		savingStatusMessage = StatusMessageManager.getInstance().getStatusMessage("savingStatus", 1);
		savingStatusMessage.setTitle("VoxelCam");
	}

	//Leave empty
	@Override
	public String getName() {return null;}
	@Override
	public String getVersion() {return null;}

}
