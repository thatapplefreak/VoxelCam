package com.thatapplefreak.voxelcam.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thatapplefreak.voxelcam.client.command.BigScreenshotCommand;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.gui.PhotoButton;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class VoxelCamClient implements ClientModInitializer {

	public static final String MOD_ID = "voxelcam";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "voxelcam"));

	/** Spacing between the camera button and the row it sits beside. */
	private static final int GAP = 4;

	private static KeyMapping openScreenshotManagerKey;

	@Override
	public void onInitializeClient() {
		openScreenshotManagerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.voxelcam.openscreenshotmanager",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_H,
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
	private static void addTitleScreenButton(Minecraft client, Screen screen, int width, int height) {
		int columnLeft = width / 2 - 101;
		int columnRight = width / 2 + 101;
		int rowY = Integer.MIN_VALUE;
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
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
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
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
		Screens.getWidgets(screen).add(new PhotoButton(x, rowY,
			b -> client.setScreenAndShow(new GuiScreenShotManager(screenshotsDir(client)))));
	}

	private static File screenshotsDir(Minecraft client) {
		return new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR);
	}

	private static void onEndTick(Minecraft client) {
		while (openScreenshotManagerKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreenAndShow(new GuiScreenShotManager(screenshotsDir(client)));
			}
		}
	}
}
