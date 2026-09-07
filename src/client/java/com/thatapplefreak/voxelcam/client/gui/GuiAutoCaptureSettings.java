package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import com.thatapplefreak.voxelcam.client.screenshot.AutoCapture;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTrigger;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Which moments arm a capture on their own, and how close together two of them may land.
 *
 * <p>Its own screen rather than four more rows on {@link GuiSettings}: that screen positions its
 * rows by hand off {@code height / 2} with no scrolling, and four toggles plus a stepper would put
 * it at the edge of the smallest GUI scale a player can pick. Keeping the automatic capture
 * settings together also gives the one feature that takes screenshots unbidden a single obvious
 * place to be switched off.
 *
 * <p>The toggles are {@link CycleButton}s rather than {@link net.minecraft.client.gui.components.Checkbox}es,
 * which is not only vanilla's own idiom for a boolean option. A {@code CycleButton} keeps its label
 * and its value separate — the button reads "Death: ON" while its <em>name</em> stays the plain
 * translation — and the client game test API can press one by that name. It cannot press a
 * checkbox at all: its button matcher handles {@code Button} and {@code CycleButton} and nothing
 * else, so a checkbox here would leave these settings with no clickable test at all. The related
 * limitation {@code SettingsScreenTest} documents for the big-screenshot size button is the other
 * half of the same rule — a plain {@code Button} whose label carries a substitution is equally
 * unfindable.
 */
public class GuiAutoCaptureSettings extends Screen {

	private static final int ROW_WIDTH = 200;
	private static final int ROW_HEIGHT = 20;
	private static final int STEP_BUTTON_SIZE = 20;
	private static final int ROW_GAP = 24;

	private final Screen parent;

	private Button cooldownMinusButton;
	private Button cooldownPlusButton;
	private int cooldownRowY;

	public GuiAutoCaptureSettings(Screen parent) {
		super(Component.translatable("voxelcam.autocapture.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		MomentTrigger[] triggers = MomentTrigger.values();
		// The toggles plus the cooldown row, centred as a block so adding a trigger later moves the
		// whole group rather than pushing Done off the bottom.
		int top = height / 2 - ((triggers.length + 1) * ROW_GAP) / 2;

		for (int i = 0; i < triggers.length; i++) {
			MomentTrigger trigger = triggers[i];
			CycleButton<Boolean> toggle = addRenderableWidget(
					CycleButton.onOffBuilder(trigger.isEnabled())
							.create(centerX - ROW_WIDTH / 2, top + i * ROW_GAP, ROW_WIDTH, ROW_HEIGHT,
									Component.translatable(trigger.labelKey()),
									(button, enabled) -> setTrigger(trigger, enabled)));
			toggle.setTooltip(Tooltip.create(Component.translatable(trigger.tooltipKey())));
		}

		cooldownRowY = top + triggers.length * ROW_GAP + 4;
		cooldownMinusButton = addRenderableWidget(
				Button.builder(Component.translatable("voxelcam.autocapture.cooldownminus"), b -> adjustCooldown(-1))
						.bounds(centerX - ROW_WIDTH / 2, cooldownRowY, STEP_BUTTON_SIZE, ROW_HEIGHT).build());
		cooldownMinusButton.setTooltip(Tooltip.create(Component.translatable("voxelcam.tooltip.cooldowndown")));

		cooldownPlusButton = addRenderableWidget(
				Button.builder(Component.translatable("voxelcam.autocapture.cooldownplus"), b -> adjustCooldown(1))
						.bounds(centerX + ROW_WIDTH / 2 - STEP_BUTTON_SIZE, cooldownRowY, STEP_BUTTON_SIZE, ROW_HEIGHT)
						.build());
		cooldownPlusButton.setTooltip(Tooltip.create(Component.translatable("voxelcam.tooltip.cooldownup")));

		addRenderableWidget(Button.builder(Component.translatable("voxelcam.done"), b -> onClose())
				.bounds(centerX - ROW_WIDTH / 2, cooldownRowY + ROW_GAP + 12, ROW_WIDTH, ROW_HEIGHT).build());

		updateCooldownButtons();
	}

	/** Saved on the spot, like every other setting changed at the player's hand — the capture menu's
	 * ring, the manager's sort button — rather than on close, which a crash or an Escape would lose. */
	private void setTrigger(MomentTrigger trigger, boolean enabled) {
		trigger.setEnabled(enabled);
		VoxelCamConfig.saveCurrent();
	}

	/** {@code AutoCapture.cooldownSeconds()} clamps on read anyway, but the buttons disable at the
	 * edges rather than leaving a click that silently does nothing — the same pairing {@code
	 * GuiSettings} keeps around {@code Burst.setLength}. */
	private void adjustCooldown(int delta) {
		VoxelCamConfig config = VoxelCamConfig.current();
		config.autoCaptureCooldownSeconds = Math.clamp(config.autoCaptureCooldownSeconds + delta,
				AutoCapture.MIN_COOLDOWN_SECONDS, AutoCapture.MAX_COOLDOWN_SECONDS);
		VoxelCamConfig.saveCurrent();
		updateCooldownButtons();
	}

	private void updateCooldownButtons() {
		int seconds = AutoCapture.cooldownSeconds();
		cooldownMinusButton.active = seconds > AutoCapture.MIN_COOLDOWN_SECONDS;
		cooldownPlusButton.active = seconds < AutoCapture.MAX_COOLDOWN_SECONDS;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);

		context.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
		context.centeredText(font,
				Component.translatable("voxelcam.autocapture.cooldown", AutoCapture.cooldownSeconds()),
				width / 2, cooldownRowY + 6, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
