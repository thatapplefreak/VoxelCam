package com.thatapplefreak.voxelcam;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.mumfrey.liteloader.Configurable;
import com.mumfrey.liteloader.RenderListener;
import com.mumfrey.liteloader.Tickable;
import com.mumfrey.liteloader.core.LiteLoader;
import com.mumfrey.liteloader.modconfig.ConfigPanel;
import com.mumfrey.liteloader.util.ModUtilities;
import com.thatapplefreak.voxelcam.gui.GuiMainMenuWithPhotoButton;
import com.thatapplefreak.voxelcam.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.gui.GuiVoxelCamSettingsPanel;
import com.thatapplefreak.voxelcam.imagehandle.BigScreenshotTaker;
import com.thatapplefreak.voxelcam.imagehandle.ScreenshotTaker;
import com.thatapplefreak.voxelcam.popups.FirstRunPopup;
import com.thevoxelbox.common.gui.SettingsPanelManager;

/**
 * Main hook class for VoxelCam
 * 
 * @author thatapplefreak
 * 
 */
public class LiteModVoxelCam implements Tickable, RenderListener, Configurable {

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

	/**
	 * Get the name of the mod (Called by Liteloader)
	 */
	@Override
	public String getName() {
		return "VoxelCam";
	}

	/**
	 * Get the version of the mod (Called by Liteloader)
	 */
	@Override
	public String getVersion() {
		return "1.2.1 Snapshot A";
	}

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
		ModUtilities.registerKey(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER);

		// Add the configuation panel to VoxelCommons awareness
		SettingsPanelManager.addSettingsPanel("Camera", GuiVoxelCamSettingsPanel.class);

		// Look for VoxelMenu
		try {
			Class<? extends GuiMainMenu> customMainMenuClass = (Class<? extends GuiMainMenu>) Class.forName("com.thevoxelbox.voxelmenu.GuiMainMenuVoxelBox");
			Method mRegisterCustomScreen = customMainMenuClass.getDeclaredMethod("registerCustomScreen", String.class, Class.class, String.class);
			mRegisterCustomScreen.invoke(null, "right", GuiScreenShotManager.class, "Screenshots");
			Class<? extends GuiMainMenu> ingameGuiClass = (Class<? extends GuiMainMenu>) Class.forName("com.thevoxelbox.voxelmenu.ingame.GuiIngameMenu");
			mRegisterCustomScreen = ingameGuiClass.getDeclaredMethod("registerCustomScreen", String.class, Class.class, String.class);
			mRegisterCustomScreen.invoke(null, "", GuiScreenShotManager.class, "Screenshots");
			voxelMenuExists = true;
		} catch (ClassNotFoundException ex) { // This means VoxelMenu does not
												// exist
			voxelMenuExists = false;
		} catch (Exception e) {
			e.printStackTrace();
		}

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
		if (isKeyDown(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER.getKeyCode())) {
			if (!heldKeys.contains(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER.getKeyCode())) {
				if (minecraft.currentScreen instanceof GuiMainMenu || minecraft.currentScreen == null) {
					if (!screenshotIsSaving) {
						minecraft.displayGuiScreen(new GuiScreenShotManager());
					} else {
//						minecraft.ingameGUI.getChatGUI().printChatMessage("§4[VoxelCam]§F Saving Screenshot right now, please wait");
						minecraft.ingameGUI.getChatGUI().func_146239_a("§4[VoxelCam]§F Saving Screenshot right now, please wait");
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
	
	public static void screenshotListener() {
		if (isKeyDown(Keyboard.KEY_F2)) {
			if (!(Minecraft.getMinecraft().currentScreen instanceof GuiScreenShotManager)) {
				if (!heldKeys.contains(Keyboard.KEY_F2)) {
					if (isKeyDown(Keyboard.KEY_LSHIFT) || isKeyDown(Keyboard.KEY_RSHIFT)) {
						Minecraft mc = Minecraft.getMinecraft();
						heldKeys.add(Keyboard.KEY_F2);
						BigScreenshotTaker.run();
						return;
					}
					Minecraft mc = Minecraft.getMinecraft();
					heldKeys.add(Keyboard.KEY_F2);
					ScreenshotTaker.capture(mc.displayWidth, mc.displayHeight, config.getStringProperty(VoxelCamConfig.NORMALSCREENSHOTNAMINGMETHOD));
				}
			}
		} else {
			heldKeys.remove(Keyboard.KEY_F2);
		}
	}

}
