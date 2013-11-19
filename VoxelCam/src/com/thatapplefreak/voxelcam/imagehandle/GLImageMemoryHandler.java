package com.thatapplefreak.voxelcam.imagehandle;

import static org.lwjgl.opengl.GL11.glDeleteTextures;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;

import net.minecraft.src.TextureUtil;

public abstract class GLImageMemoryHandler {

	/**
	 * Map of all the GL binding integers Key is always the files absolute path
	 */
	private static Map<String, Integer> imageMap = new HashMap<String, Integer>();

	private static boolean loadingImageToMem = false;

	/**
	 * Open GL magic voodoo
	 * 
	 * @param imageFile
	 * @return
	 */
	public static void tryPutTextureIntoMem(final File imageFile) {
		if (!imageMap.containsKey(imageFile.getAbsolutePath())) {
//			new Thread("GL Image Loader") {
//				@Override
//				public void run() {
					loadingImageToMem = true;
					try {
						BufferedImage image = ImageIO.read(imageFile);
						int imgageName = TextureUtil.uploadTextureImageAllocate(TextureUtil.glGenTextures(), image, true, false);
						imageMap.put(imageFile.getAbsolutePath(), imgageName);
					} catch (IOException e) {
					}
					loadingImageToMem = false;
//				}
//			}.start();
		}
	}

	/**
	 * If the image can be removed from memory free some up
	 * 
	 * @param imageFile
	 */
	public static void requestImageRemovalFromMem(File imageFile) {
		if (imageMap.containsKey(imageFile.getAbsolutePath())) {
			glDeleteTextures(imageMap.get(imageFile.getAbsolutePath()));
			imageMap.remove(imageFile.getAbsolutePath());
		}
	}
	
	public static int getImageGLName(File f) {
		return imageMap.get(f.getAbsolutePath());
	}
	
	public static boolean loadingImage() {
		return loadingImageToMem;
	}

}
