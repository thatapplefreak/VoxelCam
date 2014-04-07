package com.thatapplefreak.voxelcam;

import com.mumfrey.liteloader.core.runtime.Obf;
import com.mumfrey.liteloader.transformers.Callback;
import com.mumfrey.liteloader.transformers.Callback.CallbackType;
import com.mumfrey.liteloader.transformers.CallbackInjectionTransformer;

public class VoxelCamTransformer extends CallbackInjectionTransformer {
	
	// TODO Obfuscation 1.7.2
	private static final String screenShotListenerObf = "ae";
	private static final String screenShotListenerSrg = "func_71365_K";
	private static final String screenShotListener = "screenshotListener";

	@Override
	protected void addCallbacks() {
		this.addCallback(Obf.Minecraft.name, screenShotListener,    "()V", new Callback(CallbackType.REDIRECT, screenShotListener, "com.thatapplefreak.voxelcam.VoxelCamCore"));
		this.addCallback(Obf.Minecraft.srg,  screenShotListenerSrg, "()V", new Callback(CallbackType.REDIRECT, screenShotListener, "com.thatapplefreak.voxelcam.VoxelCamCore"));
		this.addCallback(Obf.Minecraft.obf,  screenShotListenerObf, "()V", new Callback(CallbackType.REDIRECT, screenShotListener, "com.thatapplefreak.voxelcam.VoxelCamCore"));
	}
}
