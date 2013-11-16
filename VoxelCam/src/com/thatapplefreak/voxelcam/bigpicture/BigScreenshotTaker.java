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
public abstract class BigScreenshotTaker {

	/**
	 * The original width of minecraft
	 */
	private static int originalWidthOfScreen;
	
	/**
	 * The original height of minecraft
	 */
	private static int originalHeightOfScreen;
	
	/**
	 * Waiting for minecraft to render to take a screenshot
	 */
	private static boolean waiting;

	/**
	 * The FrameBuffer that the big screenshot gets rendered to
	 */
	private static FBO fbo;

	public static void run() {
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
	private static void resizeMinecraft(final int width, final int height) {
		PrivateMethods.resizeMinecraft.invokeVoid(Minecraft.getMinecraft(), width, height);
	}

	/**
	 * Returns Minecraft to it's original width and height
	 */
	private static void returnMinecraftToNormal() {
		PrivateMethods.resizeMinecraft.invokeVoid(Minecraft.getMinecraft(), originalWidthOfScreen, originalHeightOfScreen);
	}

	public static void onTick() {
		if (waiting) {
			Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(ScreenshotTaker.capture(LiteModVoxelCam.getConfig().getIntProperty(VoxelCamConfig.PHOTOWIDTH), LiteModVoxelCam.getConfig().getIntProperty(VoxelCamConfig.PHOTOHEIGHT), LiteModVoxelCam.getConfig().getStringProperty(VoxelCamConfig.BIGSCREENSHOTNAMINGMETHOD)));
			fbo.end();
			fbo.dispose();
			returnMinecraftToNormal();
			Minecraft.getMinecraft().gameSettings.hideGUI = false;
			waiting = false;
		}
	}

}