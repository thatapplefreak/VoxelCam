package com.thatapplefreak.voxelcam.client.mixin;

import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenu;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two points an oversized capture needs inside a frame: before anything is drawn,
 * where the framebuffer can still be resized, and after the frame is finished but before
 * it is presented, where it can be read back.
 *
 * Both live in renderFrame rather than runTick: 26.x split the old single frame method,
 * leaving runTick with the game tick and renderFrame with everything from acquiring the
 * surface through presenting it.
 */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {

	@Inject(method = "renderFrame(Z)V", at = @At("HEAD"))
	private void voxelcam$beforeFrame(boolean tick, CallbackInfo ci) {
		BigScreenshot.beforeFrame();
		CaptureMenu.beforeFrame();
	}

	/**
	 * The present is no longer Framebuffer.blitToScreen but a blit of the main render
	 * target's texture onto the window's swapchain surface. It is called exactly once in
	 * the method, so this resolves unambiguously — worth re-checking on a version bump,
	 * since a second call site would silently inject into both rather than failing.
	 */
	@Inject(method = "renderFrame(Z)V",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"))
	private void voxelcam$beforeBlit(boolean tick, CallbackInfo ci) {
		BigScreenshot.beforeBlit();
		// After BigScreenshot: a menu selection that starts an oversized capture must not land in
		// the same blit an oversized readback is already being issued from.
		CaptureMenu.beforeBlit();
	}
}
