package com.thatapplefreak.voxelcam.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/**
 * Shared checks for the camera button's placement in a vanilla row of square icon
 * buttons, used by both the title screen and pause menu variants of that layout.
 */
final class IconRowAssertions {

	/** Vanilla's icon buttons and ours are all square and this size. */
	static final int ICON_SIZE = 20;

	private IconRowAssertions() {
	}

	static List<AbstractWidget> iconRow(Screen screen) {
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

	/**
	 * The camera is the one with no message: it is icon-only and narrates through its
	 * tooltip, so an empty message is what distinguishes it from vanilla's icons.
	 */
	static int cameraIndex(List<AbstractWidget> row) {
		int cameraIndex = -1;
		for (int i = 0; i < row.size(); i++) {
			if (row.get(i).getMessage().getString().isEmpty()) {
				cameraIndex = i;
			}
		}
		return cameraIndex;
	}

	static void assertSharesOneRow(List<AbstractWidget> row) {
		int y = row.get(0).getY();
		for (AbstractWidget widget : row) {
			if (widget.getY() != y) {
				throw new AssertionError("icon row is not on one line: " + y + " vs " + widget.getY());
			}
		}
	}

	/** Uneven gaps are the visible symptom of the row not having been re-laid-out. */
	static void assertEvenlySpaced(List<AbstractWidget> row) {
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
	 * guards — it looks fine in isolation and wrong next to the menu around it.
	 */
	static void assertRowIsCentred(ClientGameTestContext context, List<AbstractWidget> row) {
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
