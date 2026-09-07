package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The whole point of the tracker is telling a kill from a bar that merely went away, and that rule
 * is reachable here because the tracker deliberately holds no Minecraft types — {@code
 * BossBarSignal} flattens the packet's {@code Component} name to a {@code String} before it
 * arrives.
 */
class BossBarTrackerTest {

	private static final UUID DRAGON = UUID.nameUUIDFromBytes("dragon".getBytes());
	private static final UUID WITHER = UUID.nameUUIDFromBytes("wither".getBytes());

	@Test
	void aBarWhoseProgressReachedZeroWasKilled() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, "Ender Dragon", 1.0F);
		tracker.progressed(DRAGON, 0.4F);
		tracker.progressed(DRAGON, 0.0F);

		BossBarTracker.Defeat defeat = tracker.removed(DRAGON);

		assertNotNull(defeat, "a bar that emptied and then disappeared is a kill");
		assertEquals("Ender Dragon", defeat.name());
	}

	/** The player walked out of range, or the server cleared the bar: the boss is alive and this is
	 * not a moment. */
	@Test
	void aBarThatDisappearsWithHealthLeftIsNotAKill() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(WITHER, "Wither", 1.0F);
		tracker.progressed(WITHER, 0.6F);

		assertNull(tracker.removed(WITHER));
	}

	/** Progress is a float the server derives from health, and a bar removed on the same tick as the
	 * killing blow can carry the last pre-death fraction rather than a clean zero. */
	@Test
	void aSliverOfProgressStillCountsAsAKill() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, "Ender Dragon", 1.0F);
		tracker.progressed(DRAGON, BossBarTracker.DEFEATED_PROGRESS);

		assertNotNull(tracker.removed(DRAGON));
	}

	@Test
	void aBarThatWasNeverTrackedIsNotAKill() {
		BossBarTracker tracker = new BossBarTracker();

		assertNull(tracker.removed(DRAGON), "no add was ever seen, so there is no progress to judge by");
	}

	/** Inventing an entry from a stray update would let a later removal read as a kill on the
	 * strength of one fraction, with no add ever having been seen. */
	@Test
	void anUpdateForAnUntrackedBarDoesNotCreateOne() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.progressed(DRAGON, 0.0F);

		assertNull(tracker.removed(DRAGON));
	}

	@Test
	void aKillWhoseAddWasMissedIsStillDistinguishableFromNoKill() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, null, 0.0F);

		BossBarTracker.Defeat defeat = tracker.removed(DRAGON);

		assertNotNull(defeat, "an unnamed kill is still a kill");
		assertNull(defeat.name());
	}

	/** Bars do not outlive a level, and one left at zero progress would read as a kill if its UUID
	 * ever recurred. */
	@Test
	void resetForgetsEveryBar() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, "Ender Dragon", 0.0F);

		tracker.reset();

		assertNull(tracker.removed(DRAGON));
	}

	@Test
	void barsAreTrackedIndependently() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, "Ender Dragon", 1.0F);
		tracker.added(WITHER, "Wither", 1.0F);
		tracker.progressed(WITHER, 0.0F);

		assertNull(tracker.removed(DRAGON), "the dragon is still at full health");
		assertNotNull(tracker.removed(WITHER));
	}
}
