package com.thatapplefreak.voxelcam.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The physical key a binding is currently bound to. {@code KeyMapping.key} is not public and has
 * no getter, so reading it needs an accessor — the same one Fabric API's own client gametest input
 * uses to press a binding.
 *
 * <p>VoxelCam needs it to tell whether a {@code Screenshot.grab} was caused by its own capture-menu
 * key being held: asking GLFW for that key's raw state answers regardless of where in vanilla's
 * key handling the grab was raised, and resolving the key from the binding keeps the answer right
 * after a rebind. See {@code VoxelCamClient.isCaptureMenuKeyDown()}.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

	@Accessor("key")
	InputConstants.Key getKey();
}
