package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.Text;

import java.io.File;

public final class DeletePopup {

	private DeletePopup() {
	}

	public static ConfirmScreen create(GuiScreenShotManager parent) {
		File target = VoxelCamIO.getSelectedPhoto();
		// Name the file being deleted rather than asking "are you sure" about nothing.
		Text message = target == null
				? Text.translatable("voxelcam.delete.confirm.generic")
				: Text.translatable("voxelcam.delete.confirm", target.getName());

		return new ConfirmScreen(confirmed -> {
			if (confirmed) {
				VoxelCamIO.delete();
			}
			MinecraftClient.getInstance().setScreen(parent);
		}, Text.translatable("voxelcam.delete.title"), message,
				Text.translatable("voxelcam.delete"), Text.translatable("gui.cancel"));
	}
}
