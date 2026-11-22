package com.thatapplefreak.voxelcam.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.screenshot.AutoCapture;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTag;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTrigger;
import com.thatapplefreak.voxelcam.client.screenshot.PngTextChunk;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The automatic capture path end to end: {@code AutoCapture.fire} through the blit, the write on
 * the IO pool, and the tags embedded afterwards. The unit suite already proves what the cooldown
 * and the gate <em>decide</em>; what only a real client can show is that a decision to capture
 * actually produces a plain-size, correctly tagged PNG — and, just as importantly, that the four
 * decisions not to capture produce no file at all.
 *
 * <p>Triggers are fired directly rather than through their hooks, with two exceptions. Earning an
 * advancement and dying are server-side events this test does not stage, since staging them would
 * be testing Minecraft rather than VoxelCam. Of their hooks, only the advancement's is a mixin, and
 * launching the client at all covers that it resolves, since {@code defaultRequire: 1} fails a
 * mis-targeted injector at load. Death is a watch in {@code AutoCapture.tick} with no injector to
 * resolve, so nothing here exercises it. The exceptions are a boss and a portal, because what the
 * capture looks like there depends on what the client is doing around it. The integrated server
 * takes commands, so a Wither is summoned and killed for real, and a Nether portal is built and
 * walked into, and neither capture comes from anything here calling {@code fire}.
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
			assertAPortalArrivalIsCapturedWithTheWorldInView(context, singleplayer.getServer(), dir);
		}
	}

	/**
	 * A real portal, walked in survival so its overlay builds to full strength, both ways a player
	 * meets it. The first trip is a fresh world's first Nether visit, which also grants "We Need to
	 * Go Deeper" under the loading screen, so whichever of the two moments arms first is the one
	 * taken. The second, after the session's dimensions are forgotten, is the new-dimension moment
	 * on its own, the shape every later session's first trip takes.
	 *
	 * <p>Vanilla closes the loading screen once the player's own section is ready, not the world
	 * around it, and the overlay is still fading out then. What a player expects is a picture of
	 * where they arrived, so each capture is compared with the same view once everything has
	 * settled: the moment is the place, not the fog it was drawn out of.
	 */
	private static void assertAPortalArrivalIsCapturedWithTheWorldInView(ClientGameTestContext context,
			TestServerContext server, File dir) {
		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty peaceful");
		server.runCommand("execute at @p run fill ~-1 ~-1 ~3 ~2 ~3 ~3 minecraft:obsidian");
		server.runCommand("execute at @p run fill ~ ~ ~3 ~1 ~2 ~3 minecraft:nether_portal[axis=x]");
		int[] home = context.computeOnClient(client -> new int[] {
				client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ()});
		String inFrontOfThePortal = (home[0] + 0.5) + " " + home[1] + " " + (home[2] + 0.5);
		String intoThePortal = (home[0] + 0.5) + " " + home[1] + " " + (home[2] + 3.5);
		context.waitTicks(5);

		resetAutoCapture(context);
		Set<String> before = listing(dir);
		server.runCommand("tp @p " + intoThePortal);
		File first = awaitArrival(context, dir, before, "the first trip to the Nether");
		assertLooksLikeTheSettledView(context, first, "the first trip to the Nether");

		// Back beside the portal rather than into it: a dimension change puts the player on portal
		// cooldown, and standing in a portal keeps renewing it.
		server.runCommand("execute in minecraft:overworld run tp @p " + inFrontOfThePortal);
		context.waitFor(client -> client.level != null && client.gui.screen() == null
				&& client.level.dimension().identifier().getPath().equals("overworld"), 600);
		context.waitTicks(40);
		// Forgotten from the overworld, so that is the session's login dimension and the Nether is
		// new again, with its advancement already earned.
		resetAutoCapture(context);
		before = listing(dir);
		server.runCommand("tp @p " + intoThePortal);
		File second = awaitArrival(context, dir, before, "a later session's first trip to the Nether");
		MomentTag tag = MomentTag.fromTags(PngTextChunk.read(second));
		if (tag == null || tag.trigger() != MomentTrigger.NEW_DIMENSION
				|| !"minecraft:the_nether".equals(tag.subject())) {
			throw new AssertionError("expected the new-dimension moment for the Nether, got " + tag);
		}
		assertLooksLikeTheSettledView(context, second, "a later session's first trip to the Nether");
	}

	/** Eighty ticks in the portal in survival, the load, and then however long the view takes to
	 * come in, with room for the encode and the tags after. */
	private static File awaitArrival(ClientGameTestContext context, File dir, Set<String> before, String what) {
		context.waitFor(client -> !newNames(dir, before).isEmpty(), 900);
		context.waitTicks(40);
		return theOnlyNewFile(dir, before, what);
	}

	/**
	 * Measured against the empty first frame vanilla hands over, which differed from the settled
	 * view by 96 per channel through the portal overlay; a capture of the drawn world differs only
	 * by the portal particles drifting through it.
	 */
	private static final double SETTLED_VIEW_TOLERANCE = 10.0;

	private static void assertLooksLikeTheSettledView(ClientGameTestContext context, File moment, String what) {
		// Long enough for every chunk in render distance to arrive, build and be drawn.
		context.waitTicks(200);
		File settled = context.takeScreenshot("arrival-settled").toFile();
		double difference = meanDifference(moment, settled);
		if (difference > SETTLED_VIEW_TOLERANCE) {
			throw new AssertionError("the moment taken on " + what + " should show where the player arrived, but"
					+ " differs from the settled view by " + difference + " per channel: compare "
					+ moment.getName() + " with " + settled.getName());
		}
		// The test's own screenshot lands in the same folder, and is not a moment.
		settled.delete();
	}

	/** Mean absolute difference per channel over the middle of the frame, clear of the HUD: notices
	 * and toasts along the top, and the chat, hotbar and hand along the bottom. */
	private static double meanDifference(File a, File b) {
		try {
			BufferedImage left = ImageIO.read(a);
			BufferedImage right = ImageIO.read(b);
			if (left.getWidth() != right.getWidth() || left.getHeight() != right.getHeight()) {
				throw new AssertionError(a.getName() + " and " + b.getName() + " differ in size");
			}
			long total = 0;
			long samples = 0;
			for (int y = left.getHeight() / 6; y < left.getHeight() * 3 / 4; y++) {
				for (int x = left.getWidth() / 4; x < left.getWidth() * 3 / 4; x++) {
					int p = left.getRGB(x, y);
					int q = right.getRGB(x, y);
					for (int shift = 0; shift <= 16; shift += 8) {
						total += Math.abs(((p >> shift) & 0xFF) - ((q >> shift) & 0xFF));
						samples++;
					}
				}
			}
			return (double) total / samples;
		} catch (IOException e) {
			throw new AssertionError("could not read " + a + " or " + b, e);
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
