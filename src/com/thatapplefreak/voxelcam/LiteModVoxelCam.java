package com.thatapplefreak.voxelcam;

import com.thevoxelbox.common.VoxelCommonLiteMod;

public class LiteModVoxelCam extends VoxelCommonLiteMod{

	public LiteModVoxelCam() {
		super("com.thatapplefreak.voxelcam.VoxelCamCore");
	}
	
	@Override
	public String getVersion() {
		return "1.3.1";
	}
	
	@Override
	public String getName() {
		return "VoxelCam";
	}
}
