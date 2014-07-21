package com.thatapplefreak.voxelcam.upload.imgur;

public interface ImgurCallback {
	public abstract void onCompleted(ImgurResponse response);

	public abstract void onHTTPFailure(int responseCode, String responseMessage);
}
