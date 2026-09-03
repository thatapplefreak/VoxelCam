package com.thatapplefreak.voxelcam.client.screenshot;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import com.thatapplefreak.voxelcam.client.util.ChatMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Oversized ("big") screenshots, the successor to the LiteLoader build's BigScreenshotTaker.
 *
 * <p>The old mod resized the real game window through a VoxelCommon reflection helper. On
 * 1.21.11 that is plain public API — {@link Window#setWidth(int)} plus
 * {@link Minecraft#framebufferSizeChanged()}, the same call GLFW's framebuffer-size
 * callback makes — so everything that caches a viewport size is notified by construction.
 *
 * <p>The capture spans two frames:
 * <ol>
 *   <li>{@link #request()} arms it (from the screenshot key).</li>
 *   <li>{@link #beforeFrame()} resizes at the head of the next frame, so the clear, the world,
 *       the HUD and post-processing all run at the target size.</li>
 *   <li>{@link #beforeBlit()} reads the finished frame back just before it is presented.</li>
 *   <li>The readback consumer restores the window.</li>
 * </ol>
 *
 * <p>The restore has to happen in the consumer rather than straight after the readback is
 * issued: {@code copyTextureToBuffer} does its {@code glReadPixels} into a buffer immediately
 * but finishes the work in a fenced task next frame, and restoring runs
 * {@code Framebuffer.resize}, which deletes the texture that task is still reading from.
 *
 * <p>A failed readback owes the window the same restore and is under the same constraint, so it
 * is deferred to the head of the next frame too — see {@link #deferRestore()}.
 */
public final class BigScreenshot {

	private enum State {
		IDLE,
		REQUESTED,
		CAPTURING,
		AWAITING_READBACK,
		RESTORE_PENDING
	}

	/**
	 * Frames a capture may sit unfinished before the window size is put back by force.
	 * Neither injection fires while the window is minimised, and another mod setting
	 * {@code skipGameRender} would skip the blit too — without this the window would stay
	 * oversized for the rest of the session.
	 */
	private static final int STALE_FRAMES = 8;

	/** Session-only; {@code /bigscreenshot} sets it and nothing writes it to disk. */
	private static volatile BigScreenshotSize size = BigScreenshotSize.DEFAULT;

	// Render-thread state only: request() runs from the screenshot key handler, the rest
	// from the two render(boolean) injections.
	private static State state = State.IDLE;
	private static int savedWidth;
	private static int savedHeight;
	private static int framesInState;

	private BigScreenshot() {
	}

	public static BigScreenshotSize getSize() {
		return size;
	}

	public static void setSize(BigScreenshotSize newSize) {
		size = newSize;
	}

	public static boolean isBusy() {
		return state != State.IDLE;
	}

	/** Arms a capture for the next frame, explaining itself if it cannot. */
	public static void request() {
		// Defensive: the screenshot key already refuses while a capture is in flight, but this
		// guard is what keeps a second request from overwriting the saved window size.
		if (state != State.IDLE) {
			ChatMessages.send("voxelcam.savingpleasewait");
			return;
		}

		Minecraft client = Minecraft.getInstance();
		// Resizing runs Screen.resize, which the manager turns into a full clearAndInit at an
		// absurd scaled width; and with no world ChatMessages is silent, so a multi-second
		// freeze would come with no explanation at all. This is the old ScreenshotIncapable.
		if (client.level == null || client.gui.screen() != null) {
			ChatMessages.send("voxelcam.bigshot.unavailable");
			return;
		}

		state = State.REQUESTED;
		framesInState = 0;
	}

	/**
	 * Head of {@code MinecraftClient.render}: applies the resize, puts the window back after a
	 * failed readback, or unsticks a stalled capture.
	 */
	public static void beforeFrame() {
		switch (state) {
			case IDLE -> {
			}
			case REQUESTED -> beginCapture();
			case RESTORE_PENDING -> finish();
			case CAPTURING, AWAITING_READBACK -> {
				if (++framesInState > STALE_FRAMES) {
					VoxelCamClient.LOGGER.warn("Big screenshot stalled in {}, restoring window size", state);
					finish();
				}
			}
		}
	}

	/** Just before the frame is presented, while the framebuffer still holds it. */
	public static void beforeBlit() {
		if (state != State.CAPTURING) {
			return;
		}
		state = State.AWAITING_READBACK;
		framesInState = 0;

		Minecraft client = Minecraft.getInstance();
		try {
			Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), BigScreenshot::onImageReady);
		} catch (Throwable t) {
			VoxelCamClient.LOGGER.error("Failed to read back a big screenshot", t);
			ChatMessages.send("voxelcam.bigshot.failed");
			deferRestore();
		}
	}

	/**
	 * Marks a capture as owing the window a restore, without performing one.
	 *
	 * <p>Kept out of {@link #beforeBlit()}'s catch body because the deferral is the whole point:
	 * at that injection vanilla has already fetched {@code mainRenderTarget().getColorTextureView()}
	 * and blits from it a couple of instructions after control comes back. Restoring here would run
	 * {@code GameRenderer.resize} -> {@code RenderTarget.resize} -> {@code destroyBuffers}, closing
	 * that exact view, and {@code GpuSurface.blitFromTexture} does not check {@code isClosed()}.
	 * The head of the next frame holds nothing of vanilla's, so that is where the resize goes.
	 */
	static void deferRestore() {
		state = State.RESTORE_PENDING;
		framesInState = 0;
	}

	private static void beginCapture() {
		Minecraft client = Minecraft.getInstance();
		Window window = client.getWindow();

		// Snapshotted only on this transition. A second request mid-capture is refused in
		// request(), because overwriting these with the oversized values would make every
		// restore path "restore" the window to the big size permanently.
		savedWidth = window.getWidth();
		savedHeight = window.getHeight();

		state = State.CAPTURING;
		framesInState = 0;

		BigScreenshotSize.Resolved target = size.resolve(window);
		try {
			applySize(client, target.width(), target.height());
		} catch (Throwable t) {
			VoxelCamClient.LOGGER.error("Failed to resize for a {}x{} screenshot",
					target.width(), target.height(), t);
			ChatMessages.send("voxelcam.bigshot.failed");
			finish();
		}
	}

	private static void onImageReady(NativeImage image) {
		// Restore before anything else: the fenced task that built this image is done with the
		// oversized texture only now, and finish() deletes it.
		finish();
		ScreenshotHandler.saveCapturedImage(image);
	}

	private static void finish() {
		state = State.IDLE;
		framesInState = 0;

		if (savedWidth <= 0 || savedHeight <= 0) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		try {
			Window window = client.getWindow();
			if (window.getWidth() != savedWidth || window.getHeight() != savedHeight) {
				applySize(client, savedWidth, savedHeight);
			}
		} catch (Throwable t) {
			VoxelCamClient.LOGGER.error("Failed to restore the window to {}x{}", savedWidth, savedHeight, t);
		} finally {
			savedWidth = 0;
			savedHeight = 0;
		}
	}

	private static void applySize(Minecraft client, int width, int height) {
		Window window = client.getWindow();
		window.setWidth(width);
		window.setHeight(height);
		// On 26.x framebufferSizeChanged only recalculates the GUI scale; resizing the main
		// render target is GameRenderer's job and nothing in Minecraft does it for us, so the
		// two have to be driven separately or the frame keeps rendering at the old size.
		client.gameRenderer.resize(width, height);
		client.framebufferSizeChanged();
	}
}
