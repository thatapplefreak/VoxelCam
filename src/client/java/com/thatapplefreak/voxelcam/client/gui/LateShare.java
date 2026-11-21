package com.thatapplefreak.voxelcam.client.gui;

/**
 * What a share that finishes late may still do.
 *
 * <p>Every {@link SharePopup} is a new object and an upload it started keeps running after the
 * player backs out, so several can finish in any order and long after their popup has gone. A link
 * is copied only for the upload started last, and only while its popup is still the one the player
 * is in. Anything else would replace the link the popup in front of them just said was copied, or
 * whatever they have copied since. A smaller copy that finishes after its popup was left is not
 * sent anywhere, on the same test of whether the popup is still open.
 *
 * <p>Its own class, holding no Minecraft types, because the popup's send paths cannot be reached by
 * a test: pressing the link button uploads to the real catbox, and Save a copy opens a native
 * dialog.
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

	/**
	 * Whether the player is still in a share popup: they have not backed out of it, and the screen
	 * that is up is the popup itself or the size prompt that popup opened.
	 *
	 * <p>The prompt has to be the popup's own. Any size prompt would do for a popup that was
	 * replaced and is gone, as soon as a later share popup asked its own size question. Screens
	 * are compared by identity and typed as {@code Object} so that this needs no live screen;
	 * {@code ownSizePrompt} is null for a popup with no prompt up.
	 */
	static boolean popupStillOpen(boolean closed, Object currentScreen, Object popup, Object ownSizePrompt) {
		// No screen and no size prompt are both null, and a null check first is what keeps one
		// from matching the other.
		if (closed || currentScreen == null) {
			return false;
		}
		return currentScreen == popup || currentScreen == ownSizePrompt;
	}
}
