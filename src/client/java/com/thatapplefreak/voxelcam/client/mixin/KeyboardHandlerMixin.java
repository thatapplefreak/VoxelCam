package com.thatapplefreak.voxelcam.client.mixin;

import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Escape backs out of the capture menu.
 *
 * <p>The press has to be swallowed here rather than reacted to a tick later: by then vanilla has
 * already opened the pause screen, and a cancel that pauses the game is not a cancel. Taking it at
 * the head of key handling leaves the player where they were, with no capture taken.
 *
 * <p>Only while the menu is actually open, and only the press — a release passes through, and
 * Escape with no menu up pauses the game exactly as it always did.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

	@Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
	private void voxelcam$cancelCaptureMenuOnEscape(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action != GLFW.GLFW_RELEASE && event.key() == GLFW.GLFW_KEY_ESCAPE && CaptureMenu.isOpen()) {
			CaptureMenu.abort();
			ci.cancel();
		}
	}
}
