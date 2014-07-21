package com.thatapplefreak.voxelcam.upload.imgur;

public interface ImgurResponse {
	public abstract int getStatus();

	public abstract boolean isSuccessful();

	public abstract String get(String key);

	public abstract int getInt(String key);
}
