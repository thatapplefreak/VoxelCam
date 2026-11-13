package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.screenshot.AnimatedPng;
import com.thatapplefreak.voxelcam.client.screenshot.AnimationPreview;
import com.thatapplefreak.voxelcam.client.screenshot.SmallerShareCopy;
import com.thatapplefreak.voxelcam.client.share.NativeShare;
import com.thatapplefreak.voxelcam.client.upload.CatboxUploader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

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
	/** What every button acts on: the screenshot itself, or for an exported animation its WebP
	 * copy — Discord plays an animated WebP but shows an APNG as a still, and a link pasted
	 * there is where most of these end up. */
	private final File screenshot;
	/** The file the popup was opened on: the APNG itself, for an animation. */
	private final File source;
	/** A smaller WebP made for Discord this session, reused for a second send, or null. */
	private File smallCopy;
	private boolean shrinking;

	private Button saveButton;
	private Button revealButton;
	private Button linkButton;

	private Component status = null;
	private boolean statusIsError = false;

	public SharePopup(GuiScreenShotManager parent, File screenshot) {
		super(Component.translatable("voxelcam.share"));
		this.parent = parent;
		this.source = screenshot;
		File webp = AnimatedPng.sharingCopyOf(screenshot);
		this.screenshot = webp != null ? webp : screenshot;
	}

	@Override
	protected void init() {
		int x = width / 2 - BUTTON_WIDTH / 2;
		int y = height / 2 - (BUTTON_HEIGHT + GAP) * 2;

		saveButton = addButton("voxelcam.share.savecopy", "voxelcam.tooltip.savecopy", x, y, this::saveCopy);
		y += BUTTON_HEIGHT + GAP;
		revealButton = addButton("voxelcam.share.reveal", "voxelcam.tooltip.reveal", x, y, this::reveal);
		y += BUTTON_HEIGHT + GAP;
		addButton("voxelcam.share.copypath", "voxelcam.tooltip.copypath", x, y, this::copyPath);
		y += BUTTON_HEIGHT + GAP;
		linkButton = addButton("voxelcam.share.link", "voxelcam.tooltip.link", x, y, this::uploadToCatbox);
		y += BUTTON_HEIGHT + GAP * 2;
		addRenderableWidget(Button.builder(Component.translatable("voxelcam.done"), b -> onClose())
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		// init() runs again on the way back from the size popup, with fresh buttons.
		saveButton.active = !shrinking;
		linkButton.active = !shrinking;
	}

	/**
	 * Runs {@code action} on the file to send — asking first, when the sharing copy is over
	 * Discord's free limit, whether to send a smaller copy instead. Only the two actions that send
	 * a file somewhere ask; Reveal and Copy path point at what is on disk.
	 */
	private void withSizeChoice(Consumer<File> action) {
		if (screenshot.equals(source) || !SmallerShareCopy.tooBigForDiscord(screenshot)) {
			action.accept(screenshot);
			return;
		}
		minecraft.setScreenAndShow(new ShareSizePopup(this, screenshot.length(), smaller -> {
			minecraft.setScreenAndShow(this);
			if (smaller) {
				withSmallerCopy(action);
			} else {
				action.accept(screenshot);
			}
		}));
	}

	/** Makes the smaller copy off the render thread — it decodes and re-encodes every frame, at
	 * least once — then runs {@code action} on it. Made once per popup and reused. */
	private void withSmallerCopy(Consumer<File> action) {
		if (smallCopy != null) {
			action.accept(smallCopy);
			return;
		}
		shrinking = true;
		saveButton.active = false;
		linkButton.active = false;
		setStatus("voxelcam.share.shrinking", false);
		long fullSize = screenshot.length();
		CompletableFuture.supplyAsync(() -> {
			try {
				return SmallerShareCopy.make(source, fullSize, SmallerShareCopy.DISCORD_LIMIT_BYTES,
						AnimationPreview::argbOf);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}, Util.ioPool()).whenComplete((made, error) -> minecraft.execute(() -> {
			shrinking = false;
			saveButton.active = true;
			linkButton.active = true;
			if (error != null) {
				VoxelCamClient.LOGGER.warn("Making a smaller copy of {} failed", source, error);
				setStatus("voxelcam.share.shrinkfailed", true);
				return;
			}
			smallCopy = made;
			status = null;
			action.accept(made);
		}));
	}

	private Button addButton(String labelKey, String tooltipKey, int x, int y, Runnable action) {
		Button widget = Button.builder(Component.translatable(labelKey), b -> action.run())
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		widget.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		return addRenderableWidget(widget);
	}

	private void saveCopy() {
		withSizeChoice(this::saveCopyOf);
	}

	private void saveCopyOf(File file) {
		// The native dialog answers asynchronously (ADR-0012); keep the button from being
		// pressed again while one is already open.
		saveButton.active = false;
		setStatus("voxelcam.share.choosing", false);
		// A smaller copy lives in a temp folder; the dialog opens beside the animation instead.
		File suggested = file.equals(smallCopy) ? new File(source.getAbsoluteFile().getParentFile(), file.getName()) : file;
		NativeShare.saveCopy(file, suggested,
						Component.translatable("voxelcam.share.savecopy.title").getString(),
						Component.translatable("webp".equals(NativeShare.extensionOf(file))
								? "voxelcam.share.webp" : "voxelcam.share.png").getString())
				.whenComplete((saved, error) -> minecraft.execute(() -> {
					saveButton.active = true;
					if (error != null) {
						VoxelCamClient.LOGGER.warn("Saving a copy of {} failed", file, error);
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
		// The command is waited on off-thread, so the outcome arrives later; keep the
		// button from queueing a second file manager window in the meantime.
		revealButton.active = false;
		NativeShare.revealInFileManager(screenshot).whenComplete((outcome, error) -> minecraft.execute(() -> {
			revealButton.active = true;
			if (error != null) {
				VoxelCamClient.LOGGER.warn("Revealing {} failed", screenshot, error);
				setStatus("voxelcam.share.revealfailed", true);
				return;
			}
			switch (outcome) {
				case REVEALED -> setStatus("voxelcam.share.revealed", false);
				case OPENED_FOLDER -> setStatus("voxelcam.share.revealed.folder", false);
				case FAILED -> setStatus("voxelcam.share.revealfailed", true);
			}
		}));
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
		withSizeChoice(this::uploadToCatbox);
	}

	private void uploadToCatbox(File file) {
		linkButton.active = false;
		setStatus("voxelcam.share.uploading.link", false);
		CatboxUploader.upload(file).whenComplete((link, error) -> minecraft.execute(() -> {
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
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		int titleY = height / 2 - (BUTTON_HEIGHT + GAP) * 2 - 30;
		context.centeredText(font, title, width / 2, titleY, 0xFFFFFFFF);
		context.centeredText(font,
				Component.literal(font.plainSubstrByWidth(screenshot.getName(), BUTTON_WIDTH)).withStyle(ChatFormatting.GRAY),
				width / 2, titleY + 12, 0xFFA0A0A0);

		if (status != null) {
			context.centeredText(font,
					status.copy().withStyle(statusIsError ? ChatFormatting.RED : ChatFormatting.GREEN),
					width / 2, height / 2 + (BUTTON_HEIGHT + GAP) * 3 + 8, statusIsError ? 0xFFFF5555 : 0xFF55FF55);
		}
	}

	@Override
	public void onClose() {
		// Not while a save dialog may still be copying it; deleteOnExit covers that case.
		if (saveButton == null || saveButton.active) {
			SmallerShareCopy.discard(smallCopy);
		}
		minecraft.setScreenAndShow(parent);
	}
}
