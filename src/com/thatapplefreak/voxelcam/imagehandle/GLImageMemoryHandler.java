package com.thatapplefreak.voxelcam.imagehandle;

import static org.lwjgl.opengl.GL11.glDeleteTextures;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.TextureUtil;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;


public abstract class GLImageMemoryHandler {

	/**
	 * Map of all the GL binding integers Key is always the files absolute path
	 */
	private static Map<String, Integer> imageMap = new HashMap<String, Integer>();

	private static HashSet<Integer> loadingImages = new HashSet<Integer>();

	/**
	 * Open GL magic voodoo
	 * 
	 * @param imageFile
	 * @return
	 */
	public static void tryPutTextureIntoMem(final File imageFile) {
		if (!imageMap.containsKey(imageFile.getAbsolutePath())) {
			int textureName = TextureUtil.glGenTextures();
			imageMap.put(imageFile.getAbsolutePath(), textureName);
//			new Thread("GL Image Loader") {
//				@Override
//				public void run() {
					loadingImages.add(textureName);
					try {
						BufferedImage image = ImageIO.read(imageFile);
						TextureUtil.uploadTextureImageAllocate(textureName, image, true, false);
						loadingImages.remove(textureName);
					} catch (IOException e) {
					}
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
	
	/**
	 * Flushes All images from memory
	 */
	public static void requestImageFlush() {
		Set<String> keyset = imageMap.keySet();
		for (String s : keyset) {
			glDeleteTextures(imageMap.get(s));
		}
		imageMap.clear();
	}
	
	public static int getImageGLName(File f) {
		return imageMap.get(f.getAbsolutePath());
	}
	
	public static boolean loadingImage(int i) {
		return loadingImages.contains(i);
	}

}
