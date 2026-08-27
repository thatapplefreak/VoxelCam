package com.thatapplefreak.voxelcam.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshotSize;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * {@code /bigscreenshot} (or {@code /bs}) sets the size an oversized capture will use; the
 * screenshot key with shift held is what actually takes one. Feedback goes through the command
 * source rather than {@code ChatMessages} because a client command only ever runs with a player.
 */
public final class BigScreenshotCommand {

	private static final String ARGUMENT = "size";

	private static final SuggestionProvider<FabricClientCommandSource> SUGGESTIONS =
			(context, builder) -> SharedSuggestionProvider.suggest(BigScreenshotSize.tokens(), builder);

	private BigScreenshotCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(tree("bigscreenshot"));
		// A second full tree rather than a Brigadier redirect: a redirect node carries no
		// command of its own, so a bare "/bs" would not reach the report branch.
		dispatcher.register(tree("bs"));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> tree(String name) {
		return ClientCommands.literal(name)
				.executes(BigScreenshotCommand::report)
				.then(ClientCommands.argument(ARGUMENT, StringArgumentType.word())
						.suggests(SUGGESTIONS)
						.executes(BigScreenshotCommand::set));
	}

	private static int report(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		BigScreenshotSize size = BigScreenshot.getSize();
		BigScreenshotSize.Resolved resolved = size.resolve(source.getClient().getWindow());
		source.sendFeedback(Component.translatable("voxelcam.bigshot.current", describe(size, resolved)));
		return 1;
	}

	/** "4k (3840x2160)", but just "1000x1000" when the name is already the dimensions. */
	private static String describe(BigScreenshotSize size, BigScreenshotSize.Resolved resolved) {
		String pixels = resolved.width() + "x" + resolved.height();
		return size.token().equals(pixels) ? pixels : size.token() + " (" + pixels + ")";
	}

	private static int set(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		String token = StringArgumentType.getString(context, ARGUMENT);

		BigScreenshotSize size = BigScreenshotSize.parse(token);
		if (size == null) {
			source.sendError(Component.translatable("voxelcam.bigshot.invalid", token,
					String.join(", ", BigScreenshotSize.tokens())));
			return 0;
		}

		BigScreenshot.setSize(size);
		BigScreenshotSize.Resolved resolved = size.resolve(source.getClient().getWindow());
		source.sendFeedback(Component.translatable("voxelcam.bigshot.set", describe(size, resolved)));
		if (resolved.clamped()) {
			source.sendFeedback(Component.translatable("voxelcam.bigshot.clamped", resolved.maxTextureSize()));
		}
		return 1;
	}
}
