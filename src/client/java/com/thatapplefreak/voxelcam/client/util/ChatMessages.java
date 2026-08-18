package com.thatapplefreak.voxelcam.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatMessages {

	private static final String PREFIX_KEY = "voxelcam.chat.prefix";

	private ChatMessages() {
	}

	/** Sends "[VoxelCam] <body>" to the in-game chat, with the prefix in dark red. */
	public static void send(Text body) {
		MutableText message = Text.translatable(PREFIX_KEY).formatted(Formatting.DARK_RED)
				.append(Text.literal(" ")).append(body);
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(message, false);
		}
	}

	public static void send(String translationKey, Object... args) {
		send(Text.translatable(translationKey, args));
	}
}
