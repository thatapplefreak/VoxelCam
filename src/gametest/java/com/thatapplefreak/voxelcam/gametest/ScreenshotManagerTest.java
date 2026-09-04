package com.thatapplefreak.voxelcam.gametest;

import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.gui.ScreenshotMetadata;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Drives the manager against real files on disk. The point is the render path:
 * 26.x moved screens from immediate-mode drawing to render-state extraction, and a
 * screen that extracts nothing still opens perfectly happily — it is just blank.
 * Rendering a frame with content present is what catches that.
 */
public class ScreenshotManagerTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		// Its own directory rather than the game's screenshots folder: the gametest
		// API writes its captures there too, so sharing it makes the expected file
		// count depend on which tests ran first.
		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, "voxelcam-gametest-shots"));
		writeFixtures(dir);

		context.setScreen(() -> new GuiScreenShotManager(dir));
		context.waitForScreen(GuiScreenShotManager.class);
		// Thumbnails decode off-thread and upload on the render thread, so the first
		// frames legitimately have no image yet.
		context.waitTicks(40);

		int listed = context.computeOnClient(client -> VoxelCamIO.getScreenShotFiles().size());
		if (listed != 3) {
			throw new AssertionError("manager should list the 3 fixtures, listed " + listed);
		}

		// Renders a full frame through the extract path with rows, thumbnails and a
		// preview all present.
		context.takeScreenshot("manager-with-screenshots");

		assertSelectionSurvivesRename(context, dir);
		assertFavoriteTogglePersistsAndRenders(context, dir);

		context.setScreen(TitleScreen::new);
		context.waitForScreen(TitleScreen.class);
	}

	/**
	 * Rename is one of only two operations in the mod that touch user data, and the
	 * selection following the renamed file is what stops the next action targeting a
	 * path that no longer exists.
	 */
	private static void assertSelectionSurvivesRename(ClientGameTestContext context, File dir) {
		context.runOnClient(client -> {
			File original = new File(dir, "gametest-b.png");
			VoxelCamIO.selectPhoto(original);

			File renamed = VoxelCamIO.rename(dir, "gametest-renamed");
			if (renamed == null) {
				throw new AssertionError("rename returned null");
			}
			if (!renamed.exists() || original.exists()) {
				throw new AssertionError("rename did not move the file on disk");
			}
			if (!renamed.equals(VoxelCamIO.getSelectedPhoto())) {
				throw new AssertionError("selection did not follow the rename");
			}
			// Leave the directory as we found it for any later test.
			if (!renamed.renameTo(original)) {
				throw new AssertionError("could not restore the fixture name");
			}
			VoxelCamIO.selectPhoto(null);
		});
	}

	/**
	 * None of the fixtures start out starred, so nothing above ever exercises the row's
	 * star badge or the toggle button's gold tint — both only paint anything once a file
	 * actually reads as starred.
	 */
	private static void assertFavoriteTogglePersistsAndRenders(ClientGameTestContext context, File dir) {
		context.runOnClient(client -> {
			File file = new File(dir, "gametest-a.png");
			VoxelCamIO.selectPhoto(file);
			if (VoxelCamIO.isSelectedFavorite()) {
				throw new AssertionError("fixture should not start out favorited");
			}

			VoxelCamIO.toggleSelectedFavorite();
			if (!VoxelCamIO.isSelectedFavorite()) {
				throw new AssertionError("toggling did not star the file");
			}
			// Mirrors what the manager's own toggle button does after a real click, so the
			// list row picks up the change on its very next render rather than a stale miss.
			ScreenshotMetadata.forget(file);
			// The row badge and the button's tint are both painted from the cached flag, so
			// the screenshot below only proves anything if the cache agrees with the file.
			if (ScreenshotMetadata.isStarred(file) != VoxelCamIO.isSelectedFavorite()) {
				throw new AssertionError("the cached starred flag disagrees with the file");
			}
		});

		// Renders the star badge on the row and the gold-tinted favorite button.
		context.waitTicks(5);
		context.takeScreenshot("manager-with-favorite");

		context.runOnClient(client -> {
			File file = new File(dir, "gametest-a.png");
			VoxelCamIO.toggleSelectedFavorite();
			if (VoxelCamIO.isSelectedFavorite()) {
				throw new AssertionError("toggling back off did not clear the flag");
			}
			ScreenshotMetadata.forget(file);
			if (ScreenshotMetadata.isStarred(file)) {
				throw new AssertionError("the cached starred flag survived the un-star");
			}
			VoxelCamIO.selectPhoto(null);
		});
	}

	private static void writeFixtures(File dir) {
		try {
			if (dir.isDirectory()) {
				File[] stale = dir.listFiles();
				if (stale != null) {
					for (File file : stale) {
						Files.deleteIfExists(file.toPath());
					}
				}
			}
			Files.createDirectories(dir.toPath());
			writePng(new File(dir, "gametest-a.png"), 320, 180);
			writePng(new File(dir, "gametest-b.png"), 200, 200);
			writePng(new File(dir, "gametest-c.png"), 480, 120);
		} catch (IOException e) {
			throw new AssertionError("could not write manager fixtures", e);
		}
	}

	/** A real, decodable PNG — the manager uploads these as textures. */
	private static void writePng(File file, int width, int height) throws IOException {
		java.awt.image.BufferedImage image =
				new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, (x * 255 / width) << 16 | (y * 255 / height) << 8 | 0x80);
			}
		}
		javax.imageio.ImageIO.write(image, "png", file);
	}
}
