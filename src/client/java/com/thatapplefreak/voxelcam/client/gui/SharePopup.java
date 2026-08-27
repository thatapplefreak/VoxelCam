package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.share.NativeShare;
import com.thatapplefreak.voxelcam.client.upload.CatboxUploader;
import java.io.File;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Share targets for one screenshot. Replaces the old "Post to..." dialog, which
 * offered six social networks behind OAuth flows; what is left is the sharing a
 * client-side mod can do without holding anyone's credentials.
 *
 * <p>Results are reported here rather than in chat: chat messages are silently
 * dropped when there is no player, and the manager is reachable from the title
 * screen.
 */
public class SharePopup extends Screen {

	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;

	private final GuiScreenShotManager parent;
	private final File screenshot;

	private Button saveButton;
	private Button linkButton;

	private Component status = null;
	private boolean statusIsError = false;

	public SharePopup(GuiScreenShotManager parent, File screenshot) {
		super(Component.translatable("voxelcam.share"));
		this.parent = parent;
		this.screenshot = screenshot;
	}

	@Override
	protected void init() {
		int x = width / 2 - BUTTON_WIDTH / 2;
		int y = height / 2 - (BUTTON_HEIGHT + GAP) * 2;

		saveButton = addButton("voxelcam.share.savecopy", "voxelcam.tooltip.savecopy", x, y, this::saveCopy);
		y += BUTTON_HEIGHT + GAP;
		addButton("voxelcam.share.reveal", "voxelcam.tooltip.reveal", x, y, this::reveal);
		y += BUTTON_HEIGHT + GAP;
		addButton("voxelcam.share.copypath", "voxelcam.tooltip.copypath", x, y, this::copyPath);
		y += BUTTON_HEIGHT + GAP;
		linkButton = addButton("voxelcam.share.link", "voxelcam.tooltip.link", x, y, this::uploadToCatbox);
		y += BUTTON_HEIGHT + GAP * 2;
		addRenderableWidget(Button.builder(Component.translatable("voxelcam.done"), b -> onClose())
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private Button addButton(String labelKey, String tooltipKey, int x, int y, Runnable action) {
		Button widget = Button.builder(Component.translatable(labelKey), b -> action.run())
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		widget.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		return addRenderableWidget(widget);
	}

	private void saveCopy() {
		// The native dialog runs off-thread; keep the button from being pressed
		// again while one is already open.
		saveButton.active = false;
		setStatus("voxelcam.share.choosing", false);
		NativeShare.saveCopy(screenshot,
						Component.translatable("voxelcam.share.savecopy.title").getString(),
						Component.translatable("voxelcam.share.png").getString())
				.whenComplete((saved, error) -> minecraft.execute(() -> {
					saveButton.active = true;
					if (error != null) {
						VoxelCamClient.LOGGER.warn("Saving a copy of {} failed", screenshot, error);
						setStatus("voxelcam.share.savefailed", true);
					} else if (saved == null) {
						// Cancelled: clear the "choose a location" prompt, say nothing.
						status = null;
					} else {
						setStatus(Component.translatable("voxelcam.share.saved", fileName(saved)), false);
					}
				}));
	}

	private void reveal() {
		NativeShare.revealInFileManager(screenshot);
		setStatus("voxelcam.share.revealed", false);
	}

	private void copyPath() {
		NativeShare.copyPath(screenshot);
		setStatus("voxelcam.share.pathcopied", false);
	}

	/**
	 * Catbox needs no credentials of any kind, so this is the one share target
	 * that produces a link without the player configuring anything first.
	 */
	private void uploadToCatbox() {
		linkButton.active = false;
		setStatus("voxelcam.share.uploading.link", false);
		CatboxUploader.upload(screenshot).whenComplete((link, error) -> minecraft.execute(() -> {
			linkButton.active = true;
			if (error != null) {
				VoxelCamClient.LOGGER.warn("Catbox upload failed", error);
				setStatus("voxelcam.share.linkfailed", true);
				return;
			}
			NativeShare.copyText(link);
			setStatus("voxelcam.share.uploaded", false);
		}));
	}

	private static String fileName(Path path) {
		Path name = path.getFileName();
		return name == null ? path.toString() : name.toString();
	}

	private void setStatus(String translationKey, boolean isError) {
		setStatus(Component.translatable(translationKey), isError);
	}

	private void setStatus(Component text, boolean isError) {
		status = text;
		statusIsError = isError;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int titleY = height / 2 - (BUTTON_HEIGHT + GAP) * 2 - 30;
		context.drawCenteredString(font, title, width / 2, titleY, 0xFFFFFFFF);
		context.drawCenteredString(font,
				Component.literal(font.plainSubstrByWidth(screenshot.getName(), BUTTON_WIDTH)).withStyle(ChatFormatting.GRAY),
				width / 2, titleY + 12, 0xFFA0A0A0);

		if (status != null) {
			context.drawCenteredString(font,
					status.copy().withStyle(statusIsError ? ChatFormatting.RED : ChatFormatting.GREEN),
					width / 2, height / 2 + (BUTTON_HEIGHT + GAP) * 3 + 8, statusIsError ? 0xFFFF5555 : 0xFF55FF55);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
