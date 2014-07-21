package com.thatapplefreak.voxelcam.upload.imgur;

import java.io.File;


/**
 * Imgur agent to upload a file, callers should get the URL from the response
 * data "
 * 
 * @author Mumfrey
 */
public class ImgurUpload extends Imgur {
	private static final String ROUTE = "image";

	private final File file;

	private final String title;

	private final String description;

	public ImgurUpload(File imageFile, String imageTitle, String imageDescription) {
		super(Method.POST, ImgurUpload.ROUTE, ImgurUploadResponse.class);

		this.title = imageTitle;
		this.file = imageFile;
		this.description = imageDescription;
	}

	@Override
	protected void assemble() {
		this.postData.put("image", this.file);
		this.postData.put("type", "file");

		if (this.title != null)
			this.postData.put("title", this.title);
		if (this.description != null)
			this.postData.put("description", this.description);
	}
}
