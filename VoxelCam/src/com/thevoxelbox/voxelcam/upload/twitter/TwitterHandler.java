package com.thevoxelbox.voxelcam.upload.twitter;

import java.io.File;

import com.thevoxelbox.voxelcam.LiteModVoxelCam;
import com.thevoxelbox.voxelcam.VoxelCamConfig;
import com.thevoxelbox.voxelcam.gui.GuiScreenShotManager;
import com.thevoxelbox.voxelcam.popups.TwitterPINPopup;
import com.thevoxelbox.voxelcam.popups.TwitterPostPopup;
import com.thevoxelbox.voxelcam.popups.UploadFailedPopup;
import com.thevoxelbox.voxelcam.popups.UploadSuccessPopup;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurCallback;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurResponse;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurUpload;
import com.thevoxelbox.voxelcam.upload.imgur.ImgurUploadResponse;

import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;

public abstract class TwitterHandler {

	public static void doTwitter(final TwitterPostPopup callbackGui, final File screenshot, final String text) {
		Long twitterUserID = Long.parseLong(LiteModVoxelCam.getConfig().getStringProperty(VoxelCamConfig.TWITTERUSERID));
		String userAuthToken = LiteModVoxelCam.getConfig().getStringProperty(VoxelCamConfig.TWITTERAUTHTOKEN);
		String userAuthTokenSecret = LiteModVoxelCam.getConfig().getStringProperty(VoxelCamConfig.TWITTERAUTHTOKENSECRET);
		TwitterKeys.twitter.setOAuthAccessToken(new AccessToken(userAuthToken, userAuthTokenSecret, twitterUserID));
		new Thread("Twitter_Post_Thread") {
			public void run() {
				try {
					StatusUpdate s = new StatusUpdate(text + " #VoxelCam");
					s.setMedia(screenshot);
					Status status = TwitterKeys.twitter.updateStatus(s);
					callbackGui.onUploadComplete(new UploadSuccessPopup(callbackGui.getParentScreen(), "Upload to Twitter succeeded", null, "http://www.twitter.com/" + status.getUser().getScreenName()));
				} catch (TwitterException e) {
					callbackGui.onUploadComplete(new UploadFailedPopup(callbackGui.getParentScreen(), "Upload to Twitter failed", "Error Code: " + Integer.toString(e.getErrorCode())));
				}
			}
		}.start();		
	}	
	
	public static TwitterOauthGrabber getAGrabber(String pin, TwitterPINPopup callbackGui) {
		return new TwitterOauthGrabber(pin, callbackGui);
	}
	
	public static class TwitterOauthGrabber implements Runnable {
		
		private String pin;
		private TwitterPINPopup callbackGui;
		
		public TwitterOauthGrabber(String pin, TwitterPINPopup callbackGui) {
			this.pin = pin;
			this.callbackGui = callbackGui;
		}
		
		@Override
		public void run() {
			AccessToken accessToken = null;
			while (accessToken == null || accessToken.getToken() == null) {
				try {
					accessToken = TwitterKeys.twitter.getOAuthAccessToken(TwitterKeys.requestToken, pin);
				} catch (TwitterException e) {
				}
			}
			storeAccessToken(accessToken);
			callbackGui.goToPostGUI();
		}

		private void storeAccessToken(AccessToken accessToken) {
			System.out.println("[VoxelCam] Setting Twitter access token");
			LiteModVoxelCam.getConfig().setProperty(VoxelCamConfig.TWITTERAUTHTOKEN, accessToken.getToken());
			LiteModVoxelCam.getConfig().setProperty(VoxelCamConfig.TWITTERAUTHTOKENSECRET, accessToken.getTokenSecret());
			LiteModVoxelCam.getConfig().setProperty(VoxelCamConfig.TWITTERUSERID, String.valueOf(accessToken.getUserId()));
		}
	}
}
