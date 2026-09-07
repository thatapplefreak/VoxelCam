package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;

import java.util.HashSet;
import java.util.Set;

/**
 * The one capture path nobody pressed a key for: a single plain-size screenshot armed by a
 * {@link MomentTrigger} and taken on the next presented frame.
 *
 * <p>Every trigger does the same three things — notice a signal, ask whether it is allowed, arm the
 * next frame — and only the noticing differs. That is deliberate, and it is what keeps the
 * "one integration point per trigger type" cost this feature was warned about from becoming one
 * mechanism per trigger type: {@link #fire} is the single entry point, and a hook is a call site
 * rather than a subsystem. Two of the four arrive from mixins ({@code ToastManagerMixin},
 * {@code BossHealthOverlayMixin}); the other two are watches in {@link #tick} and need no mixin
 * at all.
 *
 * <p>Shaped like {@link Burst}: render-thread static state, pure package-private seams beside the
 * parts that need a live client, and the capture itself issued from {@code beforeBlit} where the
 * frame is finished and menu-free.
 *
 * <p><strong>The gate is the load-bearing part.</strong> An automatic capture that fires at the
 * wrong moment is worse than one that never fires — a screenshot of the inventory screen the player
 * opened ten seconds later is not the moment, and a feature that produces those is one nobody
 * leaves switched on. So a pending capture is <em>dropped</em> when the reason it cannot shoot is
 * the player's own doing, and only <em>held</em> when the obstruction will clear on its own. See
 * {@link #gateFor}.
 */
public final class AutoCapture {

	public static final int DEFAULT_COOLDOWN_SECONDS = 10;
	public static final int MIN_COOLDOWN_SECONDS = 1;
	public static final int MAX_COOLDOWN_SECONDS = 60;

	/**
	 * Frames a pending capture may wait before giving up. Only reached by a trigger the gate holds
	 * rather than shoots on sight, and never advanced under a level-loading screen (see {@link
	 * #advancePending}), so in practice this is a backstop against leaking a pending capture across
	 * a whole session rather than a schedule anything normally runs against.
	 */
	static final int PENDING_TIMEOUT_FRAMES = 200;

	/**
	 * {@link MomentTrigger#DEATH} waits for a screen instead of waiting for one to close, so its
	 * budget is the tighter question of whether a packet is coming at all — see {@link #gateFor}.
	 */
	static final int DEATH_PENDING_TIMEOUT_FRAMES = 100;

	/** Sentinel for "nothing has armed yet", rather than a time far enough in the past to subtract
	 * from safely — {@code Util.getMillis()} is monotonic from an arbitrary origin, so an actual
	 * subtraction against {@code Long.MIN_VALUE} would overflow. */
	private static final long NEVER = Long.MIN_VALUE;

	private enum State {
		IDLE,
		PENDING
	}

	/**
	 * What is on screen, reduced to the only four cases the gate distinguishes. A separate type
	 * from {@code Screen} so {@link #gateFor} is a pure function a no-client unit test can reach —
	 * the same reason {@code ScreenshotHandler.writeOrDiscard} takes a writer rather than an image.
	 */
	enum ScreenKind {
		NONE,
		LEVEL_LOADING,
		DEATH,
		OTHER
	}

	enum Gate {
		CAPTURE,
		WAIT,
		DROP
	}

	// Render-thread state only: fire() arrives from a mixin or from tick(), both on the client
	// thread, and everything else runs from beforeBlit().
	private static State state = State.IDLE;
	private static MomentTrigger pendingTrigger;
	private static String pendingSubject;
	private static int pendingFrames;

	/** When the last capture was armed, for the cooldown. Reset to {@link #NEVER} whenever an armed
	 * capture ends up not being taken, so a moment that never became a screenshot does not spend the
	 * window a later one could have used. */
	private static long lastArmMillis = NEVER;

	/** Cleared on disconnect. The first entry is the dimension the player logged in to, which is
	 * recorded but never fired on — see {@link #watchDimension}. */
	private static final Set<String> visitedDimensions = new HashSet<>();

	/** Latches the health watch so a death fires once, not on every tick the player spends dead. */
	private static boolean wasAlive;

	private static final BossBarTracker BOSS_BARS = new BossBarTracker();

	private AutoCapture() {
	}

	public static BossBarTracker bossBars() {
		return BOSS_BARS;
	}

