package com.thatapplefreak.voxelcam.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse buttons and the wheel do nothing while the capture menu is open.
 *
 * <p>The menu frees the mouse without opening a {@code Screen}, and with no screen up vanilla reads
 * a press on a released mouse as "click to regain focus": {@code onButton} calls {@code
 * grabMouse()}. That hands the deltas back to mouse-look under the dial and moves the tracked
 * cursor to the centre of the window, so the aim falls back into the dead zone. The same call then
 * clicks the button's key mapping, and nothing stops a right click using the held item or a middle
 * click picking a block. {@code onScroll} never looks at the grab at all and moves the hotbar.
 *
 * <p>Presses are dropped rather than taken as choosing the aimed wedge: letting go of the key
 * already does that, and the aim a click leaves alone is the one it fires. Releases pass through,
 * so a button that was down when the menu opened is not left logically held.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

	@Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
	private void voxelcam$dropPressesOverCaptureMenu(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
		if (action != InputConstants.RELEASE && CaptureMenu.isOpen()) {
			ci.cancel();
		}
	}

	@Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void voxelcam$dropScrollOverCaptureMenu(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (CaptureMenu.isOpen()) {
			ci.cancel();
		}
	}
}
