package com.thevoxelbox.voxelcam;


public class VoxelCamConfig extends ModConfig implements IVoxelPropertyProvider {

	public static final KeyBinding KEY_OPENSCREENSHOTMANAGER = new KeyBinding("ScreenShot Manager", Keyboard.KEY_H);
	public static final KeyBinding KEY_TAKEBIGPICTURE = new KeyBinding("Big Screenshot", Keyboard.KEY_F4);

	public static final String FIRSTRUN = "firstRun";
	public static final String PHOTOWIDTH = "photoWidth";
	public static final String PHOTOHEIGHT = "photoHeight";
	public static final String TWITTERUSERID = "twitterUserID";
	public static final String TWITTERAUTHTOKEN = "twitterAuthToken";
	public static final String TWITTERAUTHTOKENSECRET = "twitterAuthTokenSecret";

	public VoxelCamConfig() {
		super("VoxelCam", "voxelcam.properties");
	}

	@Override
	protected void setDefaults() {
		defaults.put(FIRSTRUN, "true");
		defaults.put(PHOTOWIDTH, "1920");
		defaults.put(PHOTOHEIGHT, "1080");
		defaults.put(TWITTERAUTHTOKEN, "needLogin");
		defaults.put(TWITTERAUTHTOKENSECRET, "needLogin");
		defaults.put(TWITTERUSERID, "null");
	}

}
