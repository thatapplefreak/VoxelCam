package com.thatapplefreak.voxelcam;

import net.minecraft.src.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.thevoxelbox.common.interfaces.IVoxelPropertyProvider;
import com.thevoxelbox.common.util.ModConfig;


public class VoxelCamConfig extends ModConfig implements IVoxelPropertyProvider {

	// Keybinds that VoxelCam uses
	public static final KeyBinding KEY_OPENSCREENSHOTMANAGER = new KeyBinding("ScreenShot Manager", Keyboard.KEY_H);

	// Strings to access the settings
	public static final String FIRSTRUN = "firstRun";
	public static final String PHOTOWIDTH = "photoWidth";
	public static final String PHOTOHEIGHT = "photoHeight";
	public static final String TWITTERUSERID = "twitterUserID";
	public static final String TWITTERAUTHTOKEN = "twitterAuthToken";
	public static final String TWITTERAUTHTOKENSECRET = "twitterAuthTokenSecret";
	public static final String NORMALSCREENSHOTNAMINGMETHOD = "normalScreenshotNamingMethod";
	public static final String BIGSCREENSHOTNAMINGMETHOD = "bigScreenshotNamingMehtod";

	public VoxelCamConfig() {
		super("VoxelCam", "voxelcam.properties");
	}

	/**
	 * Sets the default values for the settings
	 */
	@Override
	protected void setDefaults() {
		defaults.put(FIRSTRUN, "true");
		defaults.put(PHOTOWIDTH, "1920");
		defaults.put(PHOTOHEIGHT, "1080");
		defaults.put(TWITTERAUTHTOKEN, "needLogin");
		defaults.put(TWITTERAUTHTOKENSECRET, "needLogin");
		defaults.put(TWITTERUSERID, "null");
		defaults.put(NORMALSCREENSHOTNAMINGMETHOD, "DATE()");
		defaults.put(BIGSCREENSHOTNAMINGMETHOD, "custom_DATE()");
	}

}
