package com.thatapplefreak.voxelcam.imagehandle.metadata;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

public abstract class MetaDataHandler {

	/**
	 * Write custom text data into an image
	 * @param image file refrence to image
	 * @param data Hashmap of data (Key, Value)
	 * @throws IOException 
	 */
	public void writeMetaData(File image, HashMap<String, String> data) throws IOException {
		ImageWriter pngWriter = ImageIO.getImageWritersByFormatName("png").next();
		IIOMetadata metadata = pngWriter.getDefaultImageMetadata(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB), pngWriter.getDefaultWriteParam());
		

		IIOMetadataNode text = new IIOMetadataNode("tEXt");
		for (Iterator iterator = data.keySet().iterator(); iterator.hasNext();) {
			IIOMetadataNode textEntry = new IIOMetadataNode("VoxelCamEntry");
			String key = (String) iterator.next();
			textEntry.setAttribute("keyword", key);
			textEntry.setAttribute("value", data.get(key));
			text.appendChild(textEntry);
		}			
			
		IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
		root.appendChild(text);

	    try {
			metadata.mergeTree("javax_imageio_png_1.0", root);
		} catch (IIOInvalidTreeException e) {
			e.printStackTrace();
		}
	    
	    //TODO write the data to the image File
		
	}
	
	/**
	 * Read custom text inside an image
	 * @param image File refrence to image being read
	 * @param key the known key for the data value
	 */
	public String readMetaData(File image, String key) {
		
		
		return null;
	}
	
}
