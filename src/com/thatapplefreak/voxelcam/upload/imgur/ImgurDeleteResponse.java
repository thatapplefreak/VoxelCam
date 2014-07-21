package com.thatapplefreak.voxelcam.upload.imgur;

public class ImgurDeleteResponse implements ImgurResponse {
	private boolean data;

	private boolean success;

	private int status;

	@Override
	public int getStatus() {
		return this.status;
	}

	@Override
	public boolean isSuccessful() {
		return this.success;
	}

	@Override
	public String get(String key) {
		if ("data".equalsIgnoreCase(key))
			return String.valueOf(this.data);
		return null;
	}

	@Override
	public int getInt(String key) {
		if ("data".equalsIgnoreCase(key))
			return this.data ? 1 : 0;
		return 0;
	}

}
