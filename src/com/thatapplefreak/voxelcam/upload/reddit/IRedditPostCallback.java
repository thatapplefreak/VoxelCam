package com.thatapplefreak.voxelcam.upload.reddit;

import java.net.URL;

public interface IRedditPostCallback {

	void onPostSuccess(String string);
	
	void onPostFailure();
	
}
