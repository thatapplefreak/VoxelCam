package com.thatapplefreak.voxelcam.gametest;

import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.gui.ScreenshotMetadata;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureContext;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTag;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTrigger;
import com.thatapplefreak.voxelcam.client.screenshot.PngTextChunk;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
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
		assertRowsAndDetailsSayWhatFits(context);

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

	/**
	 * What the playtest saw at the default window: rows reading "854×480 · 95" with the unit cut
	 * off, four rows all named "Today 19:01", and a details line ending before the advancement
	 * that explains an automatic capture. The rows can only be judged from the screenshots; the
	 * details line is read back as text as well, at the default window and at one twice as wide.
	 */
	private static void assertRowsAndDetailsSayWhatFits(ClientGameTestContext context) {
		File dir = context.computeOnClient(client -> new File(client.gameDirectory, "voxelcam-gametest-rows"));
		File moment = new File(dir, "2026-11-21_19.01.45.png");
		try {
			ScreenshotFixtures.emptyFolder(dir);
			LocalDateTime minute = LocalDateTime.now().withSecond(0).withNano(0);
			for (int second : new int[] { 5, 15, 30 }) {
				File file = new File(dir, String.format(java.util.Locale.ROOT, "2026-11-21_19.01.%02d.png", second));
				ScreenshotFixtures.writePng(file, 854, 480);
				if (second == 30) {
					// Megabytes rather than kilobytes: trailing bytes after IEND, which decoders ignore.
					try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
						raf.setLength(4_400_000);
					}
				}
				setModified(file, minute.withSecond(second));
			}
			ScreenshotFixtures.writePng(moment, 854, 480);
			Map<String, String> tags = new HashMap<>(new MomentTag(MomentTrigger.ADVANCEMENT, "We Need to Go Deeper").toTags());
			tags.putAll(new CaptureContext("minecraft:overworld", 12, 64, -45, null).toTags());
			PngTextChunk.embed(moment, tags);
			setModified(moment, minute.withSecond(45));
			File renamed = new File(dir, "0001_empty-manager-after-delete.png");
			ScreenshotFixtures.writePng(renamed, 854, 480);
			setModified(renamed, minute.minusMinutes(5));
		} catch (IOException e) {
			throw new AssertionError("could not write the row fixtures", e);
		}

		int[] original = context.computeOnClient(client ->
				new int[] { client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight() });
		try {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(client -> VoxelCamIO.selectPhoto(moment));
			context.setScreen(() -> new GuiScreenShotManager(dir));
			context.waitForScreen(GuiScreenShotManager.class);
			context.waitTicks(40);
			context.takeScreenshot("manager-rows-default-window");
			String narrow = detailsOf(context, moment);
			if (!narrow.contains("We Need to Go Deeper")) {
				throw new AssertionError("an automatic capture's reason is not on the details line at 854x480: " + narrow);
			}

			// The GUI scale is still 2, so this doubles the room every line has.
			context.getInput().resizeWindow(1708, 480);
			context.waitTicks(20);
			context.takeScreenshot("manager-rows-wide-window");
			String wide = detailsOf(context, moment);
			if (!wide.contains("We Need to Go Deeper") || !wide.contains("12, 64, -45") || !wide.contains(moment.getName())) {
				throw new AssertionError("the details line should hold every fact on a wide window: " + wide);
			}
		} finally {
			context.getInput().resizeWindow(original[0], original[1]);
			context.runOnClient(client -> VoxelCamIO.selectPhoto(null));
			context.waitTicks(5);
		}
	}

	private static String detailsOf(ClientGameTestContext context, File selected) {
		return context.computeOnClient(client ->
				((GuiScreenShotManager) client.gui.screen()).detailsLine(selected));
	}

	private static void setModified(File file, LocalDateTime when) {
		if (!file.setLastModified(when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())) {
			throw new AssertionError("could not date " + file);
		}
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
