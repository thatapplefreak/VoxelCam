package com.thatapplefreak.voxelcam.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thatapplefreak.voxelcam.client.gui.GuiAutoCaptureSettings;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.gui.GuiSettings;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import com.thatapplefreak.voxelcam.client.screenshot.Burst;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTrigger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The settings screen reached from the manager's gear button, and the config file both it and
 * the capture menu's outer ring write to — the one part of the config system the unit suite
 * cannot reach, since {@code VoxelCamConfig.load()}/{@code saveCurrent()} both need a real
 * {@code FabricLoader}.
 */
public class SettingsScreenTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, "voxelcam-gametest-settings-shots"));
		context.runOnClient(client -> dir.mkdirs());

		context.setScreen(() -> new GuiScreenShotManager(dir));
		context.waitForScreen(GuiScreenShotManager.class);

		assertGearButtonOpensSettings(context);
		assertBurstLengthStepsAndPersists(context);
		assertBigScreenshotSizeButtonReflectsState(context);
		assertAutoCaptureTogglesAndPersists(context);
		assertDoneReturnsToTheManager(context);

		context.setScreen(TitleScreen::new);
		context.waitForScreen(TitleScreen.class);
	}

	private static void assertGearButtonOpensSettings(ClientGameTestContext context) {
		context.clickScreenButton("voxelcam.settings.gear");
		context.waitForScreen(GuiSettings.class);
		context.takeScreenshot("settings-screen");
	}

	/**
	 * Proves the write actually reaches disk under a real {@code FabricLoader}, not just that
	 * {@code VoxelCamConfig.current()} updated in memory — the one thing {@code
	 * VoxelCamConfigTest}'s {@code load(Path)}/{@code save(Path, ...)} seams cannot cover on
	 * their own, since they never touch {@code FabricLoader.getConfigDir()} itself.
	 */
	private static void assertBurstLengthStepsAndPersists(ClientGameTestContext context) {
		int before = context.computeOnClient(client -> Burst.getLength());

		context.clickScreenButton("voxelcam.settings.burstlengthplus");
		context.waitTicks(2);

		int after = context.computeOnClient(client -> Burst.getLength());
		if (after != before + 1) {
			throw new AssertionError("burst length should have gone from " + before
					+ " to " + (before + 1) + ", was " + after);
		}

		// Written on Util.ioPool(), fire-and-forget — give it a moment to actually land.
		context.waitTicks(20);

		int written = context.computeOnClient(client -> {
			File configFile = FabricLoader.getInstance().getConfigDir().resolve("voxelcam.json").toFile();
			if (!configFile.exists()) {
				throw new AssertionError("voxelcam.json should have been written after changing burst length");
			}
			try {
				String json = Files.readString(configFile.toPath());
				JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
				return parsed.get("burstLength").getAsInt();
			} catch (IOException e) {
				throw new AssertionError("could not read voxelcam.json", e);
			}
		});
		if (written != after) {
			throw new AssertionError("voxelcam.json should record burst length " + after + ", recorded " + written);
		}
	}

	/**
	 * The size button's label carries the current token, so it cannot be found by {@code
	 * clickScreenButton} (which matches a fixed translation, not one with a live substitution) —
	 * this only proves the screen renders the live value, the same way {@code
	 * SharePopupTest.assertEveryTargetIsOffered} proves a static set of labels are all present.
	 */
	private static void assertBigScreenshotSizeButtonReflectsState(ClientGameTestContext context) {
		String expected = context.computeOnClient(client ->
				"Big screenshot size: " + BigScreenshot.getSize().token());
		List<String> labels = context.computeOnClient(client -> {
			List<String> found = new ArrayList<>();
			for (AbstractWidget widget : Screens.getWidgets(client.gui.screen())) {
				found.add(widget.getMessage().getString());
			}
			return found;
		});
		if (!labels.contains(expected)) {
			throw new AssertionError("settings screen should show \"" + expected + "\", offers " + labels);
		}
	}

	/**
	 * The automatic-capture sub-screen, and the one setting on it with no session-static holder to
	 * mirror — which makes this the check that {@code VoxelCamConfig.snapshot()} really did copy the
	 * new fields across. The unit suite proves the seam in isolation; only here does a real click go
	 * all the way to a real {@code voxelcam.json}.
	 */
	private static void assertAutoCaptureTogglesAndPersists(ClientGameTestContext context) {
		context.clickScreenButton("voxelcam.settings.autocapture");
		context.waitForScreen(GuiAutoCaptureSettings.class);
		context.takeScreenshot("auto-capture-settings");

		// Pressable only because the toggle is a CycleButton: the API's matcher reads a cycle
		// button's *name* (the plain translation) rather than its rendered "Death: ON" label, and
		// handles no other widget with separate label and value — a checkbox it cannot press at all.
		context.clickScreenButton("voxelcam.moment.death");
		context.waitTicks(20);

		if (context.computeOnClient(client -> MomentTrigger.DEATH.isEnabled())) {
			throw new AssertionError("clicking the death toggle should have switched the trigger off");
		}
		if (writtenDeathTrigger(context)) {
			throw new AssertionError("voxelcam.json should record the death trigger as off");
		}

		// Back on, so the shipped default is what the rest of the suite and the next launch see.
		context.clickScreenButton("voxelcam.moment.death");
		context.waitTicks(20);
		if (!writtenDeathTrigger(context)) {
			throw new AssertionError("switching the death trigger back on should have been written too");
		}

		context.clickScreenButton("voxelcam.done");
		context.waitForScreen(GuiSettings.class);
	}

	private static boolean writtenDeathTrigger(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			File configFile = FabricLoader.getInstance().getConfigDir().resolve("voxelcam.json").toFile();
			if (!configFile.exists()) {
				throw new AssertionError("voxelcam.json should have been written after toggling a trigger");
			}
			try {
				JsonObject parsed = JsonParser.parseString(Files.readString(configFile.toPath())).getAsJsonObject();
				return parsed.get("autoCaptureDeath").getAsBoolean();
			} catch (IOException e) {
				throw new AssertionError("could not read voxelcam.json", e);
			}
		});
	}

	private static void assertDoneReturnsToTheManager(ClientGameTestContext context) {
		context.clickScreenButton("voxelcam.done");
		context.waitForScreen(GuiScreenShotManager.class);
	}
}
