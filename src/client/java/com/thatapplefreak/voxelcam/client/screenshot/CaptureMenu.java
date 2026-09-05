package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.util.ChatMessages;
import net.minecraft.client.Minecraft;

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
 * {@code Screen} open/close path uses internally. That pairing could not be confirmed against the
 * 26.2 client jar from this environment (no network access to fetch one) — verify with
 * {@code javap -p net.minecraft.client.MouseHandler} before relying on it, per this repo's own
 * documented practice for exactly this situation.
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

	// Tick-thread state only: onKeyDown/onKeyUp/tick all run from VoxelCamClient's end-tick hook.
	private static State state = State.IDLE;
	private static int ticksHeld;

	// Live aim, as an offset from the menu's anchor point. Set wholesale each frame by whatever
	// reads the cursor (CaptureMenuHud) rather than accumulated, so there is nothing to drift or
	// to reset on open.
	private static double aimDx;
	private static double aimDy;

	private static boolean cursorWasGrabbed;

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

	/** Called once when the capture-menu key transitions from down to up. */
	public static void onKeyUp() {
		switch (state) {
			case ARMED -> {
				state = State.IDLE;
				ScreenshotHandler.captureNow();
			}
			case OPEN -> {
				Mode mode = aimedMode();
				restoreCursor(Minecraft.getInstance());
				state = State.IDLE;
				fire(mode);
			}
			case IDLE -> {
				// Defensive: a stray key-up with nothing armed must not throw.
			}
		}
	}

	/**
	 * Drops back to idle without firing anything, restoring the cursor if it had been freed.
	 * Used when a screen opens (Escape, disconnect, inventory…) while the menu is open.
	 */
	public static void abort() {
		if (state == State.OPEN) {
			restoreCursor(Minecraft.getInstance());
		}
		state = State.IDLE;
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
	}

	private static void restoreCursor(Minecraft client) {
		if (cursorWasGrabbed) {
			client.mouseHandler.grabMouse();
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
	 * The mode a given offset from the menu's anchor selects. Dead centre (both zero, e.g. the
	 * mouse never moved) resolves to the first mode rather than throwing — releasing at the
	 * anchor should degrade to "just take a normal screenshot," never to doing nothing.
	 */
	static Mode modeForOffset(double dx, double dy) {
		Mode[] modes = Mode.values();
		if (dx == 0 && dy == 0) {
			return modes[0];
		}
		// atan2(dx, -dy): 0 when the offset points straight up (dy negative), clockwise positive.
		double angle = Math.atan2(dx, -dy);
		return modes[wedgeForAngle(angle, modes.length)];
	}
}
