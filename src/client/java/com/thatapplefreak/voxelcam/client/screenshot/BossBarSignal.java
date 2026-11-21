package com.thatapplefreak.voxelcam.client.screenshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Turns a boss-bar packet into {@link BossBarTracker} calls, and a kill into an {@link AutoCapture}
 * moment.
 *
 * <p>Exists as its own class rather than living in {@code BossHealthOverlayMixin} for two reasons:
 * a mixin is a poor home for members that are not injections, and {@link BossBarTracker} is kept
 * free of Minecraft types so its kill-detection rule stays reachable from a no-client unit test.
 * This is the seam where the two meet — {@code Component} is flattened to a plain {@code String}
 * here, once, and the two facts the rule needs from the level are read here and handed over as a
 * {@code long} and a {@code boolean}.
 *
 * <p>{@code ClientboundBossEventPacket.Handler} is a public interface whose methods all have
 * defaults, so only the four operations that matter are overridden and the rest — style,
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
		AutoCapture.bossBars().added(id, name.getString(), progress, gameTicks());
	}

	@Override
	public void updateProgress(UUID id, float progress) {
		AutoCapture.bossBars().progressed(id, progress, gameTicks());
	}

	@Override
	public void updateName(UUID id, Component name) {
		AutoCapture.bossBars().renamed(id, name.getString());
	}

	@Override
	public void remove(UUID id) {
		BossBarTracker bars = AutoCapture.bossBars();
		BossBarTracker.Defeat defeat = bars.removed(id, gameTicks(), isDyingInLevel(bars.nameOf(id)));
		if (defeat != null) {
			AutoCapture.fire(MomentTrigger.BOSS_DEFEATED, defeat.name());
		}
	}

	/**
	 * The level's game time rather than a wall clock: the delays the tracker's window separates —
	 * the Ender Dragon's death animation, a raid's celebration — are counted in server ticks, so a
	 * lagging server or a paused game stretches them and a wall clock would not follow.
	 */
	private static long gameTicks() {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? 0L : level.getGameTime();
	}

	/**
	 * Whether the boss a bar belongs to is dying in the level right now. A bar's packets carry no
	 * link to an entity, so the owner is recognised the way a player recognises it: the bar is
	 * titled with the mob's name.
	 *
	 * <p>The entity is still here when its bar goes. {@code ServerEntity.removePairing} calls {@code
	 * stopSeenByPlayer}, which is where a mob takes its bar away from the player, before it sends
	 * the packet that removes the entity. And a bar removed for any other reason — out of range,
	 * despawned, cleared by the server — leaves nothing by that name dying.
	 */
	private static boolean isDyingInLevel(String barName) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || barName == null || barName.isBlank()) {
			return false;
		}
		for (Entity entity : level.entitiesForRendering()) {
			// Not a player: a server can title a bar with a player's name, and that player dying as
			// it is cleared is not a boss going down.
			if (entity instanceof LivingEntity living && !(living instanceof Player) && living.isDeadOrDying()
					&& (barName.equals(living.getName().getString())
							|| barName.equals(living.getDisplayName().getString()))) {
				return true;
			}
		}
		return false;
	}
}
