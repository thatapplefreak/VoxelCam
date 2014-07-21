package com.thatapplefreak.voxelcam.upload.reddit;


public interface IRedditPostCallback {

	void onPostSuccess(String string);
	
	void onPostFailure();
	
}
