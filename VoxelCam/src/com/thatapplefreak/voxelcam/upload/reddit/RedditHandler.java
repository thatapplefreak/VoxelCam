package com.thatapplefreak.voxelcam.upload.reddit;

import java.io.File;

import com.cd.reddit.Reddit;
import com.cd.reddit.RedditException;
import com.cd.reddit.json.mapping.RedditJsonMessage;

public abstract class RedditHandler {
	
	/**
	 * Reddit API instance
	 */
	private static Reddit reddit;
	
	/**
	 * user is logged into reddit
	 */
	private static boolean loggedIn = false;
	
	/**
	 * Post image to reddit
	 * @param PostTitle
	 * @param screenshot
	 */
	public static void doRedditPost(final String PostTitle, final File screenshot, IRedditPostCallback callback) {
		
	}
	
	/**
	 * Log the user into reddit
	 * @param username
	 * @param password
	 * @return
	 */
	public static void login(final String username, final String password, final ILoginCallback logincallback) {
		reddit = new Reddit(username);
		new Thread() {
			@Override
			public void run() {
				RedditJsonMessage respmessage = null;
				try {
					respmessage = reddit.login(username, password);
					logincallback.onLoginSuccess();
					loggedIn = true;
				} catch (RedditException e) {
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
