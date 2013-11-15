package com.thatapplefreak.voxelcam.bigpicture;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.imageio.ImageIO;

import net.minecraft.src.Minecraft;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.thatapplefreak.voxelcam.LiteModVoxelCam;
import com.thatapplefreak.voxelcam.VoxelCamConfig;
import com.thevoxelbox.common.gl.FBO;
import com.thevoxelbox.common.util.PrivateFields;
import com.thevoxelbox.common.util.PrivateMethods;

/**
 * Takes a big screenshot
 * 
 * @author thatapplefreak
 * 
 */
public class BigScreenshotTaker {

	/**
	 * The original width of minecraft
	 */
	private int originalWidthOfScreen;
	
	/**
	 * The original height of minecraft
	 */
	private int originalHeightOfScreen;
	
	/**
	 * Waiting for minecraft to render to take a screenshot
	 */
	private boolean waiting;

	/**
	 * The FrameBuffer that the big screenshot gets rendered to
	 */
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

	/**
	 * Sets minecraft to a custom size
	 */
	private void resizeMinecraft(final int width, final int height) {
		PrivateMethods.resizeMinecraft.invokeVoid(Minecraft.getMinecraft(), width, height);
	}

	/**
	 * Returns Minecraft to it's original width and height
	 */
	private void returnMinecraftToNormal() {
		PrivateMethods.resizeMinecraft.invokeVoid(Minecraft.getMinecraft(), originalWidthOfScreen, originalHeightOfScreen);
	}

	public void onTick() {
		if (waiting) {
			capture(LiteModVoxelCam.getConfig().getIntProperty(VoxelCamConfig.PHOTOWIDTH), LiteModVoxelCam.getConfig().getIntProperty(VoxelCamConfig.PHOTOHEIGHT), LiteModVoxelCam.getConfig().getStringProperty(VoxelCamConfig.BIGSCREENSHOTNAMINGMETHOD));
			fbo.end();
			fbo.dispose();
			returnMinecraftToNormal();
			Minecraft.getMinecraft().gameSettings.hideGUI = false;
			waiting = false;
		}
	}

	public String capture(final int width, final int height, String s) {
		GL11.glReadBuffer(GL11.GL_FRONT);
		final int bpp = 4;
		final ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * bpp);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
		final File screenshotName = getScreenshotName(s);
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
					ImageIO.write(image, "png", screenshotName);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};

		imageSaveThread.setName("Image Save Thread");
		imageSaveThread.setPriority(1);
		imageSaveThread.start();
		return "§4[VoxelCam]§F Saved Screenshot as: " + screenshotName.getName();
	}

	public static File getScreenshotName(String s) {
		s = s
		.replaceAll("DATE()", new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()))
		.replaceAll("SERVER()", PrivateFields.currentServerData.get(Minecraft.getMinecraft()) != null ? PrivateFields.currentServerData.get(Minecraft.getMinecraft()).serverName : "NoServer")
		;
		
		int var3 = 1;

		while (true) {
			File var1 = new File(LiteModVoxelCam.getScreenshotsDir(), "custom_" + s + (var3 == 1 ? "" : "_" + var3) + ".png");

			if (!var1.exists()) {
				return var1;
			}

			++var3;
		}
	}

}
