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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
			if (screen instanceof TitleScreen || screen instanceof PauseScreen) {
				addIconRowButton(client, screen, scaledWidth, scaledHeight);
			}
		});

		LOGGER.info("VoxelCam initializing");
	}

	/**
	 * Puts the camera button in the screen's row of square icon buttons — the title
	 * screen's friends/language/accessibility row, or the pause menu's bug-report/chat
	 * report/player report row.
	 *
	 * The row is found and re-laid-out at runtime rather than hardcoded. Vanilla
	 * centres it on the screen, so appending a button without moving the others would
	 * leave the whole group visibly off-centre; instead the existing icons shift left
	 * by half a slot and the camera takes the new right-hand end, which is what
	 * vanilla's own layout would produce for one more button.
	 */
	private static void addIconRowButton(Minecraft client, Screen screen, int width, int height) {
		Button.OnPress open = b -> client.setScreenAndShow(new GuiScreenShotManager(screenshotsDir(client)));

		List<AbstractWidget> iconRow = findIconRow(screen);
		if (!iconRow.isEmpty()) {
			// Derived from the row itself, so a resource pack or mod using a different
			// pitch stays consistent instead of having the camera jammed against a neighbour.
			int slot = iconRow.size() > 1
					? iconRow.get(1).getX() - iconRow.get(0).getX()
					: PhotoButton.SIZE + GAP;
			int count = iconRow.size() + 1;
			int rowWidth = count * PhotoButton.SIZE + (count - 1) * (slot - PhotoButton.SIZE);
			int left = width / 2 - rowWidth / 2;
			int rowY = iconRow.get(0).getY();

			for (int i = 0; i < iconRow.size(); i++) {
				iconRow.get(i).setX(left + i * slot);
			}
			Screens.getWidgets(screen).add(
					new PhotoButton(left + iconRow.size() * slot, rowY, open));
			return;
		}

		// Unrecognised layout (a menu-replacing mod, say): fall back to sitting past the
		// right end of the bottom full-width row, where the button used to live.
		Screens.getWidgets(screen).add(new PhotoButton(fallbackX(screen, width), fallbackY(screen, height), open));
	}

	/**
	 * The contiguous run of square, icon-sized buttons vanilla centres above the
	 * Options/Quit row, ordered left to right. Empty if nothing matches.
	 */
	private static List<AbstractWidget> findIconRow(Screen screen) {
		Map<Integer, List<AbstractWidget>> rows = new HashMap<>();
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
			// Square and icon-sized: enough to tell vanilla's icon buttons from the
			// full-width menu entries without naming their classes, which are private.
			if (widget.getWidth() == PhotoButton.SIZE && widget.getHeight() == PhotoButton.SIZE) {
				rows.computeIfAbsent(widget.getY(), y -> new ArrayList<>()).add(widget);
			}
		}
		// Lowest such row, so a mod adding its own icons higher up does not capture the camera.
		List<AbstractWidget> best = rows.entrySet().stream()
				.max(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.orElseGet(ArrayList::new);
		best.sort(Comparator.comparingInt(AbstractWidget::getX));
		return best;
	}

	private static int fallbackY(Screen screen, int height) {
		int rowY = Integer.MIN_VALUE;
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
			if (widget.getWidth() >= 90) {
				rowY = Math.max(rowY, widget.getY());
			}
		}
		return rowY == Integer.MIN_VALUE ? height / 4 + 132 : rowY;
	}

	private static int fallbackX(Screen screen, int width) {
		int rowRight = Integer.MIN_VALUE;
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
			rowRight = Math.max(rowRight, widget.getX() + widget.getWidth());
		}
		if (rowRight == Integer.MIN_VALUE) {
			return width / 2 + 100 + GAP;
		}
		int x = rowRight + GAP;
		// No room on the right (narrow window, or a mod already extended the row):
		// tuck it onto the left end instead of letting it hang off screen.
		return x + PhotoButton.SIZE > width - GAP ? Math.max(GAP, width / 2 - 100 - GAP - PhotoButton.SIZE) : x;
	}

	private static File screenshotsDir(Minecraft client) {
		return new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR);
	}

	private static void onEndTick(Minecraft client) {
		while (openScreenshotManagerKey.consumeClick()) {
			if (client.gui.screen() == null) {
				client.setScreenAndShow(new GuiScreenShotManager(screenshotsDir(client)));
			}
		}
	}
}
