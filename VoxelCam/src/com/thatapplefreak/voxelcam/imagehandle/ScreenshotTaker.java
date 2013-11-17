package com.thatapplefreak.voxelcam.imagehandle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import net.minecraft.src.Minecraft;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.thatapplefreak.voxelcam.LiteModVoxelCam;

public abstract class ScreenshotTaker {
	
	public static void capture(final int width, final int height, String s) {
		GL11.glReadBuffer(GL11.GL_FRONT);
		final int bpp = 4;
		final ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * bpp);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
		final File screenshotName = ScreenshotNamer.getScreenshotName(s);
		Thread imageSaveThread = new Thread("ImageSaver") {
			@Override
			public void run() {
				LiteModVoxelCam.screenshotIsSaving = true;
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
					Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage("§4[VoxelCam]§F Saved Screenshot as: " + screenshotName.getName());
					LiteModVoxelCam.screenshotIsSaving = false;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};

		imageSaveThread.setName("Image Save Thread");
		imageSaveThread.setPriority(1);
		imageSaveThread.start();
	}
	
}
