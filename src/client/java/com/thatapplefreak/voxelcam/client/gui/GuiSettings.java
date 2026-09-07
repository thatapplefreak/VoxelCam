package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshot;
import com.thatapplefreak.voxelcam.client.screenshot.BigScreenshotSize;
import com.thatapplefreak.voxelcam.client.screenshot.Burst;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The handful of settings the capture menu's outer ring only offers a quick ladder for: burst
 * length across its full range rather than the ring's five-value ladder, and every {@code
 * BigScreenshotSize} preset rather than just the ring's five. Reachable from a gear button in
 * the manager, or from Mod Menu if it is installed — see {@code VoxelCamModMenu}, which is the
 * reason the parent is a plain {@code Screen} rather than {@code GuiScreenShotManager}: Mod
 * Menu hands back its own mod-list screen, not the manager.
 */
public class GuiSettings extends Screen {

	private static final int ROW_WIDTH = 200;
	private static final int ROW_HEIGHT = 20;
	private static final int STEP_BUTTON_SIZE = 20;
	private static final int ROW_GAP = 28;

	private final Screen parent;

	private Button burstMinusButton;
	private Button burstPlusButton;
	private Button bigScreenshotSizeButton;
	private int burstRowY;

	public GuiSettings(Screen parent) {
		super(Component.translatable("voxelcam.settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		burstRowY = height / 2 - 30;
		int sizeRowY = burstRowY + ROW_GAP;
		int autoCaptureRowY = sizeRowY + ROW_GAP;
		int doneY = autoCaptureRowY + ROW_GAP + 16;

		burstMinusButton = addRenderableWidget(
				Button.builder(Component.translatable("voxelcam.settings.burstlengthminus"), b -> adjustBurstLength(-1))
						.bounds(centerX - ROW_WIDTH / 2, burstRowY, STEP_BUTTON_SIZE, ROW_HEIGHT).build());
		burstMinusButton.setTooltip(Tooltip.create(Component.translatable("voxelcam.tooltip.burstlengthdown")));

		burstPlusButton = addRenderableWidget(
				Button.builder(Component.translatable("voxelcam.settings.burstlengthplus"), b -> adjustBurstLength(1))
						.bounds(centerX + ROW_WIDTH / 2 - STEP_BUTTON_SIZE, burstRowY, STEP_BUTTON_SIZE, ROW_HEIGHT).build());
		burstPlusButton.setTooltip(Tooltip.create(Component.translatable("voxelcam.tooltip.burstlengthup")));

		bigScreenshotSizeButton = addRenderableWidget(Button.builder(sizeLabel(), b -> cycleBigScreenshotSize())
				.bounds(centerX - ROW_WIDTH / 2, sizeRowY, ROW_WIDTH, ROW_HEIGHT).build());
		bigScreenshotSizeButton.setTooltip(Tooltip.create(Component.translatable("voxelcam.tooltip.bigscreenshotsize")));

		// A row rather than four more toggles here: see GuiAutoCaptureSettings for why it is its own
		// screen.
		Button autoCaptureButton = addRenderableWidget(
				Button.builder(Component.translatable("voxelcam.settings.autocapture"),
							b -> minecraft.setScreenAndShow(new GuiAutoCaptureSettings(this)))
						.bounds(centerX - ROW_WIDTH / 2, autoCaptureRowY, ROW_WIDTH, ROW_HEIGHT).build());
		autoCaptureButton.setTooltip(Tooltip.create(Component.translatable("voxelcam.tooltip.autocapture")));

		addRenderableWidget(Button.builder(Component.translatable("voxelcam.done"), b -> onClose())
				.bounds(centerX - ROW_WIDTH / 2, doneY, ROW_WIDTH, ROW_HEIGHT).build());

		updateBurstButtons();
	}

	/** {@code Burst.setLength} clamps on its own, but the buttons disable at the edges too
	 * rather than leaving a click that silently does nothing. */
	private void adjustBurstLength(int delta) {
		Burst.setLength(Burst.getLength() + delta);
		VoxelCamConfig.saveCurrent();
		updateBurstButtons();
	}

	private void updateBurstButtons() {
		burstMinusButton.active = Burst.getLength() > 1;
		burstPlusButton.active = Burst.getLength() < Burst.MAX_LENGTH;
	}

	private void cycleBigScreenshotSize() {
		List<String> tokens = new ArrayList<>();
		BigScreenshotSize.tokens().forEach(tokens::add);
		int index = tokens.indexOf(BigScreenshot.getSize().token());
		String next = tokens.get((index + 1) % tokens.size());
		BigScreenshotSize parsed = BigScreenshotSize.parse(next);
		if (parsed != null) {
			BigScreenshot.setSize(parsed);
			VoxelCamConfig.saveCurrent();
			bigScreenshotSizeButton.setMessage(sizeLabel());
		}
	}

	private Component sizeLabel() {
		return Component.translatable("voxelcam.settings.bigscreenshotsize", BigScreenshot.getSize().token());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);

		context.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
		context.centeredText(font, Component.translatable("voxelcam.settings.burstlength", Burst.getLength()),
				width / 2, burstRowY + 6, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
