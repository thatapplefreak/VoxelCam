package com.thatapplefreak.voxelcam.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshotSize;
import com.thatapplefreak.voxelcam.client.screenshot.Burst;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureStatus;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureStatusHud;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The capture status HUD's one non-negotiable property: it is on screen, and it is never in a
 * screenshot. HUD elements are extracted before a frame is drawn and every capture reads the frame
 * back after, so a notice that is merely "visible" while a capture runs is burnt into the PNG.
 *
 * <p>Detection is by colour rather than by text: a FAILED notice's accent bar is an opaque red that
 * nothing in a fresh test world's top-left corner comes close to. Every negative assertion here is
 * paired with a positive one — a read-back of the presented frame showing the bar — so a test that
 * passes because the probe is looking at the wrong pixels cannot pass quietly.
 */
public class CaptureStatusHudTest implements FabricClientGameTest {

	/** {@code CaptureStatusHud}'s FAILED accent, without alpha. */
	private static final int ACCENT_RGB = 0xE04848;
	private static final int TOLERANCE = 24;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);

			assertANoticeIsOnScreen(context);
			assertANoticeIsDrawnUnderF1(context);
			assertTheSwitchHidesIt(context);
			assertAMenuScreenshotDoesNotCarryIt(context, dir);
			assertNoBurstFrameCarriesIt(context, dir);
			assertABigScreenshotShowsTheSavingLine(context);
			assertABigScreenshotIsAnnouncedWithoutChat(context, dir);
		}
		context.runOnClient(client -> {
			CaptureStatus.forgetAll();
			VoxelCamConfig.forgetCurrent();
		});
	}

	private static void assertANoticeIsOnScreen(ClientGameTestContext context) {
		postFailure(context);
		if (!presentedFrameShowsAccent(context)) {
			throw new AssertionError("a posted notice should be drawn in the top-left corner");
		}
		// For a human: legibility against the sky is the one thing no assertion here touches.
		context.runOnClient(client -> CaptureStatus.post(CaptureStatus.Kind.SAVED,
				"voxelcam.savedscreenshotas", "2026-11-12_21.30.00.png"));
		context.waitTicks(2);
		context.takeScreenshot("capture-status-notices");
	}

	/** F1 is where players compose screenshots, and with chat gone this is the only feedback
	 * there is — which is why the HUD is drawn from a mixin rather than a Fabric HudElement. */
	private static void assertANoticeIsDrawnUnderF1(ClientGameTestContext context) {
		postFailure(context);
		context.runOnClient(client -> client.gui.hud.toggle());
		try {
			context.waitTicks(2);
			if (!context.computeOnClient(client -> client.gui.hud.isHidden())) {
				throw new AssertionError("toggling the HUD should have hidden it");
			}
			if (!presentedFrameShowsAccent(context)) {
				throw new AssertionError("the capture status HUD should still draw with the HUD hidden (F1)");
			}
		} finally {
			context.runOnClient(client -> client.gui.hud.toggle());
		}
	}

	private static void assertTheSwitchHidesIt(ClientGameTestContext context) {
		postFailure(context);
		context.runOnClient(client -> VoxelCamConfig.current().captureStatusHud = false);
		try {
			if (presentedFrameShowsAccent(context)) {
				throw new AssertionError("switching the capture status HUD off should hide it");
			}
		} finally {
			context.runOnClient(client -> VoxelCamConfig.current().captureStatusHud = true);
		}
	}

	private static void assertAMenuScreenshotDoesNotCarryIt(ClientGameTestContext context, File dir) {
		postFailure(context);
		if (!presentedFrameShowsAccent(context)) {
			throw new AssertionError("precondition: the notice should be on screen before the capture");
		}
		Set<String> before = listing(dir);

		// A tap: armed, then released before the hold threshold, which queues a plain screenshot
		// for the next clean frame — the same route F2 takes.
		context.runOnClient(client -> {
			CaptureMenu.onKeyDown();
			CaptureMenu.onKeyUp();
		});
		context.waitTicks(40);

		List<File> written = newFiles(dir, before);
		if (written.size() != 1) {
			throw new AssertionError("a tap should have written one screenshot, wrote " + written);
		}
		if (fileShowsAccent(context, written.get(0))) {
			throw new AssertionError(written.get(0).getName() + " has the capture status HUD burnt into it");
		}
	}

	/** Frame 0 is protected the way a plain shot is; frames 1 onward only by the running burst
	 * hiding the HUD for its whole length. */
	private static void assertNoBurstFrameCarriesIt(ClientGameTestContext context, File dir) {
		postFailure(context);
		Set<String> before = listing(dir);
		int previousLength = context.computeOnClient(client -> Burst.getLength());

		context.runOnClient(client -> {
			Burst.setLength(4);
			Burst.request();
		});
		try {
			context.waitFor(client -> !Burst.isBusy(), 200);
			context.waitTicks(40);
		} finally {
			context.runOnClient(client -> Burst.setLength(previousLength));
		}

		List<File> written = newFiles(dir, before);
		if (written.size() != 4) {
			throw new AssertionError("a 4-frame burst should have written 4 frames, wrote " + written);
		}
		for (File frame : written) {
			if (fileShowsAccent(context, frame)) {
				throw new AssertionError("burst frame " + frame.getName() + " has the capture status HUD burnt into it");
			}
		}
	}

	/**
	 * The busy line is derived from the save counter and the capture states rather than posted, so
	 * only a real capture shows it is wired to them. 4x rather than 2x, so the encode outlasts a
	 * tick or two of polling. The screenshot is for a human checking the spinner, and best-effort:
	 * the line can be gone by the frame Fabric grabs.
	 */
	private static void assertABigScreenshotShowsTheSavingLine(ClientGameTestContext context) {
		context.runOnClient(client -> CaptureStatus.forgetAll());
		BigScreenshotSize previous = context.computeOnClient(client -> BigScreenshot.getSize());
		context.runOnClient(client -> {
			BigScreenshot.setSize(BigScreenshotSize.parse("4x"));
			BigScreenshot.request();
		});
		try {
			context.waitFor(client -> CaptureStatus.isBusyShowing(), 100);
			// While the window is still oversized the GUI is laid out for it, so the moment worth a
			// look is the encode after the restore, if it lasts long enough to catch.
			for (int i = 0; i < 40; i++) {
				if (context.computeOnClient(client -> !BigScreenshot.isBusy() && CaptureStatus.isBusyShowing())) {
					context.takeScreenshot("capture-status-saving");
					break;
				}
				context.waitTicks(1);
			}
			context.waitFor(client -> !BigScreenshot.isBusy() && !CaptureStatus.isBusyShowing(), 400);
		} finally {
			context.runOnClient(client -> BigScreenshot.setSize(previous));
		}
		context.waitTicks(20);
	}

	/** The oversized frame is the one most worth protecting, and the "saved as" that follows it is
	 * the message a player used to read in chat. */
	private static void assertABigScreenshotIsAnnouncedWithoutChat(ClientGameTestContext context, File dir) {
		postFailure(context);
		Set<String> before = listing(dir);

		context.runOnClient(client -> {
			BigScreenshot.setSize(BigScreenshotSize.parse("2x"));
			BigScreenshot.request();
		});
		context.waitFor(client -> !BigScreenshot.isBusy(), 200);
		context.waitTicks(40);

		List<File> written = newFiles(dir, before);
		if (written.size() != 1) {
			throw new AssertionError("a big screenshot should have written one file, wrote " + written);
		}
		File file = written.get(0);
		int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		// Rendered at 2x, the GUI scale is recomputed for the oversized framebuffer, so where the
		// bar lands is not simply twice where it does on screen: scan the whole top-left band.
		if (imageShowsAccent(readPng(file), scale, true)) {
			throw new AssertionError(file.getName() + " has the capture status HUD burnt into it");
		}

		boolean announced = context.computeOnClient(client ->
				CaptureStatus.visibleAt(net.minecraft.util.Util.getMillis()).stream()
						.anyMatch(n -> n.kind() == CaptureStatus.Kind.SAVED
								&& n.key().equals("voxelcam.savedscreenshotas")
								&& n.args().contains(file.getName())));
		if (!announced) {
			throw new AssertionError("the big screenshot should have posted a saved-as notice naming " + file.getName());
		}
	}

	// --- helpers ------------------------------------------------------------------------------

	/** FAILED holds longest, comfortably outlasting any one capture below. */
	private static void postFailure(ClientGameTestContext context) {
		context.runOnClient(client -> {
			CaptureStatus.forgetAll();
			CaptureStatus.post(CaptureStatus.Kind.FAILED, "voxelcam.savefailed");
		});
		context.waitTicks(3);
	}

	/**
	 * Reads back whatever the main render target holds between frames — the last frame drawn,
	 * HUD included. This goes straight to vanilla's readback rather than {@code Screenshot.grab},
	 * which VoxelCam intercepts.
	 */
	private static boolean presentedFrameShowsAccent(ClientGameTestContext context) {
		context.waitTicks(2);
		AtomicReference<Boolean> found = new AtomicReference<>();
		context.runOnClient(client -> {
			int scale = client.getWindow().getGuiScale();
			Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
				try (image) {
					found.set(imageShowsAccent(image, scale, false));
				}
			});
		});
		context.waitFor(client -> found.get() != null, 40);
		return found.get();
	}

	private static boolean fileShowsAccent(ClientGameTestContext context, File file) {
		int scale = context.computeOnClient((Minecraft client) -> client.getWindow().getGuiScale());
		return imageShowsAccent(readPng(file), scale, false);
	}

	/**
	 * Counts accent-coloured pixels in the bar's column. Half the bar's area is required, so a
	 * stray red pixel in the world cannot pass as the HUD. {@code wide} scans a band large enough
	 * for any GUI scale instead of the one position the given scale puts it at.
	 */
	private static boolean imageShowsAccent(NativeImage image, int scale, boolean wide) {
		try (image) {
			int x0 = wide ? 0 : CaptureStatusHud.MARGIN * scale;
			int y0 = wide ? 0 : CaptureStatusHud.MARGIN * scale;
			int x1 = Math.min(image.getWidth(), wide ? 64 * scale : x0 + 2 * scale);
			int y1 = Math.min(image.getHeight(), wide ? 64 * scale : y0 + CaptureStatusHud.ROW_HEIGHT * scale);
			int matches = 0;
			for (int y = y0; y < y1; y++) {
				for (int x = x0; x < x1; x++) {
					if (isAccent(image.getPixel(x, y))) {
						matches++;
					}
				}
			}
			int barArea = 2 * scale * CaptureStatusHud.ROW_HEIGHT * scale;
			return matches >= barArea / 2;
		}
	}

	private static boolean isAccent(int argb) {
		return Math.abs(((argb >> 16) & 0xFF) - ((ACCENT_RGB >> 16) & 0xFF)) <= TOLERANCE
				&& Math.abs(((argb >> 8) & 0xFF) - ((ACCENT_RGB >> 8) & 0xFF)) <= TOLERANCE
				&& Math.abs((argb & 0xFF) - (ACCENT_RGB & 0xFF)) <= TOLERANCE;
	}

	private static NativeImage readPng(File file) {
		try (InputStream in = new FileInputStream(file)) {
			return NativeImage.read(in);
		} catch (IOException e) {
			throw new AssertionError("could not read " + file, e);
		}
	}

	private static Set<String> listing(File dir) {
		String[] names = dir.list();
		return names == null ? new HashSet<>() : new HashSet<>(Arrays.asList(names));
	}

	private static List<File> newFiles(File dir, Set<String> before) {
		Set<String> after = listing(dir);
		after.removeAll(before);
		return after.stream().filter(name -> name.endsWith(".png")).sorted().map(name -> new File(dir, name)).toList();
	}
}
