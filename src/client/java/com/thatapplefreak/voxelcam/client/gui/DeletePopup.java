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
		// Name the file being deleted rather than asking "are you sure" about nothing. A burst
		// key also names its frame count, since Delete removes the whole group — silently
		// deleting several files under a confirmation that only named one would be a surprise.
		int frameCount = target == null ? 0 : VoxelCamIO.burstFrameCount(target);
		Component message;
		if (target == null) {
			message = Component.translatable("voxelcam.delete.confirm.generic");
		} else if (frameCount > 0) {
			message = Component.translatable("voxelcam.delete.confirm.burst", target.getName(), frameCount);
		} else {
			message = Component.translatable("voxelcam.delete.confirm", target.getName());
		}

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
