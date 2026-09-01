package com.thatapplefreak.voxelcam.gametest;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The pause menu carries the same row of square icon buttons as the title screen
 * (bug report, chat report, player report), so it goes through the same
 * find-and-relayout path in {@code VoxelCamClient}. This pins that placement the
 * way {@link TitleScreenButtonTest} pins the title screen's.
 */
public class PauseScreenButtonTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			context.setScreen(() -> new PauseScreen(true));
			context.waitForScreen(PauseScreen.class);
			context.waitTicks(5);

			List<AbstractWidget> iconRow = context.computeOnClient(client -> IconRowAssertions.iconRow(client.gui.screen()));

			if (iconRow.size() < 2) {
				throw new AssertionError("expected the pause menu's icon row plus the camera, found " + iconRow.size());
			}

			int cameraIndex = IconRowAssertions.cameraIndex(iconRow);
			if (cameraIndex < 0) {
				throw new AssertionError("camera button is not in the pause menu's icon row");
			}
			if (cameraIndex != iconRow.size() - 1) {
				throw new AssertionError("camera should be the right-hand end of the row, was at " + cameraIndex);
			}

			IconRowAssertions.assertSharesOneRow(iconRow);
			IconRowAssertions.assertEvenlySpaced(iconRow);
			IconRowAssertions.assertRowIsCentred(context, iconRow);

			context.takeScreenshot("pause-menu-icon-row");
		}
	}
}
