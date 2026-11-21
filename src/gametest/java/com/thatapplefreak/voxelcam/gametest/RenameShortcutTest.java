package com.thatapplefreak.voxelcam.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.gui.RenamePopup;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The manager's F2 rename shortcut, pressed for real. Nothing short of the physical key can
 * cover it: the shortcut's own code was always right, and what kept it from running is that
 * vanilla offers a press to its global keys before the open screen, and the screenshot key,
 * F2 by default, is one of them. Calling {@code keyPressed} directly would have passed all
 * along.
 *
 * The second half is the other side of the same hook. It takes the key away from vanilla only
 * where the shortcut itself applies, so F2 while typing in the search field still has to take
 * the screenshot it always did.
 */
public class RenameShortcutTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		// Its own directory: the manager lists it, and the screenshot this test is watching for
		// would land in the game's real screenshots folder, which has to be told apart from it.
		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, "voxelcam-rename-shortcut-shots"));
		File screenshotsFolder = context.computeOnClient(client ->
				new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR));
		writeFixture(dir);

		GuiScreenShotManager manager = context.computeOnClient(client -> new GuiScreenShotManager(dir));
		context.setScreen(() -> manager);
		context.waitForScreen(GuiScreenShotManager.class);
		context.waitTicks(20);

		assertF2OpensTheRenameDialogAndTakesNoScreenshot(context, screenshotsFolder);

		context.setScreen(() -> manager);
		context.waitForScreen(GuiScreenShotManager.class);
		context.waitTicks(5);

		assertF2InTheSearchFieldStillTakesAScreenshot(context, screenshotsFolder);

		context.runOnClient(client -> VoxelCamIO.selectPhoto(null));
		context.setScreen(TitleScreen::new);
		context.waitForScreen(TitleScreen.class);
	}

	private static void assertF2OpensTheRenameDialogAndTakesNoScreenshot(ClientGameTestContext context,
			File screenshotsFolder) {
		String selected = context.computeOnClient(client -> {
			File file = VoxelCamIO.getSelectedPhoto();
			return file == null ? "nothing" : file.getName();
		});
		if (!"shortcut-target.png".equals(selected)) {
			throw new AssertionError("the manager should open with its one screenshot selected, selected " + selected);
		}
		Set<String> before = listing(screenshotsFolder);

		context.getInput().pressKey(InputConstants.KEY_F2);
		context.waitTicks(5);

		String screen = currentScreen(context);
		if (!RenamePopup.class.getSimpleName().equals(screen)) {
			throw new AssertionError("F2 in the manager should open the rename dialog, screen was " + screen);
		}

		// Had the press reached vanilla's screenshot key, the screenshot would be taken from the
		// next frame and encoded on the IO worker, so the file appears a little after it.
		context.waitFor(client -> !CaptureMenu.isCapturePending(), 200);
		context.waitTicks(40);

		Set<String> written = newScreenshots(screenshotsFolder, before);
		if (!written.isEmpty()) {
			throw new AssertionError("F2 in the manager should rename, not take a screenshot, wrote " + written);
		}
	}

	private static void assertF2InTheSearchFieldStillTakesAScreenshot(ClientGameTestContext context,
			File screenshotsFolder) {
		context.runOnClient(client -> {
			for (GuiEventListener child : client.gui.screen().children()) {
				if (child instanceof EditBox box) {
					client.gui.screen().setFocused(box);
					return;
				}
			}
			throw new AssertionError("the manager should have a search field");
		});
		context.waitTicks(5);
		Set<String> before = listing(screenshotsFolder);

		context.getInput().pressKey(InputConstants.KEY_F2);
		context.waitTicks(5);

		String screen = currentScreen(context);
		if (!GuiScreenShotManager.class.getSimpleName().equals(screen)) {
			throw new AssertionError("F2 while typing a search should not open a dialog, screen was " + screen);
		}

		context.waitFor(client -> !CaptureMenu.isCapturePending(), 200);
		context.waitTicks(40);

		Set<String> written = newScreenshots(screenshotsFolder, before);
		if (written.size() != 1) {
			throw new AssertionError("F2 while typing a search should still take one screenshot, wrote " + written);
		}
	}

	private static String currentScreen(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() == null
				? "nothing"
				: client.gui.screen().getClass().getSimpleName());
	}

	private static Set<String> listing(File dir) {
		String[] names = dir.list();
		return names == null ? new HashSet<>() : new HashSet<>(Arrays.asList(names));
	}

	/** VoxelCam's own only: Fabric's test screenshots share the folder, numbered {@code 0000_}. */
	private static Set<String> newScreenshots(File dir, Set<String> before) {
		Set<String> after = listing(dir);
		after.removeAll(before);
		after.removeIf(name -> !name.endsWith(".png") || name.matches("\\d{4}_.*"));
		return after;
	}

	private static void writeFixture(File dir) {
		try {
			ScreenshotFixtures.emptyFolder(dir);
			ScreenshotFixtures.writePng(new File(dir, "shortcut-target.png"), 320, 180);
		} catch (IOException e) {
			throw new AssertionError("could not write the rename shortcut fixture", e);
		}
	}
}
