package com.thatapplefreak.voxelcam.upload.imgur;

/**
 * Imgur agent to delete an uploaded file
 * 
 * @author Mumfrey
 */
public class ImgurDelete extends Imgur {
	private static final String ROUTE = "image";

	/**
	 * @param deleteHash
	 *            Hash of the uploaded image to delete
	 */
	public ImgurDelete(String deleteHash) {
		super(Method.DELETE, ImgurDelete.ROUTE, ImgurDeleteResponse.class, deleteHash);
	}

	@Override
	protected void assemble() {
	}
}
