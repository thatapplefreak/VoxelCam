package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public final class DeletePopup {

	private DeletePopup() {
	}

	public static ConfirmScreen create(GuiScreenShotManager parent) {
		File target = VoxelCamIO.getSelectedPhoto();
		// Name the file being deleted rather than asking "are you sure" about nothing.
		Component message = target == null
				? Component.translatable("voxelcam.delete.confirm.generic")
				: Component.translatable("voxelcam.delete.confirm", target.getName());

		return new ConfirmScreen(confirmed -> {
			if (confirmed && target != null) {
				// A delete that failed leaves the file on disk, and the manager re-lists the
				// directory as it comes back, so the row returns on its own; the manager has
				// to say why. Chat cannot: it is silent from the title screen.
				parent.reportDeleteResult(target, VoxelCamIO.delete());
			}
			Minecraft.getInstance().setScreenAndShow(parent);
		}, Component.translatable("voxelcam.delete.title"), message,
				Component.translatable("voxelcam.delete"), Component.translatable("gui.cancel"));
	}
}
