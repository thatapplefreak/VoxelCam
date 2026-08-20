package com.thatapplefreak.voxelcam.client;

import com.thatapplefreak.voxelcam.client.command.BigScreenshotCommand;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.gui.PhotoButton;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class VoxelCamClient implements ClientModInitializer {

	public static final String MOD_ID = "voxelcam";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of(MOD_ID, "voxelcam"));

	/** Spacing between the camera button and the row it sits beside. */
	private static final int GAP = 4;

	private static KeyBinding openScreenshotManagerKey;

	@Override
	public void onInitializeClient() {
		openScreenshotManagerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.voxelcam.openscreenshotmanager",
				InputUtil.Type.KEYSYM,
				InputUtil.GLFW_KEY_H,
				CATEGORY));

		BigScreenshotCommand.register();

		ClientTickEvents.END_CLIENT_TICK.register(VoxelCamClient::onEndTick);

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen) {
				addTitleScreenButton(client, screen, scaledWidth, scaledHeight);
			}
		});

		LOGGER.info("VoxelCam initializing");
	}

	/**
	 * Puts the camera button on the title screen's bottom button row, past its
	 * right end. The LiteLoader build hardcoded width/2 + 104, but modern vanilla
	 * puts its own accessibility button in exactly that slot, so the row is
	 * measured at runtime and the camera goes after whatever is already there.
	 */
	private static void addTitleScreenButton(MinecraftClient client, Screen screen, int width, int height) {
		int columnLeft = width / 2 - 101;
		int columnRight = width / 2 + 101;
		int rowY = Integer.MIN_VALUE;
		for (ClickableWidget widget : Screens.getButtons(screen)) {
			// Full-width menu entries only, so the row is found from vanilla's own
			// layout rather than from an icon button some other mod injected.
			if (widget.getWidth() >= 90 && widget.getX() >= columnLeft
				&& widget.getX() + widget.getWidth() <= columnRight) {
				rowY = Math.max(rowY, widget.getY());
			}
		}
		if (rowY == Integer.MIN_VALUE) {
			// Unrecognised layout (a menu-replacing mod, say): fall back to where the
			// bottom row sits in vanilla.
			rowY = height / 4 + 132;
		}

		// Everything sharing that row, including vanilla's language and accessibility
		// icons, so the camera lands clear of them instead of on top.
		int rowLeft = Integer.MAX_VALUE;
		int rowRight = Integer.MIN_VALUE;
		for (ClickableWidget widget : Screens.getButtons(screen)) {
			if (widget.getY() < rowY + PhotoButton.SIZE && widget.getY() + widget.getHeight() > rowY) {
				rowLeft = Math.min(rowLeft, widget.getX());
				rowRight = Math.max(rowRight, widget.getX() + widget.getWidth());
			}
		}
		if (rowRight == Integer.MIN_VALUE) {
			rowLeft = width / 2 - 100;
			rowRight = width / 2 + 100;
		}

		int x = rowRight + GAP;
		if (x + PhotoButton.SIZE > width - GAP) {
			// No room on the right (narrow window, or a mod already extended the row):
			// tuck it onto the left end instead of letting it hang off screen.
			x = rowLeft - GAP - PhotoButton.SIZE;
		}
		Screens.getButtons(screen).add(new PhotoButton(x, rowY,
			b -> client.setScreen(new GuiScreenShotManager(screenshotsDir(client)))));
	}

	private static File screenshotsDir(MinecraftClient client) {
		return new File(client.runDirectory, ScreenshotRecorder.SCREENSHOTS_DIRECTORY);
	}

	private static void onEndTick(MinecraftClient client) {
		while (openScreenshotManagerKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new GuiScreenShotManager(screenshotsDir(client)));
			}
		}
	}
}
