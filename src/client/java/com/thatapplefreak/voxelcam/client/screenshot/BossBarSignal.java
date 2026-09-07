package com.thatapplefreak.voxelcam.client.screenshot;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;

import java.util.UUID;

/**
 * Turns a boss-bar packet into {@link BossBarTracker} calls, and a kill into an {@link AutoCapture}
 * moment.
 *
 * <p>Exists as its own class rather than living in {@code BossHealthOverlayMixin} for two reasons:
 * a mixin is a poor home for members that are not injections, and {@link BossBarTracker} is kept
 * free of Minecraft types so its kill-detection rule stays reachable from a no-client unit test.
 * This is the seam where the two meet — {@code Component} is flattened to a plain {@code String}
 * here, once.
 *
 * <p>{@code ClientboundBossEventPacket.Handler} is a public interface whose methods all have
 * defaults, so only the three operations that matter are overridden and the rest — name, style,
 * properties — cost nothing to ignore.
 */
public final class BossBarSignal implements ClientboundBossEventPacket.Handler {

	private static final BossBarSignal INSTANCE = new BossBarSignal();

	private BossBarSignal() {
	}

	public static void observe(ClientboundBossEventPacket packet) {
		packet.dispatch(INSTANCE);
	}

	@Override
	public void add(UUID id, Component name, float progress, BossEvent.BossBarColor color,
			BossEvent.BossBarOverlay overlay, boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
		AutoCapture.bossBars().added(id, name.getString(), progress);
	}

	@Override
	public void updateProgress(UUID id, float progress) {
		AutoCapture.bossBars().progressed(id, progress);
	}

	@Override
	public void remove(UUID id) {
		BossBarTracker.Defeat defeat = AutoCapture.bossBars().removed(id);
		if (defeat != null) {
			AutoCapture.fire(MomentTrigger.BOSS_DEFEATED, defeat.name());
		}
	}
}
