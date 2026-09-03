package com.thatapplefreak.voxelcam.gametest;

import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.gui.RenamePopup;
import com.thatapplefreak.voxelcam.client.screenshot.SortMode;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Where a rename leaves the manager's highlight. The resolution itself is unit tested;
 * only a real client can drive the chain it exists for — Ok closes the popup with
 * {@code setScreenAndShow(parent)}, which re-inits the already-initialized manager
 * through {@code repositionElements()} and rebuilds every widget from scratch.
 *
 * Sorted by name and renaming the first entry to a name that sorts last, because
 * {@code renameTo} leaves the modification time alone: under the default newest-first
 * sort the renamed file stays at the head of the list, so the bug this guards against
 * would pass unnoticed.
 */
public class RenameSelectionTest implements FabricClientGameTest {

	private static final String RENAMED_TO = "zz-renamed";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		// Its own directory: this test renames a fixture and the sibling manager test
		// asserts an exact file count in the shared one.
		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, "voxelcam-rename-selection-shots"));
		writeFixtures(dir);

		context.runOnClient(client -> SortMode.setCurrent(SortMode.NAME_A_TO_Z));

		GuiScreenShotManager manager = context.computeOnClient(client -> new GuiScreenShotManager(dir));
		context.setScreen(() -> manager);
		context.waitForScreen(GuiScreenShotManager.class);
		context.waitTicks(20);

		// Without this the rest proves nothing: the whole point is that the file being
		// renamed is the one init() picked, and that it stops being the head of the list.
		assertSelected(context, "a-first.png", "the manager should open on the first file by name");

		context.clickScreenButton("voxelcam.rename");
		context.waitForScreen(RenamePopup.class);
		context.waitTicks(5);

		typeOverTheName(context);

		context.clickScreenButton("voxelcam.ok");
		context.waitForScreen(GuiScreenShotManager.class);
		context.waitTicks(20);

		assertTheFileMoved(context, dir);
		// The manager pushes whatever its rebuild resolved back down through the list
		// widget, so this reads the selection init() chose: with the rename ignored it
		// would be b-second.png, the new head of the list.
		assertSelected(context, RENAMED_TO + ".png", "selection did not follow the rename across the rebuild");

		// Renders the details line and the highlighted row for the renamed file.
		context.takeScreenshot("manager-selection-after-rename");

		context.runOnClient(client -> {
			SortMode.setCurrent(SortMode.DATE_NEWEST);
			VoxelCamIO.selectPhoto(null);
		});
		context.setScreen(TitleScreen::new);
		context.waitForScreen(TitleScreen.class);
	}

	/**
	 * The value is set rather than typed: whether {@code setValue} leaves a selection for
	 * the first keystroke to replace decides what typing produces, and a name that ends up
	 * as the original with a suffix would still sort first and quietly make this vacuous.
	 * That the field can be typed into at all is {@code RenamePopupFocusTest}'s business.
	 */
	private static void typeOverTheName(ClientGameTestContext context) {
		context.runOnClient(client -> {
			for (GuiEventListener child : client.gui.screen().children()) {
				if (child instanceof EditBox box) {
					box.setValue(RENAMED_TO);
					return;
				}
			}
			throw new AssertionError("the rename popup should have a name field");
		});
		context.waitTicks(5);

		String value = context.computeOnClient(client -> {
			for (GuiEventListener child : client.gui.screen().children()) {
				if (child instanceof EditBox box) {
					return box.getValue();
				}
			}
			return null;
		});
		if (!RENAMED_TO.equals(value)) {
			throw new AssertionError("the name field should hold " + RENAMED_TO + ", it held " + value);
		}
	}

	private static void assertTheFileMoved(ClientGameTestContext context, File dir) {
		boolean moved = context.computeOnClient(client ->
				new File(dir, RENAMED_TO + ".png").exists() && !new File(dir, "a-first.png").exists());
		if (!moved) {
			throw new AssertionError("Ok should have renamed the file on disk");
		}
	}

	private static void assertSelected(ClientGameTestContext context, String name, String message) {
		String actual = context.computeOnClient(client -> {
			File file = VoxelCamIO.getSelectedPhoto();
			return file == null ? "nothing" : file.getName();
		});
		if (!name.equals(actual)) {
			throw new AssertionError(message + " — selected " + actual);
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
			writePng(new File(dir, "a-first.png"), 320, 180);
			writePng(new File(dir, "b-second.png"), 200, 200);
			writePng(new File(dir, "c-third.png"), 480, 120);
		} catch (IOException e) {
			throw new AssertionError("could not write rename fixtures", e);
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
