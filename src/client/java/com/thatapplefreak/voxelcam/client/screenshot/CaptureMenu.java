package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.util.ChatMessages;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Holding the screenshot key opens a mouse-aimed menu of capture modes instead of firing
 * immediately; releasing over a mode fires it. A tap that never crosses the hold threshold still
 * behaves exactly like the old instant screenshot — see {@link #onKeyUp()}.
 *
 * <p>Vanilla's own screenshot key fires its callback once, at key-down, with no way to tell a tap
 * from a hold at that instant — so the plain-capture action this used to trigger synchronously has
 * to move to release time instead, decided by this state machine's own tick-by-tick read of a
 * second, VoxelCam-owned {@code KeyMapping} bound to the same physical key (see
 * {@code VoxelCamClient}). {@code ScreenshotRecorderMixin} keeps unconditionally cancelling
 * vanilla's save either way, so vanilla never writes a file under its own naming scheme.
 *
 * <p>Freeing the mouse to aim the menu without opening a full {@code Screen} (a {@code Screen}
 * would rebuild widgets and reposition things — the wrong tool here) rides on
 * {@code MouseHandler.releaseMouse()}/{@code grabMouse()}, the same pair vanilla's own
 * {@code Screen} open/close path uses internally. Restoring is deliberately skipped while a screen
 * is open: vanilla re-grabs on its own when that screen closes, and grabbing underneath it would
 * capture the cursor on a menu the player is still using.
 */
public final class CaptureMenu {

	private enum State {
		IDLE,
		ARMED,
		OPEN
	}

	/** Registered capture modes, in menu order — order is wedge order. */
	public enum Mode {
		SCREENSHOT,
		BIG_SCREENSHOT
	}

	/**
	 * ~200ms at 20 tps: comfortably above a normal tap (80-120ms) so an ordinary screenshot never
	 * brushes it, short enough that summoning the menu does not feel sluggish. Retune here if
	 * manual testing says otherwise — kept as the one place that number lives.
	 */
	private static final int HOLD_THRESHOLD_TICKS = 4;

	/**
	 * Radius, in GUI pixels, within which no wedge is aimed at and the first mode wins. Freeing the
	 * cursor leaves it wherever it happens to sit, and with the wedges meeting at the centre a
	 * pixel of drift would otherwise pick a neighbour — so a hold the player never aimed takes an
	 * ordinary screenshot rather than whatever the cursor was nearest.
	 */
	private static final int DEAD_ZONE = 18;

	// Tick-thread state only: onKeyDown/onKeyUp/tick all run from VoxelCamClient's end-tick hook.
	private static State state = State.IDLE;
	private static int ticksHeld;

	// Live aim, as an offset from the menu's anchor point. Set wholesale each frame by whatever
	// reads the cursor (CaptureMenuHud) rather than accumulated, so there is nothing to drift or
	// to reset on open.
	private static double aimDx;
	private static double aimDy;

	private static boolean cursorWasGrabbed;

	// Set when a menu is cancelled with the key still held. Without it the very next tick sees the
	// key down and opens the menu straight back up, and cancelling would be impossible to do.
	private static boolean suppressUntilRelease;

	// A fired mode waits here for a frame that no longer has the menu in it — see beforeBlit().
	private static Mode pendingCapture;
	private static boolean pendingFrameStarted;

	private CaptureMenu() {
	}

	public static boolean isOpen() {
		return state == State.OPEN;
	}

	/** True while the key is doing anything at all — armed or fully open. */
	public static boolean isArmed() {
		return state == State.ARMED || state == State.OPEN;
	}

	/** Valid only while {@link #isOpen()}. */
	public static Mode aimedMode() {
		return modeForOffset(aimDx, aimDy);
	}

	/**
	 * Feeds the live cursor offset from the menu's anchor while the menu is open; a no-op
	 * otherwise, so a caller does not need to guard every frame itself.
	 */
	public static void setAimOffset(double dx, double dy) {
		if (state != State.OPEN) {
			return;
		}
		aimDx = dx;
		aimDy = dy;
	}

	/** Called once when the capture-menu key transitions from up to down. */
	public static void onKeyDown() {
		if (suppressUntilRelease) {
			return;
		}
		if (state != State.IDLE) {
			// A duplicate down without an intervening up shouldn't happen, but must not reset
			// ticksHeld and re-arm a menu that is already open.
			return;
		}
		if (ScreenshotHandler.isSaving() || BigScreenshot.isBusy()) {
			ChatMessages.send("voxelcam.savingpleasewait");
			return;
		}

		state = State.ARMED;
		ticksHeld = 0;
		aimDx = 0;
		aimDy = 0;
	}

	/** Called every tick while {@link #isArmed()}, from the same end-tick hook that edge-detects the key. */
	public static void tick() {
		if (state != State.ARMED) {
			return;
		}
		if (++ticksHeld < HOLD_THRESHOLD_TICKS) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() != null) {
			// A screen appeared between key-down and now (same "gate checked twice" lesson as
			// BigScreenshot.canCapture()/beginCapture()): drop back to idle rather than opening a
			// menu the player can no longer see or aim.
			state = State.IDLE;
			return;
		}

		state = State.OPEN;
		freeCursor(client);
	}

	/**
	 * Called whenever the capture-menu key is not held. Idle most of the time; this is also where
	 * a cancelled menu stops being suppressed, since letting go is what ends that interaction.
	 */
	public static void onKeyUp() {
		suppressUntilRelease = false;
		switch (state) {
			case ARMED -> {
				state = State.IDLE;
				queue(Mode.SCREENSHOT);
			}
			case OPEN -> {
				Mode mode = aimedMode();
				restoreCursor(Minecraft.getInstance());
				state = State.IDLE;
				queue(mode);
			}
			case IDLE -> {
				// Defensive: a stray key-up with nothing armed must not throw.
			}
		}
	}

	/** True from a release until the capture it asked for has actually been taken. */
	public static boolean isCapturePending() {
		return pendingCapture != null;
	}

	/**
	 * Head of {@code Minecraft.renderFrame}: marks that a frame has begun with the menu already
	 * closed, so what {@link #beforeBlit()} finds in the framebuffer cannot still have it in it.
	 */
	public static void beforeFrame() {
		if (pendingCapture != null) {
			pendingFrameStarted = true;
		}
	}

	/**
	 * Just before the frame is presented, the same point the oversized path reads back from.
	 *
	 * <p>The capture is deferred to here rather than taken when the key comes up because at that
	 * moment the framebuffer still holds the last frame drawn — the one with the menu on top of it.
	 * Waiting for a frame that started after the menu closed is what makes the screenshot the first
	 * clean frame instead of a picture of the menu.
	 */
	public static void beforeBlit() {
		if (pendingCapture == null || !pendingFrameStarted) {
			return;
		}
		Mode mode = pendingCapture;
		pendingCapture = null;
		pendingFrameStarted = false;
		fire(mode);
	}

	private static void queue(Mode mode) {
		pendingCapture = mode;
		pendingFrameStarted = false;
	}

	/**
	 * Drops a queued capture without taking it. Only the unit tests need this: they release the
	 * key without a client to render the frame that would consume it, and this state is static.
	 */
	static void discardPendingCapture() {
		pendingCapture = null;
		pendingFrameStarted = false;
		suppressUntilRelease = false;
	}

	/**
	 * Drops back to idle without firing anything, restoring the cursor if it had been freed — the
	 * cancel, whether it came from Escape or from a screen opening underneath the menu.
	 *
	 * <p>Nothing is captured, and nothing will be until the key is let go: the player is still
	 * holding it at this point, and re-opening on the next tick would make the menu impossible to
	 * back out of.
	 */
	public static void abort() {
		if (state == State.OPEN) {
			restoreCursor(Minecraft.getInstance());
		}
		state = State.IDLE;
		suppressUntilRelease = true;
	}

	private static void fire(Mode mode) {
		switch (mode) {
			case SCREENSHOT -> ScreenshotHandler.captureNow();
			case BIG_SCREENSHOT -> BigScreenshot.request();
		}
	}

	private static void freeCursor(Minecraft client) {
		cursorWasGrabbed = client.mouseHandler.isMouseGrabbed();
		if (cursorWasGrabbed) {
			client.mouseHandler.releaseMouse();
		}
		// releaseMouse leaves the system pointer sitting visible in the middle of the dial, which
		// is a second thing to track while aiming something that already shows its own selection.
		// Hidden, not disabled: disabled would hand the deltas back to mouse-look and turn the
		// camera under the menu.
		GLFW.glfwSetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
	}

	private static void restoreCursor(Minecraft client) {
		// A screen opening is what aborted the menu in the first place on that path; vanilla
		// re-grabs when it closes, so grabbing here would capture the cursor out from under it.
		if (cursorWasGrabbed && client.gui.screen() == null) {
			client.mouseHandler.grabMouse();
		} else {
			// Nothing else is going to undo the hide, and a screen that is up now needs a pointer.
			GLFW.glfwSetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
		}
		cursorWasGrabbed = false;
	}

	/**
	 * The wedge (0-based, matching {@link Mode#values()} order) that the given angle selects.
	 * Angle is radians, 0 pointing up, increasing clockwise — the HUD's "12 o'clock" convention.
	 * Pure geometry: no {@code Minecraft}, no GL, unit-testable directly.
	 */
	static int wedgeForAngle(double angleRadians, int wedgeCount) {
		if (wedgeCount <= 0) {
			throw new IllegalArgumentException("wedgeCount must be positive, was " + wedgeCount);
		}
		double twoPi = Math.PI * 2;
		double normalized = ((angleRadians % twoPi) + twoPi) % twoPi;
		double wedgeWidth = twoPi / wedgeCount;
		// Centre wedge 0 on angle 0 rather than starting a wedge boundary there.
		int index = (int) Math.floor((normalized + wedgeWidth / 2) / wedgeWidth) % wedgeCount;
		return index;
	}

	/**
	 * The mode a given offset from the menu's anchor selects. Anything inside {@link #DEAD_ZONE}
	 * resolves to the first mode rather than to whichever wedge the cursor happens to lean into —
	 * releasing without having aimed should degrade to "just take a normal screenshot," never to
	 * doing nothing and never to a mode the player did not choose.
	 */
	static Mode modeForOffset(double dx, double dy) {
		Mode[] modes = Mode.values();
		if (dx * dx + dy * dy < (double) DEAD_ZONE * DEAD_ZONE) {
			return modes[0];
		}
		// atan2(dx, -dy): 0 when the offset points straight up (dy negative), clockwise positive.
		double angle = Math.atan2(dx, -dy);
		return modes[wedgeForAngle(angle, modes.length)];
	}
}
