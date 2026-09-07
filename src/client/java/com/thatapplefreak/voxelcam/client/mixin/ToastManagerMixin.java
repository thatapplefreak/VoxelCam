package com.thatapplefreak.voxelcam.client.mixin;

import com.thatapplefreak.voxelcam.client.screenshot.AutoCapture;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTrigger;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where an earned advancement becomes a capture. The toast is the hook rather than {@code
 * ClientAdvancements} itself because vanilla has already done the interesting filtering by the
 * time one is built: an advancement only toasts when its progress has just completed, its display
 * asks for a toast, and the packet was not the reset one — which is what keeps the whole
 * advancement list a server sends at login from firing a screenshot per entry on every join.
 *
 * <p>It is also what gives this trigger its reach. Nothing here names a mod or an advancement id,
 * so any content mod granting an advancement through the vanilla system arrives here on its own,
 * with no per-mod adapter.
 */
@Mixin(ToastManager.class)
public class ToastManagerMixin {

	@Inject(method = "addToast(Lnet/minecraft/client/gui/components/toasts/Toast;)V", at = @At("HEAD"))
	private void voxelcam$onAdvancementToast(Toast toast, CallbackInfo ci) {
		if (!(toast instanceof AdvancementToast advancementToast)) {
			return;
		}
		AdvancementHolder holder = ((AdvancementToastAccessor) advancementToast).getAdvancement();
		// The plain title, not Advancement.name(holder): that one is decorated for hovering, and its
		// formatting has no business in a Latin-1 PNG text chunk.
		String title = holder.value().display()
				.map(DisplayInfo::getTitle)
				.map(Component::getString)
				.orElse(null);
		AutoCapture.fire(MomentTrigger.ADVANCEMENT, title);
	}
}
