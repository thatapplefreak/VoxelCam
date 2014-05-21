package com.thatapplefreak.voxelcam.imagehandle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.thatapplefreak.voxelcam.VoxelCamCore;
import com.thatapplefreak.voxelcam.VoxelCamConfig;
import com.thatapplefreak.voxelcam.imagehandle.metadata.MetaDataHandler;
import com.thatapplefreak.voxelcam.upload.AutoUploader;
import com.thevoxelbox.common.util.AbstractionLayer;
import com.thevoxelbox.common.util.ChatMessageBuilder;

public abstract class ScreenshotTaker {
	
	public static void capture(int width, int height, String s) {
		Minecraft mc = Minecraft.getMinecraft();
		if (OpenGlHelper.isFramebufferEnabled()) {
			width = mc.getFramebuffer().framebufferTextureWidth;
			height = mc.getFramebuffer().framebufferTextureHeight;
		}
		int totalPixels = width * height;
		
		IntBuffer pixelBuffer = BufferUtils.createIntBuffer(totalPixels);
		int[] pixelValues = new int[totalPixels];
		
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		
        if (OpenGlHelper.isFramebufferEnabled()) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, mc.getFramebuffer().framebufferTexture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        } else {
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        }
		
        pixelBuffer.get(pixelValues);
        TextureUtil.func_147953_a(pixelValues, width, height);    
        
		
		final File screenshotName = ScreenshotNamer.getScreenshotName(s);
		save(pixelValues, width, height, screenshotName);
	}
	
	private static int savepercent = 0;
	private static boolean isWritingToFile = false;
	
	private static void save(final int[] pixelValues, final int width, final int height, final File saveTo) {
		Thread imageSaveThread = new Thread("ImageSaver") {
			@Override
			public void run() {
				VoxelCamCore.screenshotIsSaving = true;
				BufferedImage image;				
				if (OpenGlHelper.isFramebufferEnabled()) {
	                image = new BufferedImage(width, height, 1);

	                double currentPixel = 0;
	                double totalPixels = pixelValues.length;
	                
	                for (int h = 0; h < height; ++h) {
	                    for (int w = 0; w < width; ++w) {
	                        image.setRGB(w, h, pixelValues[h * width + w]);
	                        currentPixel++;
	                        savepercent = (int) ((currentPixel / totalPixels) * 100);
	                    }
	                }	                
	                
	            } else {
	                image = new BufferedImage(width, height, 1);
	                savepercent = 100;
	                image.setRGB(0, 0, width, height, pixelValues, 0, width);
	            }
								
				try {
					isWritingToFile = true;
					ImageIO.write(image, "png", saveTo);
					MetaDataHandler.writeMetaData(saveTo);
					ChatMessageBuilder cmb = new ChatMessageBuilder();
					cmb.append("[VoxelCam]", EnumChatFormatting.DARK_RED, false);
					cmb.append(" " + I18n.format("savedscreenshotas") + ": ");
					cmb.append(saveTo.getName(), saveTo.getPath(), false);
					cmb.showChatMessageIngame();
					VoxelCamCore.screenshotIsSaving = false;
					isWritingToFile = false;
					upload(saveTo);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		imageSaveThread.setPriority(5);
		imageSaveThread.start();
	}
	
	public static boolean isWritingToFile() {
		return isWritingToFile;
	}
	
	public static int getSavePercent() {
		return savepercent;
	}

	private static void upload(final File saveTo) {
		if (VoxelCamCore.getConfig().getBoolProperty(VoxelCamConfig.AUTO_UPLOAD)) {
			AutoUploader.upload(saveTo);
		}
	}
	
}
