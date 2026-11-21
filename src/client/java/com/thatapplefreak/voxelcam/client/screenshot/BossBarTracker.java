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
 * <p>A removal is a kill on either of two grounds, because vanilla's own bosses do not agree on
 * what a dead boss's bar looks like:
 * <ul>
 *   <li><strong>The bar emptied, and recently.</strong> The Ender Dragon's bar is driven to zero
 *       and removed 200 ticks later, by {@code EnderDragonFight}, which can be out of the client's
 *       entity range. "Recently" is what keeps a raid out: its bar empties at the win and is not
 *       removed until the celebration ends, 600 ticks later, when the moment is long gone.</li>
 *   <li><strong>The bar's owner was dying when the bar went.</strong> The Wither stops updating
 *       its bar at the killing blow, so the bar is removed still showing whatever health the blow
 *       landed on. Nothing in the bar says it was a kill; the Wither dying in the level does.</li>
 * </ul>
 * A bar with health left and nothing dying is a boss the player walked away from.
 *
 * <p>Deliberately holds no Minecraft types: names arrive already flattened to {@code String}, the
 * clock and the dying owner arrive as a {@code long} and a {@code boolean}, which is what lets the
 * whole kill-detection rule be exercised by a no-client unit test.
 */
public final class BossBarTracker {

	/**
	 * A bar whose last progress was at or below this counts as emptied.
	 * Not exactly zero: progress is a float the server derives from the boss's health, and a bar
	 * removed on the same tick the killing blow lands can carry the last pre-death fraction rather
	 * than a clean 0. This is an allowance for rounding, not for a whole hit — a boss whose bar is
	 * removed above it is judged by whether it was seen dying instead.
	 */
	static final float DEFEATED_PROGRESS = 0.02F;

	/**
	 * How long after its last change an emptied bar's removal still reads as a kill, in game ticks.
	 * Between the two vanilla delays it has to separate, with the same margin either side: the
	 * Ender Dragon's bar sits at zero for the 200 ticks of its death animation, and a won raid's
	 * for the 600 ticks of its celebration.
	 *
	 * <p>Measured from the last <em>change</em>, not from when the bar first got low. A dragon left
	 * on 3 health of 200 is already under {@link #DEFEATED_PROGRESS} and may not take its final blow
	 * for a minute; that blow and its death still move the bar. A raid's bar does not move again
	 * once it is empty.
	 *
	 * <p><strong>Known gaps,</strong> both a raid's, both still taken for a kill:
	 * <ul>
	 *   <li>Leaving the raid's range within this window of winning it. The server removes the
	 *       player from the bar, and the bar emptied less than 400 ticks ago.</li>
	 *   <li>Arriving, or coming back into range, during the last 400 ticks of the celebration. The
	 *       window starts at any observation of the bar, and the first one is {@link #added}: a bar
	 *       that arrives already empty counts as having just emptied, and the celebration ends
	 *       inside the window.</li>
	 * </ul>
	 * The second would go if a bar first seen empty were not stamped until it changed. That is a
	 * change to what an add means here (a bar added at zero and then removed has always read as a
	 * kill, and a player who joins as the dragon dies relies on it), so it is left for a decision.
	 */
	static final long DEFEATED_WINDOW_TICKS = 400L;

	private static final class Bar {
		private String name;
		private float progress;
		private long changedAtTicks;
	}

	private final Map<UUID, Bar> bars = new HashMap<>();

	public void added(UUID id, String name, float initialProgress, long nowTicks) {
		Bar bar = new Bar();
		bar.name = name;
		bar.progress = initialProgress;
		bar.changedAtTicks = nowTicks;
		bars.put(id, bar);
	}

	public void progressed(UUID id, float current, long nowTicks) {
		// Only for bars already seen: an update for one that was never added means the add was
		// missed, and inventing an entry from it would let a later removal read as a kill on the
		// strength of a single fraction.
		Bar bar = bars.get(id);
		// A repeat of the same value is not a change. Vanilla's server only sends a progress that
		// differs from the last, but the window must not depend on every server doing the same: a
		// raid sets its empty bar to zero again every second of its celebration.
		if (bar != null && bar.progress != current) {
			bar.progress = current;
			bar.changedAtTicks = nowTicks;
		}
	}

	/** A name tag, or a raid turning into "Raid - Victory". Ignored for a bar never added, like a
	 * progress update is. */
	public void renamed(UUID id, String name) {
		Bar bar = bars.get(id);
		if (bar != null) {
			bar.name = name;
		}
	}

	/** The bar's current name, or null for a bar that is not tracked or was added without one. */
	public String nameOf(UUID id) {
		Bar bar = bars.get(id);
		return bar == null ? null : bar.name;
	}

	/** A kill, as distinct from a bar that went away. {@code name} may still be null — a bar added
	 * without a name whose progress reached zero is a kill nobody can name. */
	public record Defeat(String name) {
	}

	/**
	 * @param nowTicks the same clock {@link #added} and {@link #progressed} were given
	 * @param ownerDying whether the boss this bar belongs to was dying in the level as the bar went
	 * @return a {@link Defeat} if this removal reads as a kill, or null otherwise — including for a
	 * bar that was never tracked. A record rather than the name alone so that an unnamed kill is not
	 * indistinguishable from no kill at all.
	 */
	public Defeat removed(UUID id, long nowTicks, boolean ownerDying) {
		Bar bar = bars.remove(id);
		if (bar == null) {
			return null;
		}
		boolean emptiedRecently = bar.progress <= DEFEATED_PROGRESS
				&& nowTicks - bar.changedAtTicks <= DEFEATED_WINDOW_TICKS;
		return ownerDying || emptiedRecently ? new Defeat(bar.name) : null;
	}

	/** From {@code BossHealthOverlay.reset()} and from a disconnect: bars do not outlive a level. */
	public void reset() {
		bars.clear();
	}
}
