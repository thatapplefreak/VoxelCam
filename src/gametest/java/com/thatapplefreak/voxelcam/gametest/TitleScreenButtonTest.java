package com.thatapplefreak.voxelcam.gametest;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The camera button is placed by measuring vanilla's icon row at runtime, so it
 * breaks silently whenever vanilla changes that row — which is exactly what
 * happened in 26.2 when the Friends button arrived. This pins the placement.
 */
public class TitleScreenButtonTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(5);

		List<AbstractWidget> iconRow = context.computeOnClient(client -> IconRowAssertions.iconRow(client.gui.screen()));

		if (iconRow.size() < 2) {
			throw new AssertionError("expected vanilla's icon row plus the camera, found " + iconRow.size());
		}

		int cameraIndex = IconRowAssertions.cameraIndex(iconRow);
		if (cameraIndex < 0) {
			throw new AssertionError("camera button is not in the title screen's icon row");
		}
		if (cameraIndex != iconRow.size() - 1) {
			throw new AssertionError("camera should be the right-hand end of the row, was at " + cameraIndex);
		}

		IconRowAssertions.assertSharesOneRow(iconRow);
		IconRowAssertions.assertEvenlySpaced(iconRow);
		IconRowAssertions.assertRowIsCentred(context, iconRow);

		context.takeScreenshot("title-screen-icon-row");
	}
}
