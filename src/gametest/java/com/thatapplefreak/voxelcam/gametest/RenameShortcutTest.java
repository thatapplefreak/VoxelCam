package com.thatapplefreak.voxelcam.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.gui.GuiSettings;
import com.thatapplefreak.voxelcam.client.gui.RenamePopup;
import com.thatapplefreak.voxelcam.client.gui.SharePopup;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.InputType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
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

		addSecondFixture(dir);
		context.runOnClient(client -> client.setLastInputType(InputType.MOUSE));
		context.setScreen(() -> manager);
		context.waitForScreen(GuiScreenShotManager.class);
		context.waitTicks(5);

		assertShortcutsSurviveEveryReturnFromAPopup(context, dir);
		assertTypingASearchKeepsTheField(context);

		// Put the input type back so this test cannot change how a later one behaves.
		context.runOnClient(client -> client.setLastInputType(InputType.MOUSE));
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

	/**
	 * The rebuild on every return from a popup calls the no-arg {@code setInitialFocus()}, and
	 * after a keyboard input vanilla's default tabs onto the first focusable widget, which is
	 * the search field. That silently switched every shortcut off until the next click. Arrow
	 * keys and Tab are what leave the input type on keyboard; it is set by hand here because
	 * pressing one would also move the selection or the focus this test is watching.
	 */
	private static void assertShortcutsSurviveEveryReturnFromAPopup(ClientGameTestContext context, File dir) {
		keyboardWasLast(context);
		context.getInput().pressKey(InputConstants.KEY_F2);
		context.waitTicks(5);
		expectScreen(context, RenamePopup.class.getSimpleName(), "F2 in the manager");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		assertShortcutsApply(context, "Escape from the rename dialog");

		context.getInput().pressKey(InputConstants.KEY_DELETE);
		context.waitTicks(5);
		expectScreen(context, ConfirmScreen.class.getSimpleName(), "Delete after Escape from the rename dialog");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		assertShortcutsApply(context, "cancelling a delete");

		int before = listing(dir).size();
		context.getInput().pressKey(InputConstants.KEY_DELETE);
		context.waitTicks(5);
		expectScreen(context, ConfirmScreen.class.getSimpleName(), "Delete after cancelling a delete");
		context.clickScreenButton("voxelcam.delete");
		context.waitTicks(5);
		if (listing(dir).size() != before - 1) {
			throw new AssertionError("confirming the delete should remove one screenshot, left " + listing(dir));
		}
		assertShortcutsApply(context, "confirming a delete");

		context.clickScreenButton("voxelcam.share");
		context.waitTicks(5);
		expectScreen(context, SharePopup.class.getSimpleName(), "the Share button");
		keyboardWasLast(context);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		assertShortcutsApply(context, "Escape from the share popup");

		context.clickScreenButton("voxelcam.settings.gear");
		context.waitTicks(5);
		expectScreen(context, GuiSettings.class.getSimpleName(), "the settings gear");
		keyboardWasLast(context);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		assertShortcutsApply(context, "Escape from settings");

		context.getInput().pressKey(InputConstants.KEY_F2);
		context.waitTicks(5);
		expectScreen(context, RenamePopup.class.getSimpleName(), "F2 after Escape from settings");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		expectScreen(context, GuiScreenShotManager.class.getSimpleName(), "Escape from the rename dialog");
	}

	/** The search refilters on every keystroke; a refilter that took focus would eat the next one. */
	private static void assertTypingASearchKeepsTheField(ClientGameTestContext context) {
		keyboardWasLast(context);
		context.runOnClient(client -> client.gui.screen().setFocused(searchField(client.gui.screen())));
		context.getInput().typeChars("sh");
		context.waitTicks(5);

		String state = context.computeOnClient(client -> {
			EditBox box = searchField(client.gui.screen());
			return box.getValue() + (box.isFocused() ? "" : " (not focused)");
		});
		if (!"sh".equals(state)) {
			throw new AssertionError("typing two characters into the search should leave both there, field was " + state);
		}
	}

	private static void keyboardWasLast(ClientGameTestContext context) {
		context.runOnClient(client -> client.setLastInputType(InputType.KEYBOARD_ARROW));
	}

	private static void assertShortcutsApply(ClientGameTestContext context, String after) {
		expectScreen(context, GuiScreenShotManager.class.getSimpleName(), after);
		String focused = context.computeOnClient(client -> {
			GuiEventListener widget = client.gui.screen().getFocused();
			return widget == null ? "nothing" : widget.getClass().getSimpleName();
		});
		if (EditBox.class.getSimpleName().equals(focused)) {
			throw new AssertionError("after " + after + " the search field should not have focus, or the shortcuts are off");
		}
	}

	private static void expectScreen(ClientGameTestContext context, String expected, String after) {
		String screen = currentScreen(context);
		if (!expected.equals(screen)) {
			throw new AssertionError("after " + after + " the screen should be " + expected + ", was " + screen);
		}
	}

	private static EditBox searchField(Screen screen) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof EditBox box) {
				return box;
			}
		}
		throw new AssertionError("the manager should have a search field");
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

	/** Two, so that confirming a delete still leaves something for the shortcuts to act on. */
	private static void addSecondFixture(File dir) {
		try {
			ScreenshotFixtures.writePng(new File(dir, "shortcut-second.png"), 320, 180);
		} catch (IOException e) {
			throw new AssertionError("could not write the second rename shortcut fixture", e);
		}
	}
}
