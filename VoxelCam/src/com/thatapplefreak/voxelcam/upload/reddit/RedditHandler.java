package com.thatapplefreak.voxelcam.upload.reddit;

import java.io.File;

import com.thatapplefreak.voxelcam.gui.manager.PostPopup;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurCallback;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurResponse;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUpload;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUploadFailedPopup;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUploadResponse;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUploadSuccessPopup;

public abstract class RedditHandler {
	
	/**
	 * user is logged into reddit
	 */
	private static boolean loggedIn = false;
	
	/**
	 * Post image to reddit
	 * @param postTitle
	 * @param screenshot
	 */
	public static void doRedditPost(final String postTitle, final String subreddit, final File screenshot, final IRedditPostCallback callback) {
		final ImgurUpload poster = new ImgurUpload(screenshot, screenshot.getName(), "");
		poster.start(new ImgurCallback() {

			@Override
			public void onHTTPFailure(int responseCode, String responseMessage) {
				callback.onPostFailure();
			}

			@Override
			public void onCompleted(ImgurResponse response) {

				ImgurUploadResponse uploadResponse = (ImgurUploadResponse) poster.getResponse();
				if (uploadResponse.isSuccessful()) {
					try {
						// POST to reddit
					} catch (Exception e) {
						callback.onPostFailure();
					}
				} else {
					callback.onPostFailure();
				}
			}
		});
	}
	
	/**
	 * Log the user into reddit
	 * @param username
	 * @param password
	 * @return
	 */
	public static void login(final String username, final String password, final ILoginCallback logincallback) {
		new Thread() {
			@Override
			public void run() {
				try {
					//LOGIN
					logincallback.onLoginSuccess();
					loggedIn = true;
				} catch (Exception e) {
					logincallback.onLoginFailure();
				}			
			}
		}.start();
	}
	
	/**
	 * @return True if the user is logged in
	 */
	public static boolean isLoggedIn() {
		return loggedIn;
	}
	
}
