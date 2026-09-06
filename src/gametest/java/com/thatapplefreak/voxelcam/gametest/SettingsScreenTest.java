package com.thatapplefreak.voxelcam.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.gui.GuiSettings;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import com.thatapplefreak.voxelcam.client.screenshot.Burst;
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

	private static void assertDoneReturnsToTheManager(ClientGameTestContext context) {
		context.clickScreenButton("voxelcam.done");
		context.waitForScreen(GuiScreenShotManager.class);
	}
}
