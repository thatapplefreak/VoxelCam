package com.thatapplefreak.voxelcam.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.screenshot.AnimatedPng;
import com.thatapplefreak.voxelcam.client.screenshot.AnimationPreview;
import com.thatapplefreak.voxelcam.client.screenshot.Burst;
import com.thatapplefreak.voxelcam.client.screenshot.BurstFrame;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
import com.thatapplefreak.voxelcam.client.screenshot.PngTextChunk;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Burst capture through the real F2 key and the radial menu's third wedge, the same
 * input-simulation approach {@code CaptureMenuTest} uses. What is specific to a burst rather than
 * already covered there: every frame lands at window size, never oversized — the memory-ceiling
 * guard issue #21 calls out, and the kind of thing that only shows up by measuring the file
 * rather than trusting the code path taken — the key and its frames share one group id in their
 * embedded tags, and a screen opening mid-burst stops it early rather than hanging.
 */
public class BurstCaptureTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);

			File key = assertAHeldAndAimedReleaseFiresABurstAtWindowSize(context, dir);
			assertTheManagerShowsTheFilmstripWithoutDimmingItsButtons(context, dir, key);
			assertExportAnimationWritesOneApngOfTheWholeBurst(context, dir, key);
			assertAScreenOpeningMidBurstStopsItEarly(context, dir);
		}
	}

	/**
	 * Aimed at the third wedge's centre — the same non-boundary vector {@code CaptureMenuTest}
	 * uses for the second, mirrored to the other side — and released.
	 */
	private static File assertAHeldAndAimedReleaseFiresABurstAtWindowSize(ClientGameTestContext context, File dir) {
		Dimensions window = windowSize(context);
		Set<String> before = listing(dir);
		int length = context.computeOnClient(client -> Burst.getLength());

		context.getInput().holdKey(InputConstants.KEY_F2);
		try {
			context.waitFor(client -> CaptureMenu.isOpen(), 200);
			context.getInput().moveCursor(-130, 75);
			context.waitFor(client -> CaptureMenu.aimedMode() == CaptureMenu.Mode.BURST, 100);
		} finally {
			context.getInput().releaseKey(InputConstants.KEY_F2);
		}

		context.waitFor(client -> !CaptureMenu.isCapturePending() && !Burst.isBusy(), 400);
		// PNG encoding runs on the IO worker, and a burst has several frames queued on it.
		context.waitTicks(60);

		Set<String> written = theNewFiles(dir, before, length, "a held, aimed release at the burst wedge");
		File key = theKeyAmong(written, dir);

		for (String name : written) {
			Dimensions size = pngSize(new File(dir, name));
			if (!size.equals(window)) {
				throw new AssertionError("every burst frame should be window-sized " + window
						+ " (never oversized), " + name + " was " + size);
			}
		}

		assertTagsAgree(dir, key, written);
		return key;
	}

	/**
	 * {@code voxelcam:burst}/{@code voxelcam:burstindex} are the authoritative record of a
	 * frame's place in the group — the filename tag is only the zero-I/O predicate the manager's
	 * listing needs. The key carries index 0; every other frame shares its group id and carries
	 * a positive offset from it.
	 */
	private static void assertTagsAgree(File dir, File key, Set<String> written) {
		BurstFrame keyFrame = BurstFrame.fromTags(PngTextChunk.read(key));
		if (keyFrame == null || keyFrame.index() != 0) {
			throw new AssertionError("the key should carry burst index 0, had " + keyFrame);
		}
		for (String name : written) {
			File file = new File(dir, name);
			if (file.equals(key)) {
				continue;
			}
			BurstFrame frame = BurstFrame.fromTags(PngTextChunk.read(file));
			if (frame == null || !frame.groupId().equals(keyFrame.groupId())) {
				throw new AssertionError(name + " should share the key's group id, had " + frame);
			}
			if (frame.offsetMillis() <= 0) {
				throw new AssertionError(name + " should be offset after the key, was " + frame.offsetMillis() + "ms");
			}
		}
	}

	/**
	 * Live-playtest finding: the preview's dark background panel used to render after the
	 * widgets, painting over anything inside its bounds — including the filmstrip's Set as key
	 * and scroll buttons — so a fully active button read identically dimmed to a disabled one.
	 * Opens the manager on this burst's own directory with its key selected and screenshots it
	 * for visual confirmation.
	 */
	private static void assertTheManagerShowsTheFilmstripWithoutDimmingItsButtons(
			ClientGameTestContext context, File dir, File key) {
		openManagerOn(context, dir, key);
		context.waitForScreen(GuiScreenShotManager.class);
		// Thumbnails decode off-thread and upload on the render thread.
		context.waitTicks(20);

		boolean hasFilmstrip = context.computeOnClient(client -> {
			File selected = VoxelCamIO.getSelectedPhoto();
			return selected != null && VoxelCamIO.burstFrameCount(selected) > 0;
		});
		if (!hasFilmstrip) {
			String state = context.computeOnClient(client -> "selected " + VoxelCamIO.getSelectedPhoto()
					+ ", listed " + VoxelCamIO.getScreenShotFiles().stream().map(File::getName).toList());
			throw new AssertionError("the manager's fallback selection should be this burst's own key; " + state);
		}

		context.takeScreenshot("manager-with-burst-filmstrip");

		context.setScreen(() -> null);
		context.waitForScreen(null);
	}

	/**
	 * The export button, pressed by its fixed translation key the way a player's click finds it,
	 * on the burst this test captured for real. What only a real burst proves and {@code
	 * AnimatedPngTest} cannot: that stb's own PNGs reframe — its IHDR, its IDAT split — and that
	 * the count in {@code acTL} is the group's whole size as the manager sees it. Then the manager
	 * should have moved onto the new file, which says it is an animation.
	 */
	private static void assertExportAnimationWritesOneApngOfTheWholeBurst(ClientGameTestContext context, File dir,
			File key) {
		openManagerOn(context, dir, key);
		context.waitTicks(5);

		int groupSize = context.computeOnClient(client -> VoxelCamIO.burstFrameCount(key)) + 1;
		Set<String> before = listing(dir);

		context.clickScreenButton("voxelcam.burst.exportanimation");
		context.waitFor(client -> !key.equals(VoxelCamIO.getSelectedPhoto()), 400);

		Set<String> after = listing(dir);
		after.removeAll(before);
		List<String> pngs = after.stream().filter(name -> name.endsWith(".png")).toList();
		if (pngs.size() != 1 || after.size() != 2) {
			throw new AssertionError("an export should write one APNG and its WebP copy, wrote " + after);
		}
		File animation = new File(dir, pngs.get(0));
		File webp = AnimatedPng.webpBeside(animation);
		if (!after.contains(webp.getName())) {
			throw new AssertionError("the WebP copy should sit beside " + animation.getName() + ", found " + after);
		}
		if (!animation.equals(context.computeOnClient(client -> VoxelCamIO.getSelectedPhoto()))) {
			throw new AssertionError("the manager should select the animation it just wrote, " + animation.getName());
		}

		byte[] bytes;
		try {
			bytes = Files.readAllBytes(animation.toPath());
		} catch (IOException e) {
			throw new AssertionError("could not read " + animation.getName(), e);
		}
		int frames = actlFrameCount(bytes);
		if (frames != groupSize) {
			throw new AssertionError("acTL should count all " + groupSize + " frames of the burst, said " + frames);
		}
		if (AnimatedPng.frameCount(PngTextChunk.read(animation)) != groupSize) {
			throw new AssertionError("the animation should carry its frame count as a tag");
		}
		if (!pngSize(animation).equals(pngSize(key))) {
			throw new AssertionError("the animation should be the burst's own size, was " + pngSize(animation));
		}
		// The copy Share hands out holds the same frames, encoded by the game's own decoder's pixels.
		int webpFrames = anmfCount(webp);
		if (webpFrames != groupSize) {
			throw new AssertionError("the WebP copy should hold all " + groupSize + " frames, held " + webpFrames);
		}

		// A decoder that knows nothing of APNG must still open the file as its first frame.
		try {
			if (javax.imageio.ImageIO.read(animation) == null) {
				throw new AssertionError("the JDK's PNG reader could not open " + animation.getName());
			}
		} catch (IOException e) {
			throw new AssertionError("the JDK's PNG reader rejected " + animation.getName(), e);
		}

		// The preview plays it: every frame decoded, and more than one of them shown over time.
		context.waitFor(client -> AnimationPreview.playingFrameCount(animation) == groupSize, 200);
		Set<Object> shown = new HashSet<>();
		for (int i = 0; i < 10; i++) {
			shown.add(context.computeOnClient(client ->
					AnimationPreview.frameAt(animation, net.minecraft.util.Util.getMillis()).id()));
			context.waitTicks(1);
		}
		if (shown.size() < 2) {
			throw new AssertionError("the preview should cycle through the animation's frames, showed " + shown);
		}

		context.takeScreenshot("manager-with-exported-animation");
		context.setScreen(() -> null);
		context.waitForScreen(null);
	}

	/**
	 * Selected by hand rather than left to the newest-first sort: under WSL, files written minutes
	 * earlier have been seen carrying modification times later than the burst's, which put an
	 * older screenshot first and selected that instead. The manager keeps a selection it can find
	 * in the listing, so this is the burst's own key whatever the clock says.
	 */
	private static void openManagerOn(ClientGameTestContext context, File dir, File key) {
		context.runOnClient(client -> VoxelCamIO.selectPhoto(key));
		context.setScreen(() -> new GuiScreenShotManager(dir));
		context.waitForScreen(GuiScreenShotManager.class);
	}

	/** How many ANMF chunks a WebP's RIFF holds: one per frame. */
	private static int anmfCount(File webp) {
		byte[] data;
		try {
			data = Files.readAllBytes(webp.toPath());
		} catch (IOException e) {
			throw new AssertionError("could not read " + webp.getName(), e);
		}
		String riff = new String(data, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)
				+ new String(data, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
		if (!riff.equals("RIFFWEBP")) {
			throw new AssertionError(webp.getName() + " is not a WebP: " + riff);
		}
		int count = 0;
		for (int pos = 12; pos + 8 <= data.length; ) {
			int size = (data[pos + 4] & 0xFF) | ((data[pos + 5] & 0xFF) << 8) | ((data[pos + 6] & 0xFF) << 16)
					| ((data[pos + 7] & 0xFF) << 24);
			if (new String(data, pos, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ANMF")) {
				count++;
			}
			pos += 8 + ((size + 1) & ~1);
		}
		return count;
	}

	/** {@code acTL}'s {@code num_frames}, found by walking the chunks from the signature. */
	private static int actlFrameCount(byte[] png) {
		int pos = 8;
		while (pos + 12 <= png.length) {
			int length = readInt(png, pos);
			String type = new String(png, pos + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
			if (type.equals("acTL")) {
				return readInt(png, pos + 8);
			}
			if (type.equals("IDAT")) {
				break; // acTL must come before the first IDAT; after it, it does not count.
			}
			pos += 12 + length;
		}
		return -1;
	}

	private static int readInt(byte[] d, int at) {
		return ((d[at] & 0xFF) << 24) | ((d[at + 1] & 0xFF) << 16) | ((d[at + 2] & 0xFF) << 8) | (d[at + 3] & 0xFF);
	}

	/**
	 * A screen can appear at any point across the roughly a second a burst spans, not just
	 * between the request and the very next frame the way {@code BigScreenshot} has to guard
	 * against — {@code Burst.beforeBlit()} re-checks the gate on every frame it issues rather
	 * than only once at the start.
	 */
	private static void assertAScreenOpeningMidBurstStopsItEarly(ClientGameTestContext context, File dir) {
		Set<String> before = listing(dir);

		context.getInput().holdKey(InputConstants.KEY_F2);
		try {
			context.waitFor(client -> CaptureMenu.isOpen(), 200);
			context.getInput().moveCursor(-130, 75);
			context.waitFor(client -> CaptureMenu.aimedMode() == CaptureMenu.Mode.BURST, 100);
		} finally {
			context.getInput().releaseKey(InputConstants.KEY_F2);
		}

		context.waitFor(client -> Burst.isBusy(), 200);
		context.runOnClient(client -> client.gui.setScreen(new PauseScreen(false)));
		context.waitTicks(20);

		if (Burst.isBusy()) {
			throw new AssertionError("a burst overtaken by a screen should have stopped, not hung");
		}

		int length = context.computeOnClient(client -> Burst.getLength());
		Set<String> after = listing(dir);
		after.removeAll(before);
		after.removeIf(name -> !name.endsWith(".png"));
		if (after.size() >= length) {
			throw new AssertionError("a burst stopped by a screen should have written fewer than "
					+ length + " frames, wrote " + after.size());
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

	private static Set<String> theNewFiles(File dir, Set<String> before, int expectedCount, String what) {
		Set<String> after = listing(dir);
		after.removeAll(before);
		after.removeIf(name -> !name.endsWith(".png"));
		if (after.size() != expectedCount) {
			throw new AssertionError(what + " should have written exactly " + expectedCount
					+ " frames, wrote " + after.size() + ": " + after);
		}
		return after;
	}

	/** The one name among a burst's files that carries no {@code _burstNN} tag — the key, named
	 * plainly by {@code ScreenshotNamer} the same way an ordinary capture is. */
	private static File theKeyAmong(Set<String> names, File dir) {
		List<String> keys = names.stream().filter(name -> !name.matches(".*_burst\\d+\\.png$")).toList();
		if (keys.size() != 1) {
			throw new AssertionError("exactly one of a burst's files should be the plainly-named key, found "
					+ keys + " among " + names);
		}
		return new File(dir, keys.get(0));
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
