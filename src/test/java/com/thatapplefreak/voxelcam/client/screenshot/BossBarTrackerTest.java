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
 * arrives, and passes in the game time and whether the bar's owner is dying.
 *
 * <p>The sequences below are the ones vanilla 26.3 sends, read from its bytecode. Times are game
 * ticks, twenty to the second, because that is the unit vanilla's own delays are written in.
 */
class BossBarTrackerTest {

	private static final UUID DRAGON = UUID.nameUUIDFromBytes("dragon".getBytes());
	private static final UUID WITHER = UUID.nameUUIDFromBytes("wither".getBytes());
	private static final UUID RAID = UUID.nameUUIDFromBytes("raid".getBytes());

	/** An arbitrary game time for a fight to start at. */
	private static final long START = 48_000L;

	private static long seconds(int seconds) {
		return seconds * 20L;
	}

	// --- a bar that emptied -----------------------------------------------------------------------

	@Test
	void aBarWhoseProgressReachedZeroWasKilled() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, "Ender Dragon", 1.0F, START);
		tracker.progressed(DRAGON, 0.4F, START + seconds(60));
		tracker.progressed(DRAGON, 0.0F, START + seconds(90));

		BossBarTracker.Defeat defeat = tracker.removed(DRAGON, START + seconds(90), false);

		assertNotNull(defeat, "a bar that emptied and then disappeared is a kill");
		assertEquals("Ender Dragon", defeat.name());
	}

	/**
	 * The Ender Dragon, as vanilla plays it out. {@code handleKillingBlow} leaves it on 1 health of
	 * 200 while it flies to the podium, {@code tickDeath} then holds the bar at zero for 200 ticks,
	 * and only then does {@code EnderDragonFight.setDragonKilled} take the bar away. The dragon
	 * itself can be out of the client's entity range the whole time, so nothing here says its owner
	 * was seen dying.
	 */
	@Test
	void theEnderDragonsDeathIsAKill() {
		BossBarTracker tracker = new BossBarTracker();
		long killingBlow = START + seconds(300);
		long reachedThePodium = killingBlow + seconds(8);
		tracker.added(DRAGON, "Ender Dragon", 1.0F, START);
		tracker.progressed(DRAGON, 0.06F, START + seconds(280));
		tracker.progressed(DRAGON, 1.0F / 200.0F, killingBlow);
		tracker.progressed(DRAGON, 0.0F, reachedThePodium);

		BossBarTracker.Defeat defeat = tracker.removed(DRAGON, reachedThePodium + 200, false);

		assertNotNull(defeat, "the death animation's ten seconds at zero must not read as a bar that went stale");
		assertEquals("Ender Dragon", defeat.name());
	}

	/**
	 * The window runs from the bar's last change, not from when it first got low. A dragon left on
	 * 3 health of 200 is already under the threshold, and the final blow can be a minute away: timed
	 * from the first low reading, most fights that end that way would be over the window before the
	 * bar goes.
	 */
	@Test
	void aDragonLeftOnASliverLongBeforeTheFinalBlowIsStillAKill() {
		BossBarTracker tracker = new BossBarTracker();
		long leftOnASliver = START + seconds(240);
		long killingBlow = leftOnASliver + seconds(60);
		long reachedThePodium = killingBlow + seconds(8);
		tracker.added(DRAGON, "Ender Dragon", 1.0F, START);
		tracker.progressed(DRAGON, 3.0F / 200.0F, leftOnASliver);
		tracker.progressed(DRAGON, 1.0F / 200.0F, killingBlow);
		tracker.progressed(DRAGON, 0.0F, reachedThePodium);

		assertNotNull(tracker.removed(DRAGON, reachedThePodium + 200, false));
	}

	/** Progress is a float the server derives from health, and a bar removed on the same tick as the
	 * killing blow can carry the last pre-death fraction rather than a clean zero. */
	@Test
	void aSliverOfProgressStillCountsAsAKill() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, "Ender Dragon", 1.0F, START);
		tracker.progressed(DRAGON, BossBarTracker.DEFEATED_PROGRESS, START + seconds(90));

		assertNotNull(tracker.removed(DRAGON, START + seconds(90), false));
	}

	// --- a bar that emptied long ago --------------------------------------------------------------

	/**
	 * A raid, as vanilla plays it out. The bar empties when the last raider dies. {@code Raid.tick}
	 * then celebrates for 600 ticks, setting the progress to zero again every 20 of them and the
	 * name to the victory line, before {@code stop()} removes the bar. Thirty seconds after the win
	 * the player is trading or walking home, and that is not the moment.
	 */
	@Test
	void aRaidBarRemovedThirtySecondsAfterItEmptiedIsNotAKill() {
		BossBarTracker tracker = new BossBarTracker();
		long lastRaiderDied = START + seconds(200);
		tracker.added(RAID, "Raid", 0.0F, START);
		tracker.progressed(RAID, 1.0F, START + seconds(15));
		tracker.progressed(RAID, 0.25F, START + seconds(190));
		tracker.progressed(RAID, 0.0F, lastRaiderDied);
		tracker.renamed(RAID, "Raid - Victory");
		// The server only sends a progress that differs from the last, so these never arrive. Fed
		// anyway: a repeat of the same value must not start the window again.
		for (long tick = lastRaiderDied + 20; tick < lastRaiderDied + 600; tick += 20) {
			tracker.progressed(RAID, 0.0F, tick);
		}

		assertNull(tracker.removed(RAID, lastRaiderDied + 600, false),
				"a bar that has sat empty for thirty seconds did not just lose a boss");
	}

	/** The two vanilla delays the window has to fall between: the dragon's 200 ticks at zero are
	 * inside it, the raid's 600 are outside. */
	@Test
	void theWindowSeparatesADeathAnimationFromACelebration() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, "Ender Dragon", 1.0F, START);
		tracker.progressed(DRAGON, 0.0F, START + seconds(90));
		tracker.added(RAID, "Raid", 1.0F, START);
		tracker.progressed(RAID, 0.0F, START + seconds(90));

		assertNotNull(tracker.removed(DRAGON, START + seconds(90) + seconds(10), false));
		assertNull(tracker.removed(RAID, START + seconds(90) + seconds(30), false));
	}

	@Test
	void theWindowEndsAtItsLastTick() {
		BossBarTracker tracker = new BossBarTracker();
		long emptied = START + seconds(90);
		tracker.added(DRAGON, "Ender Dragon", 1.0F, START);
		tracker.progressed(DRAGON, 0.0F, emptied);
		tracker.added(RAID, "Raid", 1.0F, START);
		tracker.progressed(RAID, 0.0F, emptied);

		assertNotNull(tracker.removed(DRAGON, emptied + BossBarTracker.DEFEATED_WINDOW_TICKS, false));
		assertNull(tracker.removed(RAID, emptied + BossBarTracker.DEFEATED_WINDOW_TICKS + 1, false));
	}

	// --- a bar that never emptied -----------------------------------------------------------------

	/**
	 * The Wither, as vanilla plays it out. Its bar is written from {@code customServerAiStep}, which
	 * a dying mob no longer runs, so the bar keeps the fraction from before the killing blow and is
	 * removed with it 20 ticks later. A blow that lands on 120 health of 300 leaves the bar at 0.4.
	 * What says it was a kill is the Wither itself, dying in the client's level when its bar goes.
	 */
	@Test
	void aWitherKilledFromWellAboveSixHealthIsAKill() {
		BossBarTracker tracker = new BossBarTracker();
		long killingBlow = START + seconds(120);
		tracker.added(WITHER, "Wither", 1.0F, START);
		tracker.progressed(WITHER, 0.4F, killingBlow - seconds(2));

		BossBarTracker.Defeat defeat = tracker.removed(WITHER, killingBlow + 20, true);

		assertNotNull(defeat, "a boss seen dying as its bar goes was killed, whatever the bar last said");
		assertEquals("Wither", defeat.name());
	}

	/** The player walked out of range, or the server cleared the bar: the boss is alive and this is
	 * not a moment. The same bar as the Wither's above, with nothing dying. */
	@Test
	void aBarThePlayerWalkedAwayFromIsNotAKill() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(WITHER, "Wither", 1.0F, START);
		tracker.progressed(WITHER, 0.4F, START + seconds(118));

		assertNull(tracker.removed(WITHER, START + seconds(121), false));
	}

	// --- bars that were never seen ----------------------------------------------------------------

	@Test
	void aBarThatWasNeverTrackedIsNotAKill() {
		BossBarTracker tracker = new BossBarTracker();

		assertNull(tracker.removed(DRAGON, START, false), "no add was ever seen, so there is no progress to judge by");
		assertNull(tracker.removed(WITHER, START, true), "and something dying nearby does not make it one");
	}

	/** Inventing an entry from a stray update would let a later removal read as a kill on the
	 * strength of one fraction, with no add ever having been seen. */
	@Test
	void anUpdateForAnUntrackedBarDoesNotCreateOne() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.progressed(DRAGON, 0.0F, START);
		tracker.renamed(DRAGON, "Ender Dragon");

		assertNull(tracker.nameOf(DRAGON));
		assertNull(tracker.removed(DRAGON, START, false));
	}

	@Test
	void aKillWhoseAddWasMissedIsStillDistinguishableFromNoKill() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, null, 0.0F, START);

		BossBarTracker.Defeat defeat = tracker.removed(DRAGON, START, false);

		assertNotNull(defeat, "an unnamed kill is still a kill");
		assertNull(defeat.name());
	}

	/** Bars do not outlive a level, and one left at zero progress would read as a kill if its UUID
	 * ever recurred. */
	@Test
	void resetForgetsEveryBar() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, "Ender Dragon", 0.0F, START);

		tracker.reset();

		assertNull(tracker.removed(DRAGON, START, false));
	}

	@Test
	void barsAreTrackedIndependently() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(DRAGON, "Ender Dragon", 1.0F, START);
		tracker.added(WITHER, "Wither", 1.0F, START);
		tracker.progressed(DRAGON, 0.0F, START + seconds(90));

		assertNull(tracker.removed(WITHER, START + seconds(90), false), "the wither is still at full health");
		assertNotNull(tracker.removed(DRAGON, START + seconds(90), false));
	}

	// --- names ------------------------------------------------------------------------------------

	/** A name tag changes a boss's bar after it was added, and the name is both what the screenshot
	 * is tagged with and what the bar's owner is recognised by. */
	@Test
	void aRenamedBarIsKnownByItsNewName() {
		BossBarTracker tracker = new BossBarTracker();
		tracker.added(WITHER, "Wither", 1.0F, START);

		tracker.renamed(WITHER, "Gerald");

		assertEquals("Gerald", tracker.nameOf(WITHER));
		assertEquals("Gerald", tracker.removed(WITHER, START + seconds(30), true).name());
	}
}
