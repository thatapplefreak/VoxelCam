package com.thatapplefreak.voxelcam.client.mixin;

import com.thatapplefreak.voxelcam.client.screenshot.CaptureStatusHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the capture status HUD is drawn, rather than a Fabric {@code HudElementRegistry} entry:
 * those are suppressed under F1, and F1 is where players compose screenshots — with chat gone, a
 * HUD hidden there would leave a several-second big screenshot with no feedback at all.
 *
 * <p>{@code TAIL} resolves to the method's final return only, after both {@code isHidden} branches.
 * The other return is the early one under a {@code LevelLoadingScreen}, which is the right place
 * for this to stay off too.
 */
@Mixin(Hud.class)
public class HudMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At("TAIL"))
	private void voxelcam$extractCaptureStatus(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
			CallbackInfo ci) {
		CaptureStatusHud.extractRenderState(graphics);
	}
}
