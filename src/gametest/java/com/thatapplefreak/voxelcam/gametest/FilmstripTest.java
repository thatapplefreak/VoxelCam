package com.thatapplefreak.voxelcam.gametest;

import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.screenshot.BurstFrame;
import com.thatapplefreak.voxelcam.client.screenshot.PngTextChunk;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The filmstrip under a burst's preview, on a burst written straight to disk. {@code
 * BurstCaptureTest} covers one captured for real; what is checked here is the manager's own
 * behaviour around the strip, which needs a burst and no world.
 */
public class FilmstripTest implements FabricClientGameTest {

	private static final MouseButtonInfo LEFT_CLICK = new MouseButtonInfo(0, 0);

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, "voxelcam-filmstrip-shots"));
		File key = new File(dir, "burst.png");
		writeBurst(dir, key);

		// Before the export, which moves the selection onto the animation it writes.
		assertTheStripHasRoomForACellAtTheSmallestWindow(context, dir, key);
		assertTheFolderIsNotListedAgainForEveryFrame(context, dir, key);
		assertSetAsKeyIsHeldOffWhileAnExportRuns(context, dir, key);

		context.runOnClient(client -> VoxelCamIO.selectPhoto(null));
		context.setScreen(TitleScreen::new);
		context.waitForScreen(TitleScreen.class);
	}

	/**
	 * At 320x240 the GUI scale is 1 and the preview is at its narrowest. The full-width action
	 * column left less than nothing between the scroll buttons there, and the one cell drawn
	 * anyway covered the right scroll button and the edge of Set as key and Export animation.
	 * The cells are not widgets, so what is measured is the room the widgets leave for them;
	 * {@code FilmstripLayoutTest} holds the cell arithmetic to the same row at every width.
	 */
	private static void assertTheStripHasRoomForACellAtTheSmallestWindow(ClientGameTestContext context, File dir,
			File key) {
		int[] original = context.computeOnClient(client ->
				new int[] { client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight() });
		context.getInput().resizeWindow(320, 240);
		try {
			context.waitTicks(5);
			openManagerOn(context, dir, key);

			String problem = context.computeOnClient(client -> {
				Screen screen = client.gui.screen();
				Button scrollLeft = button(screen, "voxelcam.burst.scrollleft");
				Button scrollRight = button(screen, "voxelcam.burst.scrollright");
				Button setAsKey = button(screen, "voxelcam.burst.setaskey");
				Button export = button(screen, "voxelcam.burst.exportanimation");
				if (!scrollLeft.visible || !scrollRight.visible || !setAsKey.visible || !export.visible) {
					return "a selected burst should show its filmstrip buttons";
				}
				// A 34-wide cell with the 6 the row keeps either side of it.
				int room = scrollRight.getX() - scrollLeft.getRight();
				if (room < 6 + 34 + 6) {
					return "the scroll buttons leave " + room + " for cells, which is less than one";
				}
				if (scrollRight.getRight() > setAsKey.getX() || scrollRight.getRight() > export.getX()) {
					return "the right scroll button runs under the action column";
				}
				if (setAsKey.getRight() > screen.width || export.getRight() > screen.width) {
					return "the action column runs off a " + screen.width + " wide screen";
				}
				return null;
			});
			if (problem != null) {
				throw new AssertionError("filmstrip at 320x240: " + problem);
			}
			// For a human: the one cell, clear of the buttons either side of it.
			context.takeScreenshot("manager-filmstrip-320x240");
		} finally {
			context.getInput().resizeWindow(original[0], original[1]);
			context.waitTicks(5);
		}
	}

	/** The screenshots folder, counting how often it is listed. {@code File} is not final, and
	 * a directory read is the one thing here that cannot be seen from outside any other way. */
	private static final class CountingDir extends File {

		final AtomicInteger listings = new AtomicInteger();

		CountingDir(File of) {
			super(of.getPath());
		}

		@Override
		public File[] listFiles() {
			listings.incrementAndGet();
			return super.listFiles();
		}

		@Override
		public String[] list() {
			listings.incrementAndGet();
			return super.list();
		}
	}

	/**
	 * Drawing the strip used to list the whole screenshots folder, match every name against
	 * the burst pattern and sort the result, on every extracted frame, for as long as a burst
	 * stayed selected. With nothing changing, no frame should list it at all.
	 *
	 * <p>The second half is what caching the cells could break. Set as key swaps two files'
	 * bytes, tags included, so the strip's capture order puts different paths in its cells
	 * afterwards: the first cell becomes the frame that was the key. Picking that cell arms Set
	 * as key only if the strip was worked out again, and pressing it puts the fixture back.
	 */
	private static void assertTheFolderIsNotListedAgainForEveryFrame(ClientGameTestContext context, File dir, File key) {
		CountingDir counted = new CountingDir(dir);
		openManagerOn(context, counted, key);

		boolean hasFilmstrip = context.computeOnClient(client ->
				key.equals(VoxelCamIO.getSelectedPhoto()) && VoxelCamIO.burstFrameCount(key) > 0);
		if (!hasFilmstrip) {
			throw new AssertionError("the manager should be showing the fixture burst's filmstrip");
		}

		int before = counted.listings.get();
		context.waitTicks(20);
		int during = counted.listings.get() - before;
		if (during != 0) {
			throw new AssertionError("the screenshots folder was listed " + during
					+ " times in 20 ticks with a burst selected and nothing changing");
		}

		String problem = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			Button setAsKey = button(screen, "voxelcam.burst.setaskey");
			Button scrollLeft = button(screen, "voxelcam.burst.scrollleft");
			double firstCellX = scrollLeft.getRight() + 6 + 17;
			double secondCellX = firstCellX + 34 + 3;
			double cellY = scrollLeft.getY() + scrollLeft.getHeight() / 2.0;

			screen.mouseClicked(new MouseButtonEvent(secondCellX, cellY, LEFT_CLICK), false);
			if (!setAsKey.active) {
				return "picking a frame that is not the key should enable Set as key";
			}
			setAsKey.onPress(LEFT_CLICK);
			if (BurstFrame.fromTags(PngTextChunk.read(key)).index() != 1) {
				return "Set as key should have put the second frame under the key's path";
			}

			screen.mouseClicked(new MouseButtonEvent(firstCellX, cellY, LEFT_CLICK), false);
			if (!setAsKey.active) {
				return "after Set as key the first cell should be the frame that was the key, and pickable; "
						+ "the strip is still in its old order";
			}
			setAsKey.onPress(LEFT_CLICK);
			if (BurstFrame.fromTags(PngTextChunk.read(key)).index() != 0) {
				return "setting the first frame as key again should have restored the burst";
			}
			return null;
		});
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}

	/**
	 * An export fixes the frame order from every file's tags and reads the pixels afterwards,
	 * so a Set as key that swaps two files' bytes in between writes frames out of order.
	 *
	 * <p>All in one task on the client thread, on purpose. A fixture burst exports in
	 * milliseconds, and the export reports back through {@code Minecraft.execute}: nothing
	 * queued that way can run until this task returns, so the export is certainly still
	 * "running" as far as the manager knows for every check below, however fast the disk is.
	 */
	private static void assertSetAsKeyIsHeldOffWhileAnExportRuns(ClientGameTestContext context, File dir, File key) {
		openManagerOn(context, dir, key);

		String problem = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			Button setAsKey = button(screen, "voxelcam.burst.setaskey");
			Button export = button(screen, "voxelcam.burst.exportanimation");

			pickTheSecondCell(screen);
			if (!setAsKey.active) {
				return "picking a frame that is not the key should enable Set as key";
			}

			export.onPress(LEFT_CLICK);
			if (setAsKey.active) {
				return "Set as key should be disabled while an export is running";
			}

			// onPress skips the active check, as a click that was already on its way would.
			setAsKey.onPress(LEFT_CLICK);
			int keyIndex = BurstFrame.fromTags(PngTextChunk.read(key)).index();
			if (keyIndex != 0) {
				return "a Set as key that got through mid-export should do nothing, but the key's path now holds frame "
						+ keyIndex;
			}

			// Closed and opened again with the export still running. Every open builds a new
			// manager, and it must not come up offering what the first one was holding off.
			screen.onClose();
			GuiScreenShotManager reopened = new GuiScreenShotManager(dir);
			client.setScreenAndShow(reopened);
			pickTheSecondCell(reopened);
			if (button(reopened, "voxelcam.burst.exportanimation").active) {
				return "a manager reopened mid-export should not offer Export again";
			}
			if (button(reopened, "voxelcam.burst.setaskey").active) {
				return "a manager reopened mid-export should not offer Set as key";
			}
			return null;
		});
		if (problem != null) {
			throw new AssertionError(problem);
		}

		// The export selects the animation it wrote, which is how its end is seen from here.
		// The manager that started it is gone by now, so this is also the export reporting to
		// whichever manager is open.
		context.waitFor(client -> !key.equals(VoxelCamIO.getSelectedPhoto()), 400);

		// And the hold has to end with the export, though the manager that began it never
		// heard: the same pick on a fresh manager arms Set as key again. This is also what
		// shows the pick above landed on a cell, so the disabled button there meant something.
		openManagerOn(context, dir, key);
		String afterwards = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			pickTheSecondCell(screen);
			if (!button(screen, "voxelcam.burst.exportanimation").active) {
				return "Export should be offered again once the export has finished";
			}
			if (!button(screen, "voxelcam.burst.setaskey").active) {
				return "Set as key should be offered again once the export has finished";
			}
			return null;
		});
		if (afterwards != null) {
			throw new AssertionError(afterwards);
		}
	}

	/** The second cell: a gap after the left scroll button, one cell and its gap along, then
	 * half a cell in. Its frame is not the key, which is what arms Set as key. */
	private static void pickTheSecondCell(Screen screen) {
		Button scrollLeft = button(screen, "voxelcam.burst.scrollleft");
		double cellX = scrollLeft.getRight() + 6 + (34 + 3) + 17;
		double cellY = scrollLeft.getY() + scrollLeft.getHeight() / 2.0;
		screen.mouseClicked(new MouseButtonEvent(cellX, cellY, LEFT_CLICK), false);
	}

	private static void openManagerOn(ClientGameTestContext context, File dir, File key) {
		context.runOnClient(client -> VoxelCamIO.selectPhoto(key));
		context.setScreen(() -> new GuiScreenShotManager(dir));
		context.waitForScreen(GuiScreenShotManager.class);
		context.waitTicks(20);
	}

	/** By translation key rather than by label, so the pirate translation finds it too. */
	private static Button button(Screen screen, String translationKey) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof Button button
					&& button.getMessage().getContents() instanceof TranslatableContents contents
					&& contents.getKey().equals(translationKey)) {
				return button;
			}
		}
		throw new AssertionError("the manager should have a " + translationKey + " button");
	}

	/** A key frame and two more, tagged the way a capture tags them, all the same size. */
	private static void writeBurst(File dir, File key) {
		try {
			ScreenshotFixtures.emptyFolder(dir);
			writeFrame(key, 0);
			writeFrame(new File(dir, "burst_burst01.png"), 1);
			writeFrame(new File(dir, "burst_burst02.png"), 2);
		} catch (IOException e) {
			throw new AssertionError("could not write the burst fixture", e);
		}
	}

	/** A real, decodable PNG, with a band that moves so the frames differ. Its own writer and
	 * not {@code ScreenshotFixtures.writePng}, which would make three frames of one picture. */
	private static void writeFrame(File file, int index) throws IOException {
		int width = 160;
		int height = 90;
		java.awt.image.BufferedImage image =
				new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				boolean band = x / 20 == index;
				image.setRGB(x, y, band ? 0xFFFFFF : (x * 255 / width) << 16 | (y * 255 / height) << 8 | 0x80);
			}
		}
		javax.imageio.ImageIO.write(image, "png", file);
		PngTextChunk.embed(file, new BurstFrame("filmstrip-fixture", index, index * 100).toTags());
	}
}
