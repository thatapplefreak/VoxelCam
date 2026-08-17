package com.thatapplefreak.voxelcam.client.upload;

import java.util.Map;

/** Mirrors Imgur API v3's response shape: {"data": {...}, "success": bool, "status": int}. */
public class ImgurUploadResponse {

	Map<String, String> data;
	boolean success;
	int status;

	public boolean isSuccessful() {
		return success;
	}

	public int getStatus() {
		return status;
	}

	public String getLink() {
		return data != null ? data.get("link") : null;
	}

	public String getDeleteHash() {
		return data != null ? data.get("deletehash") : null;
	}
}
