package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.share.NativeShare;
import com.thatapplefreak.voxelcam.client.upload.CatboxUploader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.nio.file.Path;

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

	private ButtonWidget saveButton;
	private ButtonWidget linkButton;

	private Text status = null;
	private boolean statusIsError = false;

	public SharePopup(GuiScreenShotManager parent, File screenshot) {
		super(Text.translatable("voxelcam.share"));
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
		addDrawableChild(ButtonWidget.builder(Text.translatable("voxelcam.done"), b -> close())
				.dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private ButtonWidget addButton(String labelKey, String tooltipKey, int x, int y, Runnable action) {
		ButtonWidget widget = ButtonWidget.builder(Text.translatable(labelKey), b -> action.run())
				.dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		widget.setTooltip(Tooltip.of(Text.translatable(tooltipKey)));
		return addDrawableChild(widget);
	}

	private void saveCopy() {
		// The native dialog runs off-thread; keep the button from being pressed
		// again while one is already open.
		saveButton.active = false;
		setStatus("voxelcam.share.choosing", false);
		NativeShare.saveCopy(screenshot,
						Text.translatable("voxelcam.share.savecopy.title").getString(),
						Text.translatable("voxelcam.share.png").getString())
				.whenComplete((saved, error) -> client.execute(() -> {
					saveButton.active = true;
					if (error != null) {
						VoxelCamClient.LOGGER.warn("Saving a copy of {} failed", screenshot, error);
						setStatus("voxelcam.share.savefailed", true);
					} else if (saved == null) {
						// Cancelled: clear the "choose a location" prompt, say nothing.
						status = null;
					} else {
						setStatus(Text.translatable("voxelcam.share.saved", fileName(saved)), false);
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
		CatboxUploader.upload(screenshot).whenComplete((link, error) -> client.execute(() -> {
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
		setStatus(Text.translatable(translationKey), isError);
	}

	private void setStatus(Text text, boolean isError) {
		status = text;
		statusIsError = isError;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int titleY = height / 2 - (BUTTON_HEIGHT + GAP) * 2 - 30;
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, titleY, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal(textRenderer.trimToWidth(screenshot.getName(), BUTTON_WIDTH)).formatted(Formatting.GRAY),
				width / 2, titleY + 12, 0xFFA0A0A0);

		if (status != null) {
			context.drawCenteredTextWithShadow(textRenderer,
					status.copy().formatted(statusIsError ? Formatting.RED : Formatting.GREEN),
					width / 2, height / 2 + (BUTTON_HEIGHT + GAP) * 3 + 8, statusIsError ? 0xFFFF5555 : 0xFF55FF55);
		}
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
