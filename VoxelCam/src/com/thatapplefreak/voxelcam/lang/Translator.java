package com.thatapplefreak.voxelcam.lang;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import net.minecraft.client.Minecraft;

public class Translator {
	
	private static Properties english_US = getTranslation("english_US");
	private static Properties spanish_ES = getTranslation("spanish_ES");
	private static Properties sweedish_SWE = getTranslation("sweedish_SWE");
	
	public static String translate(String key) {
		String language = Minecraft.getMinecraft().gameSettings.language;
		
		if (language.equals("en_US")) {
			return english_US.getProperty(key);
		} else if (language.equals("es_ES")) {
			return spanish_ES.getProperty(key);
		} else if (language.equals("sv_SE")) {
			return sweedish_SWE.getProperty(key);
		}
		
		//Default to US English
		return english_US.getProperty(key);
	}
	
	private static Properties getTranslation(String fileName) {
		File langFile = null;
		try {
			URI res = Translator.class.getResource(fileName + ".lang").toURI();
			langFile = new File(res);
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
