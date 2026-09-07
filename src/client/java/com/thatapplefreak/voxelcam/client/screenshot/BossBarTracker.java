package com.thatapplefreak.voxelcam.client.screenshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enough of the boss-bar state to tell a kill from a walk-away. Fed by {@code
 * BossHealthOverlayMixin}, which dispatches each {@code ClientboundBossEventPacket} into the
 * matching method here.
 *
 * <p>It keeps its own copy of each bar's last known progress rather than reading vanilla's, for one
 * reason: the interesting moment is the <em>removal</em>, and by the time vanilla has handled that
 * packet the entry — and its final progress — is gone. That is also why the mixin injects at
 * {@code HEAD} rather than the more obvious {@code TAIL}.
 *
 * <p>Deliberately holds no Minecraft types: names arrive already flattened to {@code String}, which
 * is what lets the whole kill-detection rule be exercised by a no-client unit test.
 */
public final class BossBarTracker {

	/**
	 * A bar whose last progress was at or below this counts as defeated when it disappears.
	 * Not exactly zero: progress is a float the server derives from the boss's health, and a bar
	 * removed on the same tick the killing blow lands can carry the last pre-death fraction rather
	 * than a clean 0. Anything above this is a bar that went away for some other reason — the player
	 * walked out of range, the boss despawned, the server cleared it — and is not a moment.
	 */
	static final float DEFEATED_PROGRESS = 0.02F;

	private final Map<UUID, Float> progress = new HashMap<>();
	private final Map<UUID, String> names = new HashMap<>();

	public void added(UUID id, String name, float initialProgress) {
		progress.put(id, initialProgress);
		names.put(id, name);
	}

	public void progressed(UUID id, float current) {
		// Only for bars already seen: an update for one that was never added means the add was
		// missed, and inventing an entry from it would let a later removal read as a kill on the
		// strength of a single fraction.
		if (progress.containsKey(id)) {
			progress.put(id, current);
		}
	}

	/** A kill, as distinct from a bar that went away. {@code name} may still be null — a bar whose
	 * add was missed but whose progress reached zero is a kill nobody can name. */
	public record Defeat(String name) {
	}

	/**
	 * @return a {@link Defeat} if this removal reads as a kill, or null otherwise — including for a
	 * bar that was never tracked. A record rather than the name alone so that an unnamed kill is not
	 * indistinguishable from no kill at all.
	 */
	public Defeat removed(UUID id) {
		Float last = progress.remove(id);
		String name = names.remove(id);
		return wasDefeated(last) ? new Defeat(name) : null;
	}

	/** Whether a removal at this last known progress reads as a kill. Null — an untracked bar — never
	 * does. */
	static boolean wasDefeated(Float lastProgress) {
		return lastProgress != null && lastProgress <= DEFEATED_PROGRESS;
	}

	/** From {@code BossHealthOverlay.reset()} and from a disconnect: bars do not outlive a level. */
	public void reset() {
		progress.clear();
		names.clear();
	}
}
