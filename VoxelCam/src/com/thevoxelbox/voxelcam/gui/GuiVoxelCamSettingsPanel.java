package com.thevoxelbox.voxelcam.gui;

import com.thevoxelbox.voxelcam.LiteModVoxelCam;
import com.thevoxelbox.voxelcam.VoxelCamConfig;

public class GuiVoxelCamSettingsPanel extends GuiVoxelBoxSettingsPanel {

	private static final int max = (int) ((Runtime.getRuntime().totalMemory() / 1.074e9) * 5000);
	private static final int min = 30;

	private VoxelPropertyIntField widthField;
	private VoxelPropertyIntField heightField;

	public GuiVoxelCamSettingsPanel() {
		config = LiteModVoxelCam.getConfig();
	}

	@Override
	public void initGui() {
		super.initGui();

		// Mega Screenshot settings
		properties.add(new VoxelPropertyLabel("Mega-Screenshot Settings", PANEL_LEFT + 15, PANEL_TOP + 10));
		widthField = new VoxelPropertyIntFieldEX(LiteModVoxelCam.getConfig(), VoxelCamConfig.PHOTOWIDTH, "Width", PANEL_LEFT + 20, PANEL_TOP + 25, 40);
		heightField = new VoxelPropertyIntFieldEX(LiteModVoxelCam.getConfig(), VoxelCamConfig.PHOTOHEIGHT, "Height", PANEL_LEFT + 20, PANEL_TOP + 45, 40);
		widthField.setMaxFieldValue(max);
		widthField.setMinFieldValue(min);
		properties.add(widthField);
		heightField.setMaxFieldValue(max);
		heightField.setMinFieldValue(min);
		properties.add(heightField);
		properties.add(new VoxelPropertyAbstractButton(LiteModVoxelCam.getConfig(), null, "720p", PANEL_LEFT + 70, PANEL_TOP + 24) {
			@Override
			protected void onClick() {
				config.setProperty(VoxelCamConfig.PHOTOWIDTH, 1280);
				config.setProperty(VoxelCamConfig.PHOTOHEIGHT, 720);
				updateFields();
			}
		});
		properties.add(new VoxelPropertyAbstractButton(LiteModVoxelCam.getConfig(), null, "1080p", PANEL_LEFT + 70, PANEL_TOP + 44) {
			@Override
			protected void onClick() {
				config.setProperty(VoxelCamConfig.PHOTOWIDTH, 1920);
				config.setProperty(VoxelCamConfig.PHOTOHEIGHT, 1080);
				updateFields();
			}
		});
		properties.add(new VoxelPropertyAbstractButton(LiteModVoxelCam.getConfig(), null, "4K (2160p)", PANEL_LEFT + 145, PANEL_TOP + 24) {
			@Override
			protected void onClick() {
				config.setProperty(VoxelCamConfig.PHOTOWIDTH, 3840);
				config.setProperty(VoxelCamConfig.PHOTOHEIGHT, 2160);
				updateFields();
			}
		});
		properties.add(new VoxelPropertyAbstractButton(LiteModVoxelCam.getConfig(), null, "IMAX", PANEL_LEFT + 145, PANEL_TOP + 44) {
			@Override
			protected void onClick() {
				config.setProperty(VoxelCamConfig.PHOTOWIDTH, 10000);
				config.setProperty(VoxelCamConfig.PHOTOHEIGHT, 7000);
				updateFields();
			}
		});
		properties.add(new VoxelPropertyLabel("WARNING! Do not use higher resolutions on low end computers!", PANEL_LEFT + 10, PANEL_TOP + 65, 0xFF0000));
	}

	private void updateFields() {
		widthField.update();
		heightField.update();
	}

	@Override
	public String getPanelTitle() {
		return "VoxelCam Settings";
	}

}