package com.thatapplefreak.voxelcam.gametest;

import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.screenshot.AutoCapture;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTag;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTrigger;
import com.thatapplefreak.voxelcam.client.screenshot.PngTextChunk;
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
 * The automatic capture path end to end: {@code AutoCapture.fire} through the blit, the write on
 * the IO pool, and the tags embedded afterwards. The unit suite already proves what the cooldown
 * and the gate <em>decide</em>; what only a real client can show is that a decision to capture
 * actually produces a plain-size, correctly tagged PNG — and, just as importantly, that the three
 * decisions not to capture produce no file at all.
 *
 * <p>Triggers are fired directly rather than through their hooks. Earning an advancement, killing a
 * boss and dying are all server-side events a client game test cannot stage, and staging them would
 * be testing Minecraft rather than VoxelCam; the mixins that turn those into a {@code fire} call
 * are covered by launching the client at all, since {@code defaultRequire: 1} fails a mis-targeted
 * injector at load.
 */
public class MomentCaptureTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);

			assertAnArmedMomentLandsTaggedAtWindowSize(context, dir);
			// Deliberately not reset in between: this one depends on the window the previous test
			// charged, which is the whole point of it.
			assertASecondMomentInsideTheWindowIsDropped(context, dir);
			assertADisabledTriggerCapturesNothing(context, dir);
			assertAScreenThePlayerOpenedDropsThePendingCapture(context, dir);
		}
	}

	private static void assertAnArmedMomentLandsTaggedAtWindowSize(ClientGameTestContext context, File dir) {
		resetAutoCapture(context);
		Dimensions window = windowSize(context);
		Set<String> before = listing(dir);

		fire(context, MomentTrigger.ADVANCEMENT, "Stone Age");

		File written = theOnlyNewFile(dir, before, "an armed advancement moment");
		Dimensions size = pngSize(written);
		if (!size.equals(window)) {
			throw new AssertionError("an automatic capture should be window-sized " + window
					+ " like any plain screenshot, was " + size);
		}

		// The tag is what lets the manager say why this file exists at all — nobody pressed a key
		// for it, so without this it is an unexplained screenshot.
		MomentTag tag = MomentTag.fromTags(PngTextChunk.read(written));
		if (tag == null) {
			throw new AssertionError(written.getName() + " should carry a voxelcam:trigger tag, carried none");
		}
		if (tag.trigger() != MomentTrigger.ADVANCEMENT || !"Stone Age".equals(tag.subject())) {
			throw new AssertionError("expected the advancement moment and its title, got " + tag);
		}
	}

	/** First trigger in a window wins and the rest are dropped silently — a cascade of advancements
	 * a second apart is one moment to the player, not five screenshots. */
	private static void assertASecondMomentInsideTheWindowIsDropped(ClientGameTestContext context, File dir) {
		Set<String> before = listing(dir);

		fire(context, MomentTrigger.BOSS_DEFEATED, "Ender Dragon");

		assertNothingNew(dir, before, "a second moment inside the cooldown window");
	}

	private static void assertADisabledTriggerCapturesNothing(ClientGameTestContext context, File dir) {
		resetAutoCapture(context);
		context.runOnClient(client -> MomentTrigger.BOSS_DEFEATED.setEnabled(false));
		Set<String> before = listing(dir);

		fire(context, MomentTrigger.BOSS_DEFEATED, "Ender Dragon");

		assertNothingNew(dir, before, "a trigger the player switched off");

		// ...and the switch is per trigger, not a master off: the others still fire.
		fire(context, MomentTrigger.ADVANCEMENT, "Stone Age");
		theOnlyNewFile(dir, before, "a still-enabled trigger while another is off");
	}

	/**
	 * The gate's whole reason for existing. A screen the player opened means the moment has passed,
	 * so the pending capture is dropped rather than held — held, it would shoot whatever they were
	 * looking at when they closed it. Closing the screen afterwards and finding still nothing is
	 * what separates "dropped" from "deferred".
	 */
	private static void assertAScreenThePlayerOpenedDropsThePendingCapture(ClientGameTestContext context, File dir) {
		resetAutoCapture(context);
		Set<String> before = listing(dir);

		context.setScreen(() -> new GuiScreenShotManager(dir));
		context.waitForScreen(GuiScreenShotManager.class);

		fire(context, MomentTrigger.ADVANCEMENT, "Adventuring Time");

		context.setScreen(() -> null);
		context.waitForScreen(null);
		context.waitTicks(40);

		assertNothingNew(dir, before, "a moment armed while the player had a screen open");
	}

	// --- helpers ------------------------------------------------------------------------------

	/**
	 * Fires on the client thread and then waits out the whole path: the next blit issues the
	 * readback, {@code Util.ioPool()} encodes the PNG, and only then are the tags spliced in.
	 */
	private static void fire(ClientGameTestContext context, MomentTrigger trigger, String subject) {
		context.runOnClient(client -> AutoCapture.fire(trigger, subject));
		context.waitTicks(40);
	}

	/** Both statics outlive a test: a charged cooldown would silence the next one, and a config
	 * toggle left over from {@code SettingsScreenTest} would too. */
	private static void resetAutoCapture(ClientGameTestContext context) {
		context.runOnClient(client -> {
			AutoCapture.forgetSession();
			VoxelCamConfig.forgetCurrent();
		});
		context.waitTicks(2);
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

	private static Set<String> newNames(File dir, Set<String> before) {
		Set<String> after = listing(dir);
		after.removeAll(before);
		after.removeIf(name -> !name.endsWith(".png"));
		return after;
	}

	private static File theOnlyNewFile(File dir, Set<String> before, String what) {
		Set<String> written = newNames(dir, before);
		if (written.size() != 1) {
			throw new AssertionError(what + " should have written exactly one screenshot, wrote "
					+ written.size() + ": " + written);
		}
		return new File(dir, written.iterator().next());
	}

	private static void assertNothingNew(File dir, Set<String> before, String what) {
		Set<String> written = newNames(dir, before);
		if (!written.isEmpty()) {
			throw new AssertionError(what + " should have written nothing, wrote " + written);
		}
	}

	/** Straight out of the IHDR, so the assertion is about the file on disk rather than about the
	 * code path that produced it. */
	private static Dimensions pngSize(File file) {
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			in.skipBytes(16);
			return new Dimensions(in.readInt(), in.readInt());
		} catch (IOException e) {
			throw new AssertionError("could not read " + file, e);
		}
	}
}
