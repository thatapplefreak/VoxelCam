package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Draws {@link CaptureStatus}: the ambient busy line, then the newest notices, stacked down from
 * the top-left corner. That corner is the one vanilla leaves empty during play — chat is
 * bottom-left, toasts and effects are top-right, boss bars are top-centre, and the advancement
 * toast an automatic capture is announcing is exactly where a top-right notice would land.
 *
 * <p>Not a Fabric {@code HudElement} like {@code CaptureMenuHud}: those are extracted inside {@code
 * Hud}'s F1 branch, and F1 is the mode a player composes screenshots in. With chat gone this is the
 * only feedback there is, so {@code HudMixin} calls it from the tail of {@code
 * Hud.extractRenderState}, outside {@code isHidden}. It still stays off the level-loading screen,
 * which returns before the tail.
 */
public final class CaptureStatusHud {

	/** Public, like the row pitch, so the frame-contamination game test knows where to look. */
	public static final int MARGIN = 4;
	public static final int ROW_HEIGHT = 13;
	private static final int ROW_GAP = 2;
	private static final int PADDING = 5;
	private static final int ACCENT_WIDTH = 2;

	private static final int PLATE = 0xD0101010;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_FAILED = 0xFFFF9C9C;
	private static final int ACCENT_BUSY = 0xFFB0B0B0;
	private static final int ACCENT_SAVED = 0xFF5BC25B;
	private static final int ACCENT_FAILED = 0xFFE04848;
	private static final int ACCENT_REFUSED = 0xFFE0B040;

	/** The spinner's box, square, centred on the row. Odd, so it has a centre pixel to orbit. */
	private static final int SPINNER_SIZE = 9;
	private static final int SPINNER_DOT = 2;
	private static final int SPINNER_GAP = 4;
	/** Brightness of the lit dot and the trail behind it; everything further back sits at the last. */
	private static final int[] SPINNER_TRAIL_ALPHA = { 0xFF, 0xB0, 0x70, 0x38 };
	/** Top-left corner of each dot, clockwise from twelve o'clock, inside the spinner's box. */
	private static final int[][] SPINNER_DOTS = spinnerDots();

	private static final String BUSY_KEY = "voxelcam.hud.saving";
	private static final String BUSY_REFUSAL_KEY = "voxelcam.savingpleasewait";

	private CaptureStatusHud() {
	}

	public static void extractRenderState(GuiGraphicsExtractor graphics) {
		if (!VoxelCamConfig.current().captureStatusHud || CaptureStatus.isFrameCaptured()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		long now = Util.getMillis();
		int y = MARGIN;

		boolean busy = CaptureStatus.isBusyShowing();
		if (busy) {
			extractBusyRow(graphics, client, y, now);
			y += ROW_HEIGHT + ROW_GAP;
		}

		List<CaptureStatus.Notice> notices = CaptureStatus.visibleAt(now);
		for (CaptureStatus.Notice notice : notices) {
			// The busy line already says it; a "please wait" under "Saving…" says it twice.
			if (busy && notice.kind() == CaptureStatus.Kind.BUSY && BUSY_REFUSAL_KEY.equals(notice.key())) {
				continue;
			}
			extractRow(graphics, client, y, notice.text(), accentFor(notice.kind()),
					notice.kind() == CaptureStatus.Kind.FAILED ? TEXT_FAILED : TEXT, notice.opacityAt(now));
			y += ROW_HEIGHT + ROW_GAP;
		}
	}

	/** The notice row's plate and accent, with a spinner set in ahead of the text. */
	private static void extractBusyRow(GuiGraphicsExtractor graphics, Minecraft client, int y, long now) {
		Component text = Component.translatable(BUSY_KEY);
		int contentLeft = MARGIN + ACCENT_WIDTH + PADDING;
		int textLeft = contentLeft + SPINNER_SIZE + SPINNER_GAP;
		int right = Math.min(textLeft + client.font.width(text) + PADDING, graphics.guiWidth() - MARGIN);
		graphics.fill(MARGIN, y, right, y + ROW_HEIGHT, PLATE);
		graphics.fill(MARGIN, y, MARGIN + ACCENT_WIDTH, y + ROW_HEIGHT, ACCENT_BUSY);

		int top = y + (ROW_HEIGHT - SPINNER_SIZE) / 2;
		int lit = CaptureStatus.spinnerStepAt(now);
		for (int i = 0; i < SPINNER_DOTS.length; i++) {
			int behind = Math.floorMod(lit - i, SPINNER_DOTS.length);
			int alpha = SPINNER_TRAIL_ALPHA[Math.min(behind, SPINNER_TRAIL_ALPHA.length - 1)];
			int x = contentLeft + SPINNER_DOTS[i][0];
			int dotY = top + SPINNER_DOTS[i][1];
			graphics.fill(x, dotY, x + SPINNER_DOT, dotY + SPINNER_DOT, (alpha << 24) | (TEXT & 0x00FFFFFF));
		}

		graphics.text(client.font, text, textLeft, y + 3, TEXT, false);
	}

	/**
	 * Eight dots on a circle, laid out once. The extractor draws rectangles and nothing else (see
	 * {@code CaptureMenuHud}), so the spinner is dots whose brightness moves rather than a shape
	 * that turns.
	 */
	private static int[][] spinnerDots() {
		int count = CaptureStatus.SPINNER_STEPS;
		double centre = (SPINNER_SIZE - SPINNER_DOT) / 2.0;
		double radius = centre;
		int[][] dots = new int[count][];
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2 * i / count;
			dots[i] = new int[] {
					(int) Math.round(centre + Math.sin(angle) * radius),
					(int) Math.round(centre - Math.cos(angle) * radius) };
		}
		return dots;
	}

	private static void extractRow(GuiGraphicsExtractor graphics, Minecraft client, int y, Component text,
			int accent, int textColor, float opacity) {
		if (opacity <= 0F) {
			return;
		}
		int width = ACCENT_WIDTH + PADDING * 2 + client.font.width(text);
		// Clamped so a long file name on a narrow window runs to the edge rather than off it.
		int right = Math.min(MARGIN + width, graphics.guiWidth() - MARGIN);
		graphics.fill(MARGIN, y, right, y + ROW_HEIGHT, fade(PLATE, opacity));
		graphics.fill(MARGIN, y, MARGIN + ACCENT_WIDTH, y + ROW_HEIGHT, fade(accent, opacity));
		graphics.text(client.font, text, MARGIN + ACCENT_WIDTH + PADDING, y + 3, fade(textColor, opacity), false);
	}

	private static int accentFor(CaptureStatus.Kind kind) {
		return switch (kind) {
			case BUSY -> ACCENT_BUSY;
			case SAVED -> ACCENT_SAVED;
			case FAILED -> ACCENT_FAILED;
			case REFUSED -> ACCENT_REFUSED;
		};
	}

	/** Scales an ARGB colour's alpha; text below a few alpha bits is skipped by the font, so a
	 * fade bottoms out slightly above zero rather than flickering on its last frame. */
	private static int fade(int argb, float opacity) {
		int alpha = Math.max(8, Math.round(((argb >>> 24) & 0xFF) * opacity));
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}
}
