package com.thatapplefreak.voxelcam;

import java.io.File;
import java.lang.annotation.Annotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;

import com.mumfrey.liteloader.ChatRenderListener;
import com.mumfrey.liteloader.core.runtime.Obf;
import com.mumfrey.liteloader.transformers.Callback;
import com.mumfrey.liteloader.transformers.Obfuscated;
import com.mumfrey.liteloader.transformers.Callback.CallbackType;
import com.mumfrey.liteloader.transformers.CallbackInjectionTransformer;
import com.mumfrey.liteloader.transformers.event.Event;
import com.mumfrey.liteloader.transformers.event.EventInjectionTransformer;
import com.mumfrey.liteloader.transformers.event.MethodInfo;
import com.mumfrey.liteloader.transformers.event.inject.BeforeInvoke;
import com.mumfrey.liteloader.transformers.event.inject.MethodHead;

public class VoxelCamTransformer extends EventInjectionTransformer {
	private static final String coreClassName = "com.thatapplefreak.voxelcam.VoxelCamCore";
	private static final String voxelCamScreenshotMethod = "takeScreenshot";

	private static final Obf classChatComponent = new Obf("net.minecraft.util.IChatComponent", "fj") {};
	private static final Obf classFrameBuffer = new Obf("net.minecraft.client.shader.Framebuffer","bmg") {};
	private static final Obf classScreenShotHelper = new Obf("net.minecraft.util.ScreenShotHelper", "bbp") {};
	private static final Obf methodSaveScreenshot  = new Obf("func_148260_a", "a", "saveScreenshot") {};
	
	@Override
	protected void addEvents() {
		Event onTakeScreenshot = Event.getOrCreate("onTakeScreenshot", true);
		MethodInfo target = new MethodInfo(classScreenShotHelper, methodSaveScreenshot, classChatComponent, File.class, Integer.TYPE, Integer.TYPE, classFrameBuffer);
		MethodInfo callback = new MethodInfo(coreClassName,	voxelCamScreenshotMethod);
		
		this.addEvent(onTakeScreenshot, target, new MethodHead()).addListener(callback);
	}
}