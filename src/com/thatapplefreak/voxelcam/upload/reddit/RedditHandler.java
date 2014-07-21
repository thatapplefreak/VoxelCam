package com.thatapplefreak.voxelcam.upload.reddit;

import java.io.File;
import java.lang.reflect.Method;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.github.jreddit.user.User;
import com.github.jreddit.utils.restclient.HttpRestClient;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurCallback;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurResponse;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUpload;
import com.thatapplefreak.voxelcam.upload.imgur.ImgurUploadResponse;

public abstract class RedditHandler {
	
	protected static User reddit;
	
	/**
	 * user is logged into reddit
	 */
	protected static boolean loggedIn = false;
	
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
						Method m = User.class.getDeclaredMethod("submit", String.class, String.class, boolean.class, String.class);
						m.setAccessible(true);
						Object obj = m.invoke(reddit, postTitle, uploadResponse.getLink(), false, subreddit);
						JSONObject jobj = (JSONObject) obj;
						callback.onPostSuccess(((JSONArray) ((JSONArray) ((JSONArray) jobj.get("jquery")).get(16)).get(3)).get(0).toString());
					} catch (Exception e) {
						e.printStackTrace();
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
		reddit = new User(new HttpRestClient(), username, password);
		new Thread() {
			@Override
			public void run() {
				try {
					reddit.connect();
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
