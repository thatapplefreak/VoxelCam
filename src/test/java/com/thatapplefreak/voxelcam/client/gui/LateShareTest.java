package com.thatapplefreak.voxelcam.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Which upload's link may reach the clipboard. The share popup's upload-result branch cannot be
 * driven by a test, since it would upload to the real catbox, so the decision it makes there lives
 * in {@link LateShare} and is tested without a popup or an upload.
 */
class LateShareTest {

	@Test
	void theLatestUploadsLinkIsCopiedWhileItsPopupIsOpen() {
		int upload = LateShare.beginUpload();

		assertTrue(LateShare.mayCopyLink(upload, true));
	}

	/**
	 * Share on a large screenshot, get a link, press Done; then Share on a small one and get its
	 * link. The small one finishes first and its popup says the link is on the clipboard. When the
	 * large one finishes it must not put its own link there instead.
	 */
	@Test
	void anEarlierUploadThatFinishesLateLeavesTheClipboardAlone() {
		int large = LateShare.beginUpload();
		int small = LateShare.beginUpload();

		assertTrue(LateShare.mayCopyLink(small, true), "the popup in front of the player gets its link copied");
		assertFalse(LateShare.mayCopyLink(large, false), "the upload left behind does not replace it");
		assertFalse(LateShare.mayCopyLink(large, true), "whatever became of its popup");
	}

	/** With one popup: a link that arrives after the player backed out would replace whatever they
	 * have copied since. */
	@Test
	void aLinkThatArrivesAfterItsPopupWasLeftIsNotCopied() {
		int upload = LateShare.beginUpload();

		assertFalse(LateShare.mayCopyLink(upload, false));
	}
}
