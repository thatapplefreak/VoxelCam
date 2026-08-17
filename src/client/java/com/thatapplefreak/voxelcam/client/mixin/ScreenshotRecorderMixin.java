package com.thatapplefreak.voxelcam.client.mixin;

import com.thatapplefreak.voxelcam.client.screenshot.ScreenshotHandler;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

/**
 * Intercepts vanilla's screenshot key handling so VoxelCam can take over
 * saving (and, later, uploading) instead of the default screenshots folder
 * behaviour. Replaces LiteLoader's ScreenshotListener callback.
 */
@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin {

	@Inject(method = "saveScreenshot(Ljava/io/File;Lnet/minecraft/client/gl/Framebuffer;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
	private static void voxelcam$onSaveScreenshot(File gameDirectory, Framebuffer framebuffer, Consumer<Text> messageReceiver, CallbackInfo ci) {
		if (ScreenshotHandler.onScreenshotKeyPressed(framebuffer)) {
			ci.cancel();
		}
	}
}
