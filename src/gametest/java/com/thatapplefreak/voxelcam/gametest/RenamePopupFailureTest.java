package com.thatapplefreak.voxelcam.gametest;

import com.thatapplefreak.voxelcam.client.gui.RenamePopup;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * What the rename dialog does when the filesystem refuses. The refusal itself is unit
 * tested; only a real client can show whether the dialog then stays up to say so, since
 * {@code confirm()} needs a screen, a focused text field and a pressable button.
 *
 * The source file is deleted after being selected, which is the reported case — a cloud
 * sync or another window moving the screenshot out from under an open manager. Deleting
 * it rather than leaving no selection at all matters: {@code rename} also returns null
 * for a null selection, and asserting against that would prove nothing about a failed
 * {@code renameTo}.
 */
public class RenamePopupFailureTest implements FabricClientGameTest {

	private static final String ORIGINAL_NAME = "rename-failure-target";
	private static final String TYPED = "sunset";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, "voxelcam-gametest-shots"));
		File missing = new File(dir, ORIGINAL_NAME + ".png");

		context.runOnClient(client -> {
			try {
				Files.createDirectories(dir.toPath());
				Files.deleteIfExists(missing.toPath());
			} catch (IOException e) {
				throw new AssertionError("could not prepare the rename target", e);
			}
			// The selection the popup will try to rename, pointing at a file that is gone.
			VoxelCamIO.selectPhoto(missing);
		});

		// A null parent is safe only while the popup stays open — which is the assertion.
		context.setScreen(() -> new RenamePopup(null, dir, missing));
		context.waitForScreen(RenamePopup.class);
		context.waitTicks(5);

		// Any name but the original: an unchanged one leaves OK inactive, and the test
		// would then be about the validation rather than about the failed rename.
		context.getInput().typeChars(TYPED);
		context.waitTicks(5);

		context.clickScreenButton("voxelcam.ok");
		context.waitTicks(5);

		assertTheDialogIsStillUp(context);
		assertNothingWasRenamed(context, dir);

		// Renders the extract path with the failure line present.
		context.takeScreenshot("rename-popup-failed");

		context.runOnClient(client -> VoxelCamIO.selectPhoto(null));
		context.setScreen(TitleScreen::new);
		context.waitForScreen(TitleScreen.class);
	}

	private static void assertTheDialogIsStillUp(ClientGameTestContext context) {
		String screen = context.computeOnClient(client -> client.gui.screen() == null
				? "nothing"
				: client.gui.screen().getClass().getSimpleName());

		if (!RenamePopup.class.getSimpleName().equals(screen)) {
			throw new AssertionError(
					"a failed rename should leave the dialog open to report it, screen was " + screen);
		}
	}

	/**
	 * By substring, not by an exact name: whether {@code setValue} leaves a selection for
	 * the typed characters to replace decides whether the field ends up holding
	 * {@code sunset} or the original with it appended, and neither should have appeared.
	 */
	private static void assertNothingWasRenamed(ClientGameTestContext context, File dir) {
		String created = context.computeOnClient(client -> {
			String[] names = dir.list();
			if (names != null) {
				for (String name : names) {
					if (name.contains(TYPED)) {
						return name;
					}
				}
			}
			return null;
		});

		if (created != null) {
			throw new AssertionError("a refused rename should have written nothing, found " + created);
		}
	}
}
