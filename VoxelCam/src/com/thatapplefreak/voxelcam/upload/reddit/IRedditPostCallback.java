package com.thatapplefreak.voxelcam.upload.reddit;

import java.net.URL;

public interface IRedditPostCallback {

	void onPostSuccess(URL postUrl);
	
	void onPostFailure();
	
}
