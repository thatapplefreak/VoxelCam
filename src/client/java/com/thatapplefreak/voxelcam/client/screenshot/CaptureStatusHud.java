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
			extractRow(graphics, client, y, Component.translatable(BUSY_KEY), ACCENT_BUSY, TEXT, 1F);
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
