package com.thatapplefreak.voxelcam.client.gui;

/**
 * What a share that finishes late may still do.
 *
 * <p>Every {@link SharePopup} is a new object and an upload it started keeps running after the
 * player backs out, so several can finish in any order and long after their popup has gone. A link
 * is copied only for the upload started last, and only while its popup is still the one the player
 * is in. Anything else would replace the link the popup in front of them just said was copied, or
 * whatever they have copied since.
 *
 * <p>Its own class, holding no Minecraft types, because the popup's upload-result branch cannot be
 * reached by a test: pressing the link button uploads to the real catbox.
 */
final class LateShare {

	// Render thread only: an upload begins from a button press and its result is handled inside
	// minecraft.execute.
	private static int latestUpload;

	private LateShare() {
	}

	/** Marks an upload as started. The result is what {@link #mayCopyLink} is asked about later. */
	static int beginUpload() {
		return ++latestUpload;
	}

	static boolean mayCopyLink(int upload, boolean popupStillOpen) {
		return popupStillOpen && upload == latestUpload;
	}
}
