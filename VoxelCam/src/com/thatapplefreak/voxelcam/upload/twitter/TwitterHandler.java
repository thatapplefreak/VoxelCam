package com.thatapplefreak.voxelcam.upload.twitter;

import java.io.File;

import net.minecraft.client.resources.I18n;

import com.thatapplefreak.voxelcam.VoxelCamCore;
import com.thatapplefreak.voxelcam.VoxelCamConfig;
import com.thatapplefreak.voxelcam.gui.manager.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurCallback;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurResponse;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUpload;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUploadResponse;

import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

public abstract class TwitterHandler {
	
	public static final String CONSUMER_KEY = "okIIDosE4TsrRP3JvXufw";
	public static final String CONSUMER_SECRET = "dFJIErDmYr61YwQfDdAGMAt79dCJGu1mpiflCAa2c";

	public static Twitter twitter = TwitterFactory.getSingleton();
	public static RequestToken requestToken;
	static {
		twitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
		try {
			TwitterHandler.requestToken = twitter.getOAuthRequestToken();
		} catch (TwitterException e) {
			e.printStackTrace();
		}
	}

	public static void doTwitter(final TwitterPostPopup callbackGui, final File screenshot, final String text) {
		Long twitterUserID = Long.parseLong(VoxelCamCore.getConfig().getStringProperty(VoxelCamConfig.TWITTERUSERID));
		String userAuthToken = VoxelCamCore.getConfig().getStringProperty(VoxelCamConfig.TWITTERAUTHTOKEN);
		String userAuthTokenSecret = VoxelCamCore.getConfig().getStringProperty(VoxelCamConfig.TWITTERAUTHTOKENSECRET);
		twitter.setOAuthAccessToken(new AccessToken(userAuthToken, userAuthTokenSecret, twitterUserID));
		new Thread("Twitter_Post_Thread") {
			@Override
			public void run() {
				StatusUpdate statusupdate = new StatusUpdate(text + " #VoxelCam");
				statusupdate.setMedia(screenshot);
				try {
					Status status = twitter.updateStatus(statusupdate);
					String address = "http://twitter.com/" + status.getUser().getScreenName() + "/status/" + status.getId();
					callbackGui.onUploadComplete(new TwitterUploadSuccessPopup(callbackGui.getParentScreen(), status.getId(), address));
				} catch (TwitterException e) {
					callbackGui.onUploadComplete(new TwitterUploadFailedPopup(callbackGui, statusupdate, I18n.format("errorcode") + ": " + Integer.toString(e.getErrorCode())));
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
					accessToken = twitter.getOAuthAccessToken(TwitterHandler.requestToken, pin);
				} catch (TwitterException e) {
				}
			}
			storeAccessToken(accessToken);
			callbackGui.goToPostGUI();
		}

		private void storeAccessToken(AccessToken accessToken) {
			System.out.println("[VoxelCam] Setting Twitter access token");
			VoxelCamCore.getConfig().setProperty(VoxelCamConfig.TWITTERAUTHTOKEN, accessToken.getToken());
			VoxelCamCore.getConfig().setProperty(VoxelCamConfig.TWITTERAUTHTOKENSECRET, accessToken.getTokenSecret());
			VoxelCamCore.getConfig().setProperty(VoxelCamConfig.TWITTERUSERID, String.valueOf(accessToken.getUserId()));
		}
	}
}
