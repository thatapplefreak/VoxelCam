package com.thatapplefreak.voxelcam.upload.imgur;

import java.util.HashMap;
import java.util.Map;


/**
 * 
 * @author Mumfrey
 */
public class ImgurUploadResponse implements ImgurResponse {
	private Map<String, String> data = new HashMap<String, String>();

	private boolean success;

	private int status;

	@Override
	public String get(String key) {
		return this.data.get(key);
	}

	@Override
	public int getInt(String key) {
		try {
			return Integer.parseInt(key);
		} catch (Exception ex) {
		}

		return 0;
	}

	@Override
	public boolean isSuccessful() {
		return this.success;
	}

	@Override
	public int getStatus() {
		return this.status;
	}

	/**
	 * imgur ID of the uploaded image
	 */
	public String getID() {
		return this.data.get("id");
	}

	/**
	 * When uploading anonymously, this hash can be used to delete the image by
	 * passing the hash to a delete task
	 */
	public String getDeleteHash() {
		return this.data.get("deletehash");
	}

	/**
	 * URL to the uploaded image
	 */
	public String getLink() {
		return this.data.get("link");
	}

	public String getTitle() {
		return this.data.get("title");
	}

	public String getDescription() {
		return this.data.get("description");
	}

	public int getTimestamp() {
		return Integer.parseInt(this.data.get("datetime"));
	}

	/**
	 * MIME type, eg. image/jpeg
	 */
	public String getMimeType() {
		return this.data.get("type");
	}

	public boolean isAnimated() {
		return "true".equalsIgnoreCase(this.data.get("animated"));
	}

	public int getWidth() {
		return Integer.parseInt(this.data.get("width"));
	}

	public int getHeight() {
		return Integer.parseInt(this.data.get("height"));
	}

	public int getSize() {
		return Integer.parseInt(this.data.get("size"));
	}

	public int getViews() {
		return Integer.parseInt(this.data.get("views"));
	}

	public int getBandwidth() {
		return Integer.parseInt(this.data.get("bandwidth"));
	}

	public boolean isFavourite() {
		return "true".equalsIgnoreCase(this.data.get("favorite"));
	}

	public String getNSFW() {
		return this.data.get("nsfw");
	}

	public String getSection() {
		return this.data.get("section");
	}
}
