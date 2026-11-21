package com.thatapplefreak.voxelcam.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.screenshot.AutoCapture;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import com.thatapplefreak.voxelcam.client.screenshot.Burst;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureStatus;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Everything VoxelCam injects into Minecraft itself, which is two unrelated things.
 *
 * The first is the two points an oversized capture needs inside a frame: before anything
 * is drawn, where the framebuffer can still be resized, and after the frame is finished but
 * before it is presented, where it can be read back. Both live in renderFrame rather than
 * runTick: 26.x split the old single frame method, leaving runTick with the game tick and
 * renderFrame with everything from acquiring the surface through presenting it.
 *
 * The second is key routing. An injection at the head of handleGlobalKeyPress is what lets
 * the manager's F2 rename shortcut have its key, and is here only because that method is
 * Minecraft's. Its own comment says why it is needed.
 */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {

	@Inject(method = "renderFrame(Z)V", at = @At("HEAD"))
	private void voxelcam$beforeFrame(boolean tick, CallbackInfo ci) {
		BigScreenshot.beforeFrame();
		CaptureMenu.beforeFrame();
		Burst.beforeFrame();
		// Last: it asks whether this frame will be read back, which every call above can change —
		// BigScreenshot moves into CAPTURING here, CaptureMenu marks the frame it fires from, and
		// Burst settles whether this frame is one of its slots.
		// HUD extraction comes later in this same frame, before the blit that reads it.
		CaptureStatus.beforeFrame();
	}

	/**
	 * The present is no longer Framebuffer.blitToScreen but a blit of the main render
	 * target's texture onto the window's swapchain surface. It is called exactly once in
	 * the method, so this resolves unambiguously — worth re-checking on a version bump,
	 * since a second call site would silently inject into both rather than failing.
	 */
	@Inject(method = "renderFrame(Z)V",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
	private void voxelcam$beforeBlit(boolean tick, CallbackInfo ci) {
		BigScreenshot.beforeBlit();
		// After BigScreenshot: a menu selection that starts an oversized capture must not land in
		// the same blit an oversized readback is already being issued from.
		CaptureMenu.beforeBlit();
		// After CaptureMenu, unlike BigScreenshot: a burst has no window to resize, and the frame
		// CaptureMenu released into is already finished and menu-free — so the key frame is taken
		// on the very blit a plain screenshot would have used, not one later.
		Burst.beforeBlit();
		// Last: an automatic capture is the only one nobody asked for, so it yields the frame to
		// every deliberate capture above it rather than competing for the same in-flight slot.
		AutoCapture.beforeBlit();
	}

	/**
	 * Nothing to do with a frame: this is where the manager's F2 rename shortcut gets its key.
	 * {@code KeyboardHandler.keyPress} offers every press to this method before the open screen
	 * and returns without calling {@code Screen.keyPressed} if it answers true, and vanilla's
	 * screenshot key, F2 by default, is one of the keys it answers true for. Answering false for
	 * the one press the manager claims lets vanilla carry on to the screen as it would for any
	 * other key.
	 *
	 * Here rather than in {@code KeyboardHandlerMixin}: skipping this one call from inside
	 * {@code keyPress} would take a redirect, which a second mod wanting the same call cannot
	 * share, where a cancellable inject at the head of the callee can.
	 */
	@Inject(method = "handleGlobalKeyPress(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)Z",
			at = @At("HEAD"), cancellable = true)
	private void voxelcam$leaveTheManagerItsShortcut(InputConstants.Key key, boolean controlDown,
			CallbackInfoReturnable<Boolean> cir) {
		if (Minecraft.getInstance().gui.screen() instanceof GuiScreenShotManager manager
				&& manager.claimsFromGlobalKeys(key)) {
			cir.setReturnValue(false);
		}
	}
}
