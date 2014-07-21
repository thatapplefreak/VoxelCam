package com.thatapplefreak.voxelcam;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.thevoxelbox.common.util.ModConfig;


public class VoxelCamConfig extends ModConfig {

	// Keybinds that VoxelCam uses
	public static final KeyBinding KEY_OPENSCREENSHOTMANAGER = new KeyBinding("ScreenShot Manager", Keyboard.KEY_H, "VoxelCam");

	// Strings to access the settings
	public static final String FIRSTRUN = "firstRun";
	
	public static final String PHOTOWIDTH = "photoWidth";
	public static final String PHOTOHEIGHT = "photoHeight";
	
	public static final String TWITTERUSERID = "twitterUserID";
	public static final String TWITTERAUTHTOKEN = "twitterAuthToken";
	public static final String TWITTERAUTHTOKENSECRET = "twitterAuthTokenSecret";
	
	public static final String FACEBOOKAUTHTOKEN = "facebookauthtoken";
	public static final String FACEBOOKUSERID = "facebookUserID";
	
	public static final String REDDITUSERNAME = "redditUsername";
	public static final String REDDITPASSWORD = "redditPassword";
	
	public static final String AUTO_UPLOAD = "autoUplaod";
	public static final String AUTO_UPLOAD_IMGUR = "autoUploadToImgur";
	public static final String AUTO_UPLOAD_DROPBOX = "autoUploadToDropbox";
	public static final String AUTO_UPLOAD_GOOGLEDRIVE = "autoUploadToGoogleDrive";

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
		defaults.put(TWITTERUSERID, "needLogin");
		defaults.put(TWITTERAUTHTOKEN, "needLogin");
		defaults.put(TWITTERAUTHTOKENSECRET, "needLogin");
		defaults.put(FACEBOOKAUTHTOKEN, "needLogin");
		defaults.put(FACEBOOKUSERID, "needLogin");
		defaults.put(REDDITUSERNAME, "");
		defaults.put(REDDITPASSWORD, "");
		defaults.put(AUTO_UPLOAD, "false");
		defaults.put(AUTO_UPLOAD_IMGUR, "false");
		defaults.put(AUTO_UPLOAD_DROPBOX, "false");
		defaults.put(AUTO_UPLOAD_GOOGLEDRIVE, "false");
	}

}
