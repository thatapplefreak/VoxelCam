package com.thevoxelbox.voxelcam;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;

import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.Minecraft;

import org.lwjgl.input.Keyboard;

import com.mumfrey.liteloader.Configurable;
import com.mumfrey.liteloader.RenderListener;
import com.mumfrey.liteloader.Tickable;
import com.mumfrey.liteloader.core.LiteLoader;
import com.mumfrey.liteloader.modconfig.ConfigPanel;
import com.mumfrey.liteloader.util.ModUtilities;
import com.thevoxelbox.common.gui.SettingsPanelManager;
import com.thevoxelbox.voxelcam.bigpicture.BigScreenshotTaker;
import com.thevoxelbox.voxelcam.gui.GuiMainMenuWithPhotoButton;
import com.thevoxelbox.voxelcam.gui.GuiScreenShotManager;
import com.thevoxelbox.voxelcam.gui.GuiVoxelCamSettingsPanel;
import com.thevoxelbox.voxelcam.popups.FirstRunPopup;

/**
 * Main hook class for VoxelCam
 * 
 * @author thatapplefreak
 * 
 */
public class LiteModVoxelCam implements Tickable, RenderListener, Configurable {

	private static VoxelCamConfig config = new VoxelCamConfig();

	private static File screenshotsDir;

	private static HashSet<Integer> heldKeys = new HashSet<Integer>();

	public static boolean voxelMenuExists = false;

	public static BigScreenshotTaker bigScreenshotTaker = new BigScreenshotTaker();

	@Override
	public String getName() {
		return "VoxelCam";
	}

	@Override
	public String getVersion() {
		return "1.1.1";
	}

	@Override
	public void init(File configPath) {
		screenshotsDir = new File(LiteLoader.getGameDirectory(), "/screenshots");
		if (!screenshotsDir.exists()) {
			screenshotsDir.mkdir();
		}

		ModUtilities.registerKey(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER);
		ModUtilities.registerKey(VoxelCamConfig.KEY_TAKEBIGPICTURE);

		SettingsPanelManager.addSettingsPanel("Camera", GuiVoxelCamSettingsPanel.class);

		try {
			Class<? extends GuiMainMenu> customMainMenuClass = (Class<? extends GuiMainMenu>) Class.forName("com.thevoxelbox.voxelmenu.GuiMainMenuVoxelBox");
			Method mRegisterCustomScreen = customMainMenuClass.getDeclaredMethod("registerCustomScreen", String.class, Class.class, String.class);
			mRegisterCustomScreen.invoke(null, "right", GuiScreenShotManager.class, "Screenshots");
			Class<? extends GuiMainMenu> ingameGuiClass = (Class<? extends GuiMainMenu>) Class.forName("com.thevoxelbox.voxelmenu.ingame.GuiIngameMenu");
			mRegisterCustomScreen = ingameGuiClass.getDeclaredMethod("registerCustomScreen", String.class, Class.class, String.class);
			mRegisterCustomScreen.invoke(null, "", GuiScreenShotManager.class, "Screenshots");
			voxelMenuExists = true;
		} catch (ClassNotFoundException ex) {
			voxelMenuExists = false;
		} catch (Exception e) {
		}

	}

	@Override
	public void upgradeSettings(String version, File configPath, File oldConfigPath) {
	}

	@Override
	public void onTick(Minecraft minecraft, float partialTicks, boolean inGame, boolean clock) {
		bigScreenshotTaker.onTick();
		if (Keyboard.isKeyDown(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER.keyCode)) {
			if (!heldKeys.contains(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER.keyCode)) {
				if (minecraft.currentScreen instanceof GuiMainMenu || minecraft.currentScreen == null) {
					minecraft.displayGuiScreen(new GuiScreenShotManager());
				} else if (minecraft.currentScreen instanceof GuiScreenShotManager) {
					if (!((GuiScreenShotManager) minecraft.currentScreen).searchBar.isFocused()) {
						minecraft.setIngameFocus();
					}
				}
				heldKeys.add(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER.keyCode);
			}
		} else {
			heldKeys.remove(VoxelCamConfig.KEY_OPENSCREENSHOTMANAGER.keyCode);
		}

		if (Keyboard.isKeyDown(VoxelCamConfig.KEY_TAKEBIGPICTURE.keyCode)) {
			if (!heldKeys.contains(VoxelCamConfig.KEY_TAKEBIGPICTURE.keyCode)) {
				if (minecraft.currentScreen == null && !minecraft.ingameGUI.getChatGUI().getChatOpen()) {
					bigScreenshotTaker.run();
					heldKeys.add(VoxelCamConfig.KEY_TAKEBIGPICTURE.keyCode);
				}
			}
		} else {
			heldKeys.remove(VoxelCamConfig.KEY_TAKEBIGPICTURE.keyCode);
		}
	}

	public static VoxelCamConfig getConfig() {
		return config;
	}

	public static File getScreenshotsDir() {
		return screenshotsDir;
	}

	@Override
	public void onRender() {
	}

	@Override
	public void onRenderGui(GuiScreen currentScreen) {
		if (!voxelMenuExists && currentScreen != null) {
			if (currentScreen instanceof GuiMainMenu && !(currentScreen instanceof GuiMainMenuWithPhotoButton)) {
				Minecraft.getMinecraft().displayGuiScreen(new GuiMainMenuWithPhotoButton());
			}
		}

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

	@Override
	public Class<? extends ConfigPanel> getConfigPanelClass() {
		return GuiVoxelCamSettingsPanel.class;
	}

}
