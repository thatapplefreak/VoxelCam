package com.thatapplefreak.voxelcam.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thatapplefreak.voxelcam.client.screenshot.ScreenshotHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

/**
 * Intercepts vanilla's screenshot key handling so VoxelCam can take over
 * saving (and, later, uploading) instead of the default screenshots folder
 * behaviour. Replaces LiteLoader's ScreenshotListener callback.
 */
@Mixin(Screenshot.class)
public class ScreenshotRecorderMixin {

	@Inject(method = "grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
	private static void voxelcam$onSaveScreenshot(File gameDirectory, RenderTarget framebuffer, Consumer<Component> messageReceiver, CallbackInfo ci) {
		if (ScreenshotHandler.onScreenshotKeyPressed(framebuffer)) {
			ci.cancel();
		}
	}
}