	/**
	 * The single entry point every trigger calls. Refuses silently — no chat, no toast: this is a
	 * capture the player did not ask for, and one that explains at length why it declined to happen
	 * is worse company than one that simply does not.
	 */
	public static void fire(MomentTrigger trigger, String subject) {
		long now = Util.getMillis();
		if (!shouldArm(trigger, now)) {
			return;
		}
		arm(trigger, subject, now);
	}

	/**
	 * Just before the frame is presented, after {@link Burst#beforeBlit()} for the same reason burst
	 * sits after the capture menu: the frame is finished and carries no menu of ours.
	 */
	public static void beforeBlit() {
		if (state != State.PENDING) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ScreenKind screen = screenKind(client.gui.screen());
		switch (gateFor(pendingTrigger, client.level != null, client.player != null, screen)) {
			case DROP -> dropPending();
			case WAIT -> {
				if (advancePending(screen)) {
					dropPending();
				}
			}
			case CAPTURE -> capture(client);
		}
	}

	/** The two triggers that need no mixin, plus nothing else: the pending countdown runs per frame,
	 * not per tick. */
	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			// Not forgetSession(): a respawn screen or a loading screen briefly has no player, and
			// dropping the visited dimensions there would fire again on the way back in.
			wasAlive = false;
			return;
		}
		watchDeath(player);
		watchDimension(client);
	}

	/** From {@code ClientPlayConnectionEvents.DISCONNECT}: everything here is scoped to one
	 * connection, and a pending capture outliving the world it was armed in has nothing left to
	 * shoot. */
	public static void forgetSession() {
		state = State.IDLE;
		pendingTrigger = null;
		pendingSubject = null;
		pendingFrames = 0;
		lastArmMillis = NEVER;
		visitedDimensions.clear();
		wasAlive = false;
		BOSS_BARS.reset();
	}

	// --- the watches ------------------------------------------------------------------------

	/**
	 * Health crossing zero is the earliest client-visible sign of a death, which is why this arms
	 * rather than captures: {@code DeathScreen} is opened from a server-driven packet a variable
	 * number of ticks later, so shooting here would be a coin flip between a clean frame and the
	 * red-overlay one. {@link #gateFor} is where that is made deterministic.
	 */
	private static void watchDeath(LocalPlayer player) {
		boolean alive = player.getHealth() > 0.0F;
		if (wasAlive && !alive) {
			fire(MomentTrigger.DEATH, null);
		}
		wasAlive = alive;
	}

	private static void watchDimension(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}
		String dimension = level.dimension().identifier().toString();
		if (markVisited(dimension)) {
			fire(MomentTrigger.NEW_DIMENSION, dimension);
		}
	}

	// --- pure seams, unit-testable with no client --------------------------------------------

	/**
	 * @return whether arriving in {@code dimension} is a moment. The first dimension of a session is
	 * the one the player logged in to rather than one they travelled to, so it is recorded — a later
	 * return to it is not a moment either — but never fires. That is what "first entry per dimension"
	 * buys over "once per world join": the Nether and the End fire, opening the game does not.
	 */
	static boolean markVisited(String dimension) {
		if (!visitedDimensions.add(dimension)) {
			return false;
		}
		return visitedDimensions.size() > 1;
	}

	public static int cooldownSeconds() {
		// Clamped on read rather than trusted: a hand-edited zero would turn an advancement cascade
		// into a screenshot per toast, which is the failure this whole window exists to prevent.
		return Math.clamp(VoxelCamConfig.current().autoCaptureCooldownSeconds,
				MIN_COOLDOWN_SECONDS, MAX_COOLDOWN_SECONDS);
	}

	/**
	 * The cooldown is global rather than per trigger: one automatic capture per window, whatever
	 * caused it. First trigger in a window wins and the rest are dropped — a chain of advancements a
	 * second apart is one moment to the player, not five screenshots.
	 */
	static boolean shouldArm(MomentTrigger trigger, long nowMillis) {
		if (!trigger.isEnabled()) {
			return false;
		}
		if (state != State.IDLE) {
			return false;
		}
		if (BigScreenshot.isBusy() || Burst.isBusy() || ScreenshotHandler.isSaving()) {
			return false;
		}
		return lastArmMillis == NEVER || nowMillis - lastArmMillis >= cooldownSeconds() * 1000L;
	}

	static void arm(MomentTrigger trigger, String subject, long nowMillis) {
		state = State.PENDING;
		pendingTrigger = trigger;
		pendingSubject = subject;
		pendingFrames = 0;
		lastArmMillis = nowMillis;
	}

	/**
	 * Drops an armed capture and hands the cooldown window back. Giving the window back is the
	 * point: charging it at arm time is what makes the first trigger in a burst win, but a capture
	 * that never actually happened has no business also suppressing the next one.
	 */
	static void dropPending() {
		finishPending();
		lastArmMillis = NEVER;
	}

	/** Clears the pending capture and leaves the cooldown charged — the path where a screenshot
	 * actually happened, as against {@link #dropPending()} where one did not. */
	static void finishPending() {
		state = State.IDLE;
		pendingTrigger = null;
		pendingSubject = null;
		pendingFrames = 0;
	}

	/**
	 * @return whether the pending capture has waited long enough to give up. Never advances under a
	 * level-loading screen: that screen is already bounded by its own completion, so counting frames
	 * against it only races chunk-load speed — and losing that race fails <em>silently</em>, with a
	 * cold Nether portal producing no screenshot and nothing said about why. A load that genuinely
	 * never finishes ends in a disconnect, which {@link #forgetSession()} handles.
	 */
	static boolean advancePending(ScreenKind screen) {
		if (screen == ScreenKind.LEVEL_LOADING) {
			return false;
		}
		int budget = pendingTrigger == MomentTrigger.DEATH
				? DEATH_PENDING_TIMEOUT_FRAMES
				: PENDING_TIMEOUT_FRAMES;
		return ++pendingFrames > budget;
	}

	/**
	 * Whether this frame is the moment, still a moment worth waiting for, or gone.
	 *
	 * <p>This is a <em>policy</em> gate, not the technical one {@link BigScreenshot} and {@link
	 * Burst} need. A window-size capture resizes nothing, so none of these states is unsafe to shoot
	 * in; the question is only whether the resulting screenshot would be of the thing that happened.
	 *
	 * <p>{@link MomentTrigger#DEATH} inverts two rows on purpose. Its signal — health crossing zero
	 * — arrives a variable number of ticks before {@code DeathScreen} does, because the screen comes
	 * from a server-driven packet. Shooting on the first frame with no screen up would therefore
	 * produce a clean frame or the red-overlay frame depending on latency: the same trigger, two
	 * different screenshots. Waiting <em>for</em> the death screen instead gives one consistent
	 * result every time, and {@code DeathScreen} draws the world behind itself rather than replacing
	 * it, so the moment is still in the shot.
	 */
	static Gate gateFor(MomentTrigger trigger, boolean hasLevel, boolean hasPlayer, ScreenKind screen) {
		if (!hasLevel || !hasPlayer) {
			return Gate.DROP;
		}
		return switch (screen) {
			case LEVEL_LOADING -> Gate.WAIT;
			case DEATH -> trigger == MomentTrigger.DEATH ? Gate.CAPTURE : Gate.DROP;
			case NONE -> trigger == MomentTrigger.DEATH ? Gate.WAIT : Gate.CAPTURE;
			// The player opened this one, and did so after the moment. Held rather than dropped, it
			// would shoot whatever they were looking at when they closed it.
			case OTHER -> Gate.DROP;
		};
	}

	/** Test-only reset: every field here is static and outlives any one test. */
	static void forgetAll() {
		forgetSession();
	}

	// --- client-facing helpers ----------------------------------------------------------------

	static ScreenKind screenKind(Screen screen) {
		if (screen == null) {
			return ScreenKind.NONE;
		}
		// LevelLoadingScreen is the dimension-change screen in 26.x — its own Reason enum carries
		// NETHER_PORTAL and END_PORTAL — and replaced the older ReceivingLevelScreen, which is gone.
		if (screen instanceof LevelLoadingScreen) {
			return ScreenKind.LEVEL_LOADING;
		}
		if (screen instanceof DeathScreen) {
			return ScreenKind.DEATH;
		}
		return ScreenKind.OTHER;
	}

	private static void capture(Minecraft client) {
		MomentTag tag = new MomentTag(pendingTrigger, pendingSubject);
		// Cleared before the capture is issued, not after: a refusal is not a reason to try the same
		// moment again on the next frame.
		finishPending();

		if (!ScreenshotHandler.captureMoment(client.gameRenderer.mainRenderTarget(), tag)) {
			VoxelCamClient.LOGGER.debug("Skipped an automatic {} capture: another save is in flight",
					tag.trigger().token());
			lastArmMillis = NEVER;
		}
	}
}
