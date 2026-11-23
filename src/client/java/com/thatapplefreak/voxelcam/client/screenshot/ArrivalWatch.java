package com.thatapplefreak.voxelcam.client.screenshot;

/**
 * Whether the dimension the player has just arrived in is in view yet, judged one tick at a time
 * from what the client can read for free. Vanilla closes the loading screen once the player's own
 * section is ready, not the world around it, and a portal's overlay is still fading out then, so
 * the first frame after an arrival is fog or purple. An automatic capture armed for the arrival
 * waits on this.
 *
 * <p>The measure is the renderer's visible-section count holding still. The occlusion graph adds
 * sections outward from the camera as their chunks arrive and are built, so the count climbs in
 * bursts and stops when the view has filled in. It was measured on arrivals by portal, by
 * teleport and through an end portal, at render distances 5, 12 and 16, on a software renderer.
 * After {@link #STEADY_TICKS} unchanged readings, 96% or more of the count ten seconds later was
 * already drawn, in all but one arrival. The frame then differed from that later view by 0.4 to 5
 * per channel, against 37 to 100 for the first frame vanilla shows. The one exception was a
 * fresh Nether at 16, with 75% of the count drawn, the rest a trickle of distant chunks still
 * being generated. Two readings look like better signals and are not:
 * {@code LevelRenderer.expectedChunks()} never empties at render distance 5 and empties with a
 * third of the view drawn at 16, and {@code hasRenderedAllSections()} is true with nothing on
 * screen.
 *
 * <p>No Minecraft types, so the rule is reachable from the unit suite; {@code AutoCapture} feeds
 * it the readings.
 */
final class ArrivalWatch {

	/**
	 * Unchanged readings that count as the view having filled in. Shorter runs mistake a pause
	 * between two bursts of chunks for the end. Five did so twice in thirty arrivals, with between
	 * a third and a half of the view drawn.
	 */
	static final int STEADY_TICKS = 10;

	/**
	 * How far the player may walk from where they arrived before the view settles. Past it the
	 * arrival is over, and a frame taken later would be of somewhere they went afterwards — the
	 * player's own doing, so the moment is dropped like any other ADR-0008 obstruction of theirs.
	 * Horizontal only, since falling from where a teleport left them is not their doing.
	 */
	static final double WANDER_BLOCKS = 8.0;

	/**
	 * Ticks after the loading screen closes before a view that never holds still is given up on.
	 * The slowest arrival measured, a fresh Nether at render distance 16 on a software renderer,
	 * held still after 240. No arrival measured reached this; it is a backstop, not a schedule.
	 */
	static final int TIMEOUT_TICKS = 600;

	enum Progress {
		/** Not in view yet; a capture armed for the arrival waits. */
		ARRIVING,
		/** In view; a capture armed for it can be taken. */
		IN_VIEW,
		/** Over without coming into view; a capture armed for it is dropped. */
		LOST
	}

	private int lastVisible = -1;
	private int steadyTicks;
	private boolean hasOrigin;
	private double originX;
	private double originZ;

	/**
	 * One tick's readings.
	 *
	 * @param ticksSinceScreenClosed 0 while the loading screen is up, then counting from 1
	 * @param visibleSections the renderer's visible-section count
	 * @param portalOverlay the portal overlay's strength, 0 once it has faded
	 * @param x where the player is; the first reading with the screen closed is where they arrived
	 */
	Progress observe(int ticksSinceScreenClosed, int visibleSections, float portalOverlay, double x, double z) {
		if (ticksSinceScreenClosed >= TIMEOUT_TICKS) {
			return Progress.LOST;
		}
		if (ticksSinceScreenClosed >= 1) {
			if (!hasOrigin) {
				hasOrigin = true;
				originX = x;
				originZ = z;
			}
			double dx = x - originX;
			double dz = z - originZ;
			if (dx * dx + dz * dz > WANDER_BLOCKS * WANDER_BLOCKS) {
				return Progress.LOST;
			}
		}
		// The first tick in a new level can still read the renderer of the level just left: on a
		// teleport out of a quiet overworld it reports that view's count, the arrival's own count
		// starting at 0 a tick later. Nothing read before the second tick counts towards steady.
		if (ticksSinceScreenClosed < 2) {
			lastVisible = -1;
			steadyTicks = 0;
			return Progress.ARRIVING;
		}
		if (visibleSections > 0 && visibleSections == lastVisible) {
			steadyTicks++;
		} else {
			steadyTicks = 0;
		}
		lastVisible = visibleSections;
		return steadyTicks >= STEADY_TICKS && portalOverlay <= 0.0F ? Progress.IN_VIEW : Progress.ARRIVING;
	}
}
