package com.thatapplefreak.voxelcam.gametest;

import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshotSize;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Drives {@link CaptureMenu} the way the physical key would, through its own public methods
 * directly rather than simulated GLFW press/hold/release — no such input-simulation
 * infrastructure exists in this repo (see {@code CaptureTest}, which takes the same approach for
 * {@link BigScreenshot}). Once {@code onKeyDown()} arms it, {@code VoxelCamClient}'s own end-tick
 * hook keeps calling {@code tick()} forward every real client tick exactly as it would for an
 * actual hold, so only the key transitions themselves are simulated here.
 */
public class CaptureMenuTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);

			assertATapTakesAPlainScreenshot(context, dir);
			assertAHeldAndAimedReleaseTakesTheAimedMode(context, dir);
			assertOpeningAScreenWhileHeldAbortsWithoutCapturing(context, dir);
		}
	}

	/** Released well under the hold threshold: a tap, not a hold, same as today's instant capture. */
	private static void assertATapTakesAPlainScreenshot(ClientGameTestContext context, File dir) {
		Set<String> before = listing(dir);

		context.runOnClient(client -> CaptureMenu.onKeyDown());
		context.runOnClient(client -> CaptureMenu.onKeyUp());
		// PNG encoding runs on the IO worker, so the file appears a little after the frame.
		context.waitTicks(40);

		theNewFile(dir, before, "a tap");
	}

	/**
	 * The aim is set and the key released inside a single client-thread task, atomically: the HUD
	 * render callback also calls {@code CaptureMenu.setAimOffset} every frame from the real cursor
	 * position, and a frame boundary between setting the aim and releasing would let it be
	 * overwritten before {@code onKeyUp()} ever reads it.
	 */
	private static void assertAHeldAndAimedReleaseTakesTheAimedMode(ClientGameTestContext context, File dir) {
		Dimensions window = windowSize(context);
		Set<String> before = listing(dir);

		context.runOnClient(client -> {
			BigScreenshot.setSize(BigScreenshotSize.parse("2x"));
			CaptureMenu.onKeyDown();
		});
		context.waitFor(client -> CaptureMenu.isOpen(), 200);

		context.runOnClient(client -> {
			// Straight down from the anchor: unambiguously the second wedge (BIG_SCREENSHOT),
			// far from wedge 0's dead-centre default.
			CaptureMenu.setAimOffset(0, 1000);
			CaptureMenu.onKeyUp();
		});

		// Two frames to resize and read back, then the IO worker to encode.
		context.waitFor(client -> !BigScreenshot.isBusy(), 200);
		context.waitTicks(40);

		File written = theNewFile(dir, before, "a held, aimed release");
		Dimensions size = pngSize(written);
		Dimensions expected = new Dimensions(window.width() * 2, window.height() * 2);
		if (!size.equals(expected)) {
			throw new AssertionError("aiming at the big-screenshot wedge should have produced a "
					+ expected + " capture, was " + size);
		}
	}

	/**
	 * A screen can appear while the menu is open without the key itself ever going up (Escape,
	 * disconnect, inventory…); {@code VoxelCamClient}'s end-tick guard is what has to catch that.
	 */
	private static void assertOpeningAScreenWhileHeldAbortsWithoutCapturing(
			ClientGameTestContext context, File dir) {
		Set<String> before = listing(dir);

		context.runOnClient(client -> CaptureMenu.onKeyDown());
		context.waitFor(client -> CaptureMenu.isOpen(), 200);

		context.runOnClient(client -> client.gui.setScreen(new PauseScreen(false)));
		context.waitTicks(5);

		if (CaptureMenu.isArmed()) {
			throw new AssertionError("a screen opening while the menu is open should abort it");
		}
		Set<String> after = listing(dir);
		after.removeAll(before);
		after.removeIf(name -> !name.endsWith(".png"));
		if (!after.isEmpty()) {
			throw new AssertionError("an aborted hold should not have written a capture, wrote " + after);
		}

		context.setScreen(() -> null);
		context.waitForScreen(null);
	}

	private record Dimensions(int width, int height) {
		@Override
		public String toString() {
			return width + "x" + height;
		}
	}

	private static Dimensions windowSize(ClientGameTestContext context) {
		return context.computeOnClient((Minecraft client) ->
				new Dimensions(client.getWindow().getWidth(), client.getWindow().getHeight()));
	}

	private static Set<String> listing(File dir) {
		String[] names = dir.list();
		return names == null ? new HashSet<>() : new HashSet<>(Arrays.asList(names));
	}

	private static File theNewFile(File dir, Set<String> before, String what) {
		Set<String> after = listing(dir);
		after.removeAll(before);
		after.removeIf(name -> !name.endsWith(".png"));
		if (after.size() != 1) {
			throw new AssertionError(what + " should have written exactly one png, wrote " + after);
		}
		return new File(dir, after.iterator().next());
	}

	/** Straight out of the PNG header: signature, chunk length, "IHDR", width, height. */
	private static Dimensions pngSize(File file) {
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			in.skipNBytes(16);
			return new Dimensions(in.readInt(), in.readInt());
		} catch (IOException e) {
			throw new AssertionError("could not read " + file.getName(), e);
		}
	}
}
