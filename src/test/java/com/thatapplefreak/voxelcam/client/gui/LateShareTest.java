package com.thatapplefreak.voxelcam.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Which upload's link may reach the clipboard, and whether the player is still in the popup that
 * asked for it. The share popup's upload-result branch cannot be driven by a test, since it would
 * upload to the real catbox, so the decisions it makes there live in {@link LateShare} and are
 * tested without a popup, a screen or an upload.
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

	// --- whether the player is still in the popup -------------------------------------------------

	// Stand-ins for screens. The decision only ever asks whether two of them are the same one.
	private static final Object POPUP = new Object();
	private static final Object ITS_SIZE_PROMPT = new Object();
	private static final Object ANOTHER_POPUPS_SIZE_PROMPT = new Object();
	private static final Object DEATH_SCREEN = new Object();

	@Test
	void aPopupOnScreenIsStillOpen() {
		assertTrue(LateShare.popupStillOpen(false, POPUP, POPUP, null));
	}

	/** The player comes back from the size prompt to the popup's own status line, so a link that
	 * lands meanwhile is still theirs to have. */
	@Test
	void aPopupBehindItsOwnSizePromptIsStillOpen() {
		assertTrue(LateShare.popupStillOpen(false, ITS_SIZE_PROMPT, POPUP, ITS_SIZE_PROMPT));
	}

	/**
	 * A size prompt is not enough: it has to be this popup's. A popup a death screen replaced is
	 * gone, and a later share popup asking its own size question must not bring the first one's
	 * late link, or the send it was waiting to make, back to life.
	 */
	@Test
	void anotherPopupsSizePromptDoesNotMakeAPopupOpen() {
		assertFalse(LateShare.popupStillOpen(false, ANOTHER_POPUPS_SIZE_PROMPT, POPUP, null),
				"a popup that never opened a size prompt");
		assertFalse(LateShare.popupStillOpen(false, ANOTHER_POPUPS_SIZE_PROMPT, POPUP, ITS_SIZE_PROMPT),
				"a popup whose own size prompt was itself replaced");
	}

	/** Vanilla takes a replaced screen away through {@code removed()} and never {@code onClose()},
	 * so the popup's own closed flag says nothing here. */
	@Test
	void aPopupReplacedByAnotherScreenIsNotOpen() {
		assertFalse(LateShare.popupStillOpen(false, DEATH_SCREEN, POPUP, null));
	}

	/** No screen and no size prompt are both "nothing", and must not be taken for each other. */
	@Test
	void aPopupIsNotOpenWhenNoScreenIsUp() {
		assertFalse(LateShare.popupStillOpen(false, null, POPUP, null));
	}

	@Test
	void aPopupThePlayerBackedOutOfIsNotOpen() {
		assertFalse(LateShare.popupStillOpen(true, POPUP, POPUP, null));
		assertFalse(LateShare.popupStillOpen(true, ITS_SIZE_PROMPT, POPUP, ITS_SIZE_PROMPT));
	}
}
