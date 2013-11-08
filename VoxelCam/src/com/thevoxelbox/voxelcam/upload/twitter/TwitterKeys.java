package com.thevoxelbox.voxelcam.upload.twitter;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.RequestToken;

public abstract class TwitterKeys {

	public static final String CONSUMER_KEY = "okIIDosE4TsrRP3JvXufw";
	public static final String CONSUMER_SECRET = "dFJIErDmYr61YwQfDdAGMAt79dCJGu1mpiflCAa2c";

	public static Twitter twitter = TwitterFactory.getSingleton();
	public static RequestToken requestToken;
	static {
		twitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
		try {
			requestToken = twitter.getOAuthRequestToken();
		} catch (TwitterException e) {
			e.printStackTrace();
		}
	}

}
