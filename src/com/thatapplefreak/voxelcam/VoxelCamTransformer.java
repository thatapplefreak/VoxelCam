package com.thatapplefreak.voxelcam;

import java.io.File;

import com.mumfrey.liteloader.core.runtime.Obf;
import com.mumfrey.liteloader.transformers.event.Event;
import com.mumfrey.liteloader.transformers.event.EventInjectionTransformer;
import com.mumfrey.liteloader.transformers.event.MethodInfo;
import com.mumfrey.liteloader.transformers.event.inject.MethodHead;

public class VoxelCamTransformer extends EventInjectionTransformer {
	private static final String coreClassName = "com.thatapplefreak.voxelcam.VoxelCamCore";
	private static final String voxelCamScreenshotMethod = "takeScreenshot";

	private static final Obf classChatComponent = new Obf("net.minecraft.util.IChatComponent", "fj") {};
	private static final Obf classScreenShotHelper = new Obf("net.minecraft.util.ScreenShotHelper", "bbp") {};
	private static final Obf methodSaveScreenshot  = new Obf("func_148260_a", "a", "saveScreenshot") {};
	
	@Override
	protected void addEvents() {
		Event onTakeScreenshot = Event.getOrCreate("onTakeScreenshot", true);
		MethodInfo target = new MethodInfo(classScreenShotHelper, methodSaveScreenshot, classChatComponent, File.class, Integer.TYPE, Integer.TYPE, Obf.FrameBuffer);
		MethodInfo callback = new MethodInfo(coreClassName,	voxelCamScreenshotMethod);
		
		this.addEvent(onTakeScreenshot, target, new MethodHead()).addListener(callback);
	}
}