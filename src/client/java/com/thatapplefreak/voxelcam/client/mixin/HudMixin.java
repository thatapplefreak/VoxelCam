package com.thatapplefreak.voxelcam.client.mixin;

import com.thatapplefreak.voxelcam.client.screenshot.CaptureMenuHud;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureStatusHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the capture status HUD and the capture menu are drawn, rather than Fabric {@code
 * HudElementRegistry} entries: those are suppressed under F1, and F1 is where players compose
 * screenshots. With chat gone, a capture status hidden there would leave a several-second big
 * screenshot with no feedback at all. A capture menu hidden there still opens, but it cannot be seen
 * or aimed, because drawing it is also what reads the aim.
 *
 * <p>{@code TAIL} resolves to the method's final return only, after both {@code isHidden} branches.
 * The other return is the early one under a {@code LevelLoadingScreen}, which is the right place
 * for both to stay off too.
 */
@Mixin(Hud.class)
public class HudMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At("TAIL"))
	private void voxelcam$extractCaptureHuds(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
			CallbackInfo ci) {
		// The menu first, so a notice stays readable over it, as it was while the menu was a
		// Fabric element extracted ahead of this injection.
		CaptureMenuHud.extractRenderState(graphics);
		CaptureStatusHud.extractRenderState(graphics);
	}
}
