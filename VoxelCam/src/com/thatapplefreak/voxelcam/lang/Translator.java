package com.thatapplefreak.voxelcam.lang;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

public class Translator {
	
	private static Properties english_US = getTranslation("english_US");
	
	public static String translate(String key) {
		//Find 
		return "filler";
	}
	
	private static Properties getTranslation(String fileName) {
		File langFile = null;
		try {
			langFile = new File(Translator.class.getResource(fileName + ".lang").toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		Properties p = new Properties();
		try {
			p.load(new FileInputStream(langFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return p;
	}
	
}
