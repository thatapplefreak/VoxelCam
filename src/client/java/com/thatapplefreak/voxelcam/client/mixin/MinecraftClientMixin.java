package com.thatapplefreak.voxelcam.client.mixin;

import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two points an oversized capture needs inside a frame: before anything is drawn,
 * where the framebuffer can still be resized, and after the frame is finished but before
 * it is presented, where it can be read back.
 */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {

	@Inject(method = "runTick(Z)V", at = @At("HEAD"))
	private void voxelcam$beforeFrame(boolean tick, CallbackInfo ci) {
		BigScreenshot.beforeFrame();
	}

	/**
	 * blitToScreen is called exactly once in the class, so this resolves unambiguously —
	 * worth re-checking on a version bump, since a second call site would silently inject
	 * into both rather than failing.
	 */
	@Inject(method = "runTick(Z)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V"))
	private void voxelcam$beforeBlit(boolean tick, CallbackInfo ci) {
		BigScreenshot.beforeBlit();
	}
}
