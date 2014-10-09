package com.thatapplefreak.voxelcam.gui.manager;

import static org.lwjgl.opengl.GL11.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;

import com.thatapplefreak.voxelcam.imagehandle.GLImageMemoryHandler;
import com.thatapplefreak.voxelcam.imagehandle.ImageDrawer;

public class ScalePhotoFrame extends Gui {

	private final float scale;

	public int x, y, width, height;
	private File currentPhoto;
	private GuiScreenShotManager parentScreen;

	private BufferedImage img = null;

	public ScalePhotoFrame(GuiScreenShotManager parent, int x, int y, float scale, File photo) {
		this.x = x;
		this.y = y;
		this.scale = scale;
		this.parentScreen = parent;
		this.width = (int) (parent.width * scale);
		this.height = (int) (parent.height * scale);
		setPhoto(photo);
	}

	public void setPhoto(File photo) {
		currentPhoto = photo;
		if (currentPhoto != null) {
			try {
				img = ImageIO.read(photo);
				GLImageMemoryHandler.tryPutTextureIntoMem(photo);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public File getPhoto() {
		return currentPhoto;
	}

	public void update(int x, int y) {
		this.x = x;
		this.y = y;
		this.width = (int) (parentScreen.width * scale);
		this.height = (int) (parentScreen.height * scale);
	}

	public void draw(int mouseX, int mouseY, float partialTicks) {
		
		if (currentPhoto != null && GLImageMemoryHandler.loadingImage(GLImageMemoryHandler.getImageGLName(currentPhoto))) {
			drawBackground();
			parentScreen.drawCenteredString(Minecraft.getMinecraft().fontRendererObj, I18n.format("loadingiamge") + "...", x + width / 2, y + height / 2, 0xffffff);
			return;
		}
		
		if (currentPhoto != null && img != null) {
			parentScreen.drawCenteredString(Minecraft.getMinecraft().fontRendererObj, currentPhoto.getName().replace(".png", ""), x + width / 2, y - 10, 0xffffff);
			float frameAspect = (float) width / (float) height;
			float picAspect = (float) img.getWidth() / (float) img.getHeight();
			int photoX = 0;
			int photoY = 0;
			int photoX2 = 0;
			int photoY2 = 0;
			if (picAspect == frameAspect) {
				photoX = x;
				photoY = y;
				photoX2 = x + width;
				photoY2 = y + height;
			} else if (picAspect > frameAspect) {
				photoX = x;
				photoX2 = x + width;
				float picHeight = width * (float) img.getHeight() / img.getWidth();
				photoY = (int) (y + height / 2 - picHeight / 2);
				photoY2 = (int) (y + height / 2 + picHeight / 2);
			} else if (picAspect < frameAspect) {
				photoY = y;
				photoY2 = y + height;
				float picWidth = height * (float) img.getWidth() / img.getHeight();
				photoX = (int) (x + (width / 2) - (picWidth / 2));
				photoX2 = (int) (x + (width / 2) + (picWidth / 2));
			}
			drawBackground();
			ImageDrawer.drawImageToGui(GLImageMemoryHandler.getImageGLName(currentPhoto), photoX, photoY, photoX2, photoY2);
			parentScreen.enableButtons(true);
		} else {
			drawBackground();
			parentScreen.drawCenteredString(Minecraft.getMinecraft().fontRendererObj, I18n.format("noscreenshots"), x + width / 2, y + height / 2, 0xffffff);
			parentScreen.enableButtons(false);
		}
	}

	private void drawBackground() {
		Tessellator t = Tessellator.getInstance();
		glEnable(GL_BLEND);
		glDisable(GL_TEXTURE_2D);
		glColor4f(0.0F, 0.0F, 0.0F, 0.5F);
		float blendArea = 32.0F;
		t.startDrawingQuads();
		t.addVertexWithUV(x, y + height, 0.0D, x / blendArea, (y + height) / blendArea);
		t.addVertexWithUV(x + width, y + height, 0.0D, (x + width) / blendArea, (y + height) / blendArea);
		t.addVertexWithUV(x + width, y, 0.0D, (x + width) / blendArea, (x + height) / blendArea);
		t.addVertexWithUV(x, y, 0.0D, x / blendArea, (y + height) / blendArea);
		t.draw();
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glDisable(GL_ALPHA_TEST);
		glShadeModel(GL_SMOOTH);
		byte four = 4;
		t.startDrawingQuads();
		t.setColorRGBA_I(0, 0);
		t.addVertexWithUV(x, y + four, 0.0D, 0.0D, 1.0D);
		t.addVertexWithUV(x + width, y + four, 0.0D, 1.0D, 1.0D);
		t.setColorRGBA_I(0, 255);
		t.addVertexWithUV(x + width, y, 0.0D, 1.0D, 0.0D);
		t.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
		t.draw();
		t.startDrawingQuads();
		t.setColorRGBA_I(0, 255);
		t.addVertexWithUV(x, y + height, 0.0D, 0.0D, 1.0D);
		t.addVertexWithUV(x + width, y + height, 0.0D, 1.0D, 1.0D);
		t.setColorRGBA_I(0, 0);
		t.addVertexWithUV(x + width, y + height - four, 0.0D, 1.0D, 0.0D);
		t.addVertexWithUV(x, y + height - four, 0.0D, 0.0D, 0.0D);
		t.draw();
		glEnable(GL_TEXTURE_2D);
	}
}
