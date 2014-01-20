package com.thatapplefreak.voxelcam;

import com.mumfrey.liteloader.core.runtime.Obf;
import com.mumfrey.liteloader.core.transformers.Callback;
import com.mumfrey.liteloader.core.transformers.CallbackInjectionTransformer;
import com.mumfrey.liteloader.core.transformers.Callback.CallBackType;

public class VoxelCamTransformer extends CallbackInjectionTransformer {
	
	// TODO Obfuscation 1.7.2
	private static final String screenShotListenerObf = "ae";
	private static final String screenShotListenerSrg = "func_71365_K";
	private static final String screenShotListener = "screenshotListener";

	@Override
	protected void addMappings()
	{
		this.addCallbackMapping(Obf.Minecraft.name, screenShotListener, "()V", CallBackType.REDIRECT, new Callback(screenShotListener, "com.thatapplefreak.voxelcam.LiteModVoxelCam", true));
		this.addCallbackMapping(Obf.Minecraft.srg, screenShotListenerSrg, "()V", CallBackType.REDIRECT, new Callback(screenShotListener, "com.thatapplefreak.voxelcam.LiteModVoxelCam", true));
		this.addCallbackMapping(Obf.Minecraft.obf, screenShotListenerObf, "()V", CallBackType.REDIRECT, new Callback(screenShotListener, "com.thatapplefreak.voxelcam.LiteModVoxelCam", true));
	}
}
