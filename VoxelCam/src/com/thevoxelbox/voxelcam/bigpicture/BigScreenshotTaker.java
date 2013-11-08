package com.thevoxelbox.voxelcam.bigpicture;

import com.thevoxelbox.voxelcam.LiteModVoxelCam;
import com.thevoxelbox.voxelcam.VoxelCamConfig;

/**
 * Supposed to take Big screenshot, doesnt work properly (yet)
 * 
 * @author thatapplefreak
 * 
 */
public class BigScreenshotTaker {

	private int originalWidthOfScreen;
	private int originalHeightOfScreen;
	private boolean waiting;

	FBO fbo;

	public void run() {
		Minecraft.getMinecraft().gameSettings.hideGUI = true;
		originalWidthOfScreen = Minecraft.getMinecraft().displayWidth;
		originalHeightOfScreen = Minecraft.getMinecraft().displayHeight;
		resizeMinecraft(LiteModVoxelCam.getConfig().getIntProperty(VoxelCamConfig.PHOTOWIDTH), LiteModVoxelCam.getConfig().getIntProperty(VoxelCamConfig.PHOTOHEIGHT));
		fbo = new FBO();
		fbo.begin(LiteModVoxelCam.getConfig().getIntProperty(VoxelCamConfig.PHOTOWIDTH), LiteModVoxelCam.getConfig().getIntProperty(VoxelCamConfig.PHOTOHEIGHT));
		waiting = true;
	}

	private void resizeMinecraft(final int width, final int height) {
		PrivateMethods.resizeMinecraft.invokeVoid(Minecraft.getMinecraft(), width, height);
	}

	private void returnMinecraftToNormal() {
		PrivateMethods.resizeMinecraft.invokeVoid(Minecraft.getMinecraft(), originalWidthOfScreen, originalHeightOfScreen);
	}

	public void onTick() {
		if (waiting) {
			capture(LiteModVoxelCam.getConfig().getIntProperty(VoxelCamConfig.PHOTOWIDTH), LiteModVoxelCam.getConfig().getIntProperty(VoxelCamConfig.PHOTOHEIGHT));
			fbo.end();
			fbo.dispose();
			returnMinecraftToNormal();
			Minecraft.getMinecraft().gameSettings.hideGUI = false;
			waiting = false;
		}
	}

	private void capture(final int width, final int height) {
		GL11.glReadBuffer(GL11.GL_FRONT);
		final int bpp = 4;
		final ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * bpp);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
		Thread imageSaveThread = new Thread() {
			@Override
			public void run() {
				BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				for (int x = 0; x < width; x++) {
					for (int y = 0; y < height; y++) {
						int i = (x + (width * y)) * bpp;
						int r = buffer.get(i) & 0xFF;
						int g = buffer.get(i + 1) & 0xFF;
						int b = buffer.get(i + 2) & 0xFF;
						image.setRGB(x, height - (y + 1), (0xFF << 24) | (r << 16) | (g << 8) | b);
					}
				}
				try {
					ImageIO.write(image, "png", getScreenshotName());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};

		imageSaveThread.setName("Image Save Thread");
		imageSaveThread.setPriority(1);
		imageSaveThread.start();
	}

	private static File getScreenshotName() {
		String var2 = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
		int var3 = 1;

		while (true) {
			File var1 = new File(LiteModVoxelCam.getScreenshotsDir(), var2 + (var3 == 1 ? "" : "_" + var3) + ".png");

			if (!var1.exists()) {
				return var1;
			}

			++var3;
		}
	}

}
