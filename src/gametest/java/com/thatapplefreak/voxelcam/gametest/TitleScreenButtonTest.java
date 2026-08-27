package com.thatapplefreak.voxelcam.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The camera button is placed by measuring vanilla's icon row at runtime, so it
 * breaks silently whenever vanilla changes that row — which is exactly what
 * happened in 26.2 when the Friends button arrived. This pins the placement.
 */
public class TitleScreenButtonTest implements FabricClientGameTest {

	/** Vanilla's icon buttons and ours are all square and this size. */
	private static final int ICON_SIZE = 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(5);

		List<AbstractWidget> iconRow = context.computeOnClient(client -> iconRow(client.gui.screen()));

		if (iconRow.size() < 2) {
			throw new AssertionError("expected vanilla's icon row plus the camera, found " + iconRow.size());
		}

		// The camera is the one with no message: it is icon-only and narrates through
		// its tooltip, so an empty message is what distinguishes it from vanilla's.
		int cameraIndex = -1;
		for (int i = 0; i < iconRow.size(); i++) {
			if (iconRow.get(i).getMessage().getString().isEmpty()) {
				cameraIndex = i;
			}
		}
		if (cameraIndex < 0) {
			throw new AssertionError("camera button is not in the title screen's icon row");
		}
		if (cameraIndex != iconRow.size() - 1) {
			throw new AssertionError("camera should be the right-hand end of the row, was at " + cameraIndex);
		}

		assertSharesOneRow(iconRow);
		assertEvenlySpaced(iconRow);
		assertRowIsCentred(context, iconRow);

		context.takeScreenshot("title-screen-icon-row");
	}

	private static List<AbstractWidget> iconRow(Screen screen) {
		List<AbstractWidget> icons = new ArrayList<>();
		int lowest = Integer.MIN_VALUE;
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
			if (widget.getWidth() == ICON_SIZE && widget.getHeight() == ICON_SIZE) {
				lowest = Math.max(lowest, widget.getY());
			}
		}
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
			if (widget.getWidth() == ICON_SIZE && widget.getHeight() == ICON_SIZE && widget.getY() == lowest) {
				icons.add(widget);
			}
		}
		icons.sort(Comparator.comparingInt(AbstractWidget::getX));
		return icons;
	}

	private static void assertSharesOneRow(List<AbstractWidget> row) {
		int y = row.get(0).getY();
		for (AbstractWidget widget : row) {
			if (widget.getY() != y) {
				throw new AssertionError("icon row is not on one line: " + y + " vs " + widget.getY());
			}
		}
	}

	/** Uneven gaps are the visible symptom of the row not having been re-laid-out. */
	private static void assertEvenlySpaced(List<AbstractWidget> row) {
		int slot = row.get(1).getX() - row.get(0).getX();
		for (int i = 1; i < row.size(); i++) {
			int gap = row.get(i).getX() - row.get(i - 1).getX();
			if (gap != slot) {
				throw new AssertionError("uneven icon spacing: expected " + slot + ", found " + gap);
			}
		}
	}

	/**
	 * Vanilla centres this row. Appending the camera without shifting the others
	 * would leave the group off-centre by half a slot, which is the regression this
	 * guards — it looks fine in isolation and wrong next to the menu above it.
	 */
	private static void assertRowIsCentred(ClientGameTestContext context, List<AbstractWidget> row) {
		int left = row.get(0).getX();
		int right = row.get(row.size() - 1).getX() + ICON_SIZE;
		int screenWidth = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());

		int rowCentre = (left + right) / 2;
		int screenCentre = screenWidth / 2;
		if (Math.abs(rowCentre - screenCentre) > 1) {
			throw new AssertionError("icon row is off-centre: row centre " + rowCentre
					+ " vs screen centre " + screenCentre);
		}
	}
}
