package com.thatapplefreak.voxelcam.client.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The advancement a toast is announcing. {@code AdvancementToast.advancement} is private with no
 * getter, so reading it needs an accessor — the same pattern {@link KeyMappingAccessor} uses.
 *
 * <p>{@code ToastManagerMixin} needs it to name the moment it captures: without this the trigger
 * still fires, but the screenshot and its chat line could only say "an advancement" rather than
 * which one.
 */
@Mixin(AdvancementToast.class)
public interface AdvancementToastAccessor {

	@Accessor("advancement")
	AdvancementHolder getAdvancement();
}
