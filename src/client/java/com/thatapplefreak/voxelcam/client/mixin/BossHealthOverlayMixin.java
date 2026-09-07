package com.thatapplefreak.voxelcam.client.mixin;

import com.thatapplefreak.voxelcam.client.screenshot.AutoCapture;
import com.thatapplefreak.voxelcam.client.screenshot.BossBarSignal;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where a boss dying becomes a capture. Every boss-bar packet is handed to {@link BossBarSignal},
 * which keeps just enough state to tell a kill from a bar that went away for some other reason.
 *
 * <p><strong>{@code HEAD}, not the more obvious {@code TAIL}.</strong> The moment worth capturing
 * is the <em>removal</em>, and whether it was a kill depends on the bar's progress just before it
 * vanished. Vanilla drops the entry — and that progress with it — while handling the packet, so
 * running afterwards would leave nothing to judge by. Injecting first is what lets the tracker's
 * own copy still hold the final value when {@code remove} arrives.
 */
@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

	@Inject(method = "update(Lnet/minecraft/network/protocol/game/ClientboundBossEventPacket;)V", at = @At("HEAD"))
	private void voxelcam$trackBossBars(ClientboundBossEventPacket packet, CallbackInfo ci) {
		BossBarSignal.observe(packet);
	}

	/** Vanilla clears its bars on a level change; the tracker's copy has to go with them, or a bar
	 * left at zero progress from the last world would read as a kill the next time that UUID recurs. */
	@Inject(method = "reset()V", at = @At("HEAD"))
	private void voxelcam$resetBossBars(CallbackInfo ci) {
		AutoCapture.bossBars().reset();
	}
}
