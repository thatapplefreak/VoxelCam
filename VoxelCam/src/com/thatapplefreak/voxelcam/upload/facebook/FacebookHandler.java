package com.thatapplefreak.voxelcam.upload.facebook;

import com.thatapplefreak.voxelcam.VoxelCamCore;
import com.thatapplefreak.voxelcam.VoxelCamConfig;
import com.thatapplefreak.voxelcam.upload.twitter.TwitterHandler;
import com.thatapplefreak.voxelcam.upload.twitter.TwitterPINPopup;

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
	
	class FacebookAuthGrabber implements Runnable {

		public FacebookAuthGrabber() {
			
		}
		
		@Override
		public void run() {
			
		}
		
		
		private void storeAuth() {
			
		}
		
	}

}