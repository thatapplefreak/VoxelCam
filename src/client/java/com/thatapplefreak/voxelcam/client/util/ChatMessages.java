package com.thatapplefreak.voxelcam.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ChatMessages {

	private static final String PREFIX_KEY = "voxelcam.chat.prefix";

	private ChatMessages() {
	}

	/** Sends "[VoxelCam] <body>" to the in-game chat, with the prefix in dark red. */
	public static void send(Component body) {
		MutableComponent message = Component.translatable(PREFIX_KEY).withStyle(ChatFormatting.DARK_RED)
				.append(Component.literal(" ")).append(body);
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(message, false);
		}
	}

	public static void send(String translationKey, Object... args) {
		send(Component.translatable(translationKey, args));
	}
}
