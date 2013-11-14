package com.thevoxelbox.voxelcam.upload.facebook;

import com.thevoxelbox.voxelcam.LiteModVoxelCam;
import com.thevoxelbox.voxelcam.VoxelCamConfig;
import com.thevoxelbox.voxelcam.popups.TwitterPINPopup;
import com.thevoxelbox.voxelcam.upload.twitter.TwitterHandler;

import facebook4j.Facebook;
import facebook4j.FacebookException;
import facebook4j.FacebookFactory;
import facebook4j.auth.AccessToken;

public abstract class FacebookHandler {

	public static final String APP_ID = "321963097941455";
	public static final String App_Secret = "7fe4138a051faddffc05f055ef59a015";

	public static final Facebook facebook = FacebookFactory.getSingleton();
	static {
		facebook.setOAuthAppId(APP_ID, App_Secret);
	}

}
