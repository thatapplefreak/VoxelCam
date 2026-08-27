package com.thatapplefreak.voxelcam.gametest;

import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshotSize;
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
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Capture has to run in a world: both the ordinary path and the oversized one are
 * gated on there being a level and no open screen, which is the modern equivalent
 * of the old ScreenshotIncapable check.
 *
 * The oversized path is the part most worth testing. It resizes the main render
 * target mid-frame and reads it back a frame later, and every version bump has
 * moved something underneath it — in 26.2 the frame method split in two, the present
 * became a swapchain blit, and framebufferSizeChanged stopped resizing the target.
 */
public class CaptureTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);

			assertOrdinaryCaptureWritesAFile(context, dir);
			assertOversizedCaptureMatchesTheRequest(context, dir);
			assertWindowIsRestored(context);
		}
	}

	private static void assertOrdinaryCaptureWritesAFile(ClientGameTestContext context, File dir) {
		Set<String> before = listing(dir);

		context.runOnClient(client ->
				Screenshot.grab(client.gameDirectory, client.gameRenderer.mainRenderTarget(), message -> { }));
		// PNG encoding runs on the IO worker, so the file appears a little after the frame.
		context.waitTicks(40);

		File written = theNewFile(dir, before, "an ordinary capture");
		Dimensions size = pngSize(written);
		Dimensions window = windowSize(context);
		if (!size.equals(window)) {
			throw new AssertionError("ordinary capture should match the window " + window + ", was " + size);
		}
	}

	/**
	 * 2x is the default, so the saved image should be exactly twice the window on
	 * each axis — unless the GPU clamp kicked in, which on a small dev window it
	 * will not.
	 */
	private static void assertOversizedCaptureMatchesTheRequest(ClientGameTestContext context, File dir) {
		Dimensions window = windowSize(context);
		Set<String> before = listing(dir);

		context.runOnClient(client -> {
			BigScreenshot.setSize(BigScreenshotSize.parse("2x"));
			BigScreenshot.request();
		});

		// Two frames to resize and read back, then the IO worker to encode.
		context.waitFor(client -> !BigScreenshot.isBusy(), 200);
		context.waitTicks(40);

		File written = theNewFile(dir, before, "an oversized capture");
		Dimensions size = pngSize(written);
		Dimensions expected = new Dimensions(window.width() * 2, window.height() * 2);
		if (!size.equals(expected)) {
			throw new AssertionError("2x capture of a " + window + " window should be "
					+ expected + ", was " + size);
		}
	}

	/**
	 * The restore runs inside the readback consumer, and a bug there leaves the
	 * window permanently oversized — the failure the state machine's saved-size
	 * snapshot exists to prevent.
	 */
	private static void assertWindowIsRestored(ClientGameTestContext context) {
		context.waitTicks(20);
		Dimensions now = windowSize(context);
		Dimensions gui = context.computeOnClient(client -> new Dimensions(
				client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight()));

		if (now.width() > gui.width() * 2 || now.height() > gui.height() * 2) {
			throw new AssertionError("window was left oversized at " + now);
		}
		if (BigScreenshot.isBusy()) {
			throw new AssertionError("capture never returned to idle");
		}
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
