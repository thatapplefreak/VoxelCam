package com.thatapplefreak.voxelcam.gametest;

import com.thatapplefreak.voxelcam.client.gui.SharePopup;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The share popup's own wiring, which the unit tests cannot reach: they cover the
 * uploader and the file copy, but not the screen that calls them.
 *
 * Only "Copy file path" is actually pressed. The other three would each escape the
 * test: the save dialog blocks on a native window with no headless mode, revealing
 * spawns the platform file manager, and the link button uploads to the real catbox.
 */
public class SharePopupTest implements FabricClientGameTest {

	private static final List<String> EXPECTED_BUTTONS = List.of(
			"Save a copy…",
			"Show in file manager",
			"Copy file path",
			"Get a share link",
			"Done");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		File screenshot = context.computeOnClient(client -> {
			File file = new File(client.gameDirectory, "voxelcam-gametest-shots/share-target.png");
			try {
				Files.createDirectories(file.getParentFile().toPath());
				java.awt.image.BufferedImage image =
						new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB);
				javax.imageio.ImageIO.write(image, "png", file);
			} catch (IOException e) {
				throw new AssertionError("could not write the share target", e);
			}
			return file;
		});

		// A null parent is fine here: nothing is pressed that navigates back.
		context.setScreen(() -> new SharePopup(null, screenshot));
		context.waitForScreen(SharePopup.class);
		context.waitTicks(5);

		assertEveryTargetIsOffered(context);
		assertCopyPathReachesTheClipboard(context, screenshot);

		context.takeScreenshot("share-popup");

		context.setScreen(TitleScreen::new);
		context.waitForScreen(TitleScreen.class);
	}

	private static void assertEveryTargetIsOffered(ClientGameTestContext context) {
		List<String> labels = context.computeOnClient(client -> {
			List<String> found = new ArrayList<>();
			for (AbstractWidget widget : Screens.getWidgets(client.gui.screen())) {
				found.add(widget.getMessage().getString());
			}
			return found;
		});

		for (String expected : EXPECTED_BUTTONS) {
			if (!labels.contains(expected)) {
				throw new AssertionError("share popup is missing \"" + expected + "\", offers " + labels);
			}
		}
	}

	/**
	 * The one share target that runs end to end in a test. It goes through GLFW's
	 * clipboard, which is text-only — hence a path rather than the image — and needs
	 * a real window, so this is the only place it can be exercised at all.
	 */
	private static void assertCopyPathReachesTheClipboard(ClientGameTestContext context, File screenshot) {
		context.runOnClient(client -> client.keyboardHandler.setClipboard("voxelcam-gametest-sentinel"));

		context.clickScreenButton("voxelcam.share.copypath");
		context.waitTicks(5);

		String clipboard = context.computeOnClient(client -> client.keyboardHandler.getClipboard());
		String expected = screenshot.getAbsolutePath();
		if (!expected.equals(clipboard)) {
			throw new AssertionError("clipboard should hold " + expected + ", held " + clipboard);
		}
	}
}
