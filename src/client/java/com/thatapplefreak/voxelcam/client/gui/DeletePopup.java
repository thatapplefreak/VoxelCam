package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.Text;

public final class DeletePopup {

	private DeletePopup() {
	}

	public static ConfirmScreen create(GuiScreenShotManager parent) {
		return new ConfirmScreen(confirmed -> {
			if (confirmed) {
				VoxelCamIO.delete();
			}
			MinecraftClient.getInstance().setScreen(parent);
		}, Text.translatable("voxelcam.areyousure"), Text.translatable("voxelcam.delete"));
	}
}
