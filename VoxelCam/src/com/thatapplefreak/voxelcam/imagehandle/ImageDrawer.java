package com.thatapplefreak.voxelcam.imagehandle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.src.Tessellator;
import net.minecraft.src.TextureUtil;

import static org.lwjgl.opengl.GL11.*;

/**
 * Utility Class that can take any File that is an image and draw it to a gui
 * 
 * @author thatapplefreak
 */
public abstract class ImageDrawer {

	/**
	 * Draws the image given the top left and bottom right corners
	 * 
	 * @param img
	 *            GL integer name of the texture
	 * @param x
	 *            x coordinate of the top left corner
	 * @param y
	 *            y coordinate of the top left corner
	 * @param x2
	 *            x coordinate of the bottom right corner
	 * @param y2
	 *            y coordinate of the bottom right corner
	 */
	public static void drawImageToGui(int img, int x, int y, int x2, int y2) {
		glBindTexture(GL_TEXTURE_2D, img);
		glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();
		tessellator.addVertexWithUV(x, y2, 0, 0, 1);
		tessellator.addVertexWithUV(x2, y2, 0, 1, 1);
		tessellator.addVertexWithUV(x2, y, 0, 1, 0);
		tessellator.addVertexWithUV(x, y, 0, 0, 0);
		tessellator.draw();
	}
}
