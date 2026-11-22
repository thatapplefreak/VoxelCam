package com.thatapplefreak.voxelcam.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.screenshot.AutoCapture;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
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
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;

/**
 * The automatic capture path end to end: {@code AutoCapture.fire} through the blit, the write on
 * the IO pool, and the tags embedded afterwards. The unit suite already proves what the cooldown
 * and the gate <em>decide</em>; what only a real client can show is that a decision to capture
 * actually produces a plain-size, correctly tagged PNG — and, just as importantly, that the four
 * decisions not to capture produce no file at all.
 *
 * <p>Triggers are fired directly rather than through their hooks, with one exception. Earning an
 * advancement, dying and walking a portal are server-side events this test does not stage, since
 * staging them would be testing Minecraft rather than VoxelCam. Of their hooks, only the
 * advancement's is a mixin, and launching the client at all covers that it resolves, since {@code
 * defaultRequire: 1} fails a mis-targeted injector at load. Death and a new dimension are watches
 * in {@code AutoCapture.tick} with no injector to resolve, so nothing here exercises them. The
 * exception is a boss: the integrated server takes commands, so a Wither is summoned and killed
 * for real, and that capture arrives through {@code BossBarSignal} with nothing here calling
 * {@code fire}. A death is staged once too, under {@code immediate_respawn}, and only to show
 * that it is dropped at once rather than captured.
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
			assertAMomentUnderAHeldCaptureMenuIsDropped(context, dir);
			assertAWitherKilledAtFullHealthIsCaptured(context, singleplayer.getServer(), dir);
			assertADeathUnderImmediateRespawnIsNotCapturedAndBlocksNothing(context, singleplayer.getServer(), dir);
		}
	}

	/**
	 * The one trigger hook that can be staged: the integrated server takes commands, and a Wither
	 * can be summoned and killed with two of them. Its bar is the hard case for {@code
	 * BossBarTracker}, because a Wither stops updating it at the killing blow, so the bar is removed
	 * still showing the health that blow landed on. Here that is all of it.
	 *
	 * <p>{@code NoAI} keeps the Wither from firing at the player, and is also why its bar never
	 * moves: a Wither writes its bar from its AI step. Peaceful difficulty discards a Wither without
	 * killing it, which makes it the control. That bar goes the same way, at the same progress,
	 * with nothing dying, and must not be taken for a kill.
	 */
	private static void assertAWitherKilledAtFullHealthIsCaptured(ClientGameTestContext context,
			TestServerContext server, File dir) {
		resetAutoCapture(context);
		Set<String> before = listing(dir);

		try {
			server.runCommand("difficulty normal");
			server.runCommand("execute at @p run summon minecraft:wither ~ ~10 ~ {NoAI:1b}");
			context.waitTicks(40);
			server.runCommand("difficulty peaceful");
			context.waitTicks(60);
			assertNothingNew(dir, before, "a Wither that went away at full health without dying");

			server.runCommand("difficulty normal");
			server.runCommand("execute at @p run summon minecraft:wither ~ ~10 ~ {NoAI:1b}");
			context.waitTicks(40);
			server.runCommand("kill @e[type=minecraft:wither]");
			// Twenty ticks of dying before the Wither and its bar go, then the blit, the encode on
			// the IO pool and the tags.
			context.waitTicks(100);
		} finally {
			server.runCommand("difficulty peaceful");
		}

		File written = theOnlyNewFile(dir, before, "a Wither killed at full health");
		MomentTag tag = MomentTag.fromTags(PngTextChunk.read(written));
		if (tag == null || tag.trigger() != MomentTrigger.BOSS_DEFEATED || !"Wither".equals(tag.subject())) {
			throw new AssertionError("expected the boss-defeated moment naming the Wither, got " + tag);
		}
	}

	/**
	 * A real death, staged with {@code /kill} so it arrives through the {@code watchDeath} tick watch,
	 * under {@code immediate_respawn}. Vanilla answers the kill packet by respawning instead of
	 * opening {@code DeathScreen}, so there is no frame of the death to take: the only shots left
	 * are of the respawn point, and the death is dropped rather than captured.
	 *
	 * <p>What this guards is how soon. An advancement a few ticks after the respawn must be taken:
	 * a death left waiting for a screen that is never coming holds the one pending slot for seconds
	 * and refuses it.
	 */
	private static void assertADeathUnderImmediateRespawnIsNotCapturedAndBlocksNothing(
			ClientGameTestContext context, TestServerContext server, File dir) {
		resetAutoCapture(context);
		Set<String> before = listing(dir);

		try {
			server.runCommand("gamerule immediate_respawn true");
			context.waitFor(client -> client.player != null && !client.player.shouldShowDeathScreen(), 100);
			LocalPlayer dying = context.computeOnClient(client -> client.player);
			server.runCommand("kill @a");
			// The watch arms on the one tick health reads zero, too brief to wait for. A respawn
			// builds a new LocalPlayer, which is what says the death has come and gone.
			context.waitFor(client -> client.player != null && client.player != dying
					&& client.gui.screen() == null, 400);
			context.waitTicks(5);
			assertNothingNew(dir, before, "a death under immediate respawn");

			fire(context, MomentTrigger.ADVANCEMENT, "Stone Age");
			File written = theOnlyNewFile(dir, before, "an advancement just after a death under immediate respawn");
			MomentTag tag = MomentTag.fromTags(PngTextChunk.read(written));
			if (tag == null || tag.trigger() != MomentTrigger.ADVANCEMENT) {
				throw new AssertionError("expected the advancement moment, not the death, got " + tag);
			}
		} finally {
			server.runCommand("gamerule immediate_respawn false");
		}
	}

	/**
	 * The capture menu is not a screen, so the gate's screen rows read a held one as a clear view.
	 * It is the player's own doing all the same: a moment taken under it has the dial drawn across
	 * it, and its save can refuse the screenshot the player was aiming. The real key is held, since
	 * that is what arms the menu in play.
	 *
	 * <p>The menu is backed out of with Escape rather than released, because a release takes the
	 * player's own screenshot and this is about the file nobody asked for. Still nothing after it
	 * closes is what separates dropped from deferred, and the moment that follows being taken is
	 * what shows the refused one did not spend the cooldown.
	 */
	private static void assertAMomentUnderAHeldCaptureMenuIsDropped(ClientGameTestContext context, File dir) {
		resetAutoCapture(context);
		Set<String> before = listing(dir);

		context.getInput().holdKey(InputConstants.KEY_F2);
		try {
			context.waitFor(client -> CaptureMenu.isOpen(), 200);

			fire(context, MomentTrigger.ADVANCEMENT, "Stone Age");
			assertNothingNew(dir, before, "a moment that arrived while the capture menu was held");

			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
		} finally {
			context.getInput().releaseKey(InputConstants.KEY_F2);
		}
		context.waitTicks(40);
		assertNothingNew(dir, before, "a moment that arrived under a capture menu since cancelled");

		fire(context, MomentTrigger.ADVANCEMENT, "Stone Age");
		theOnlyNewFile(dir, before, "the next moment after the capture menu was put away");
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
