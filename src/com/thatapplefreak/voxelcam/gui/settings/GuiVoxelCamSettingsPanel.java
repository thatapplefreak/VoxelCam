package com.thatapplefreak.voxelcam.gui.settings;

import net.minecraft.client.resources.I18n;

import com.thatapplefreak.voxelcam.VoxelCamConfig;
import com.thatapplefreak.voxelcam.VoxelCamCore;
import com.thevoxelbox.common.gui.GuiVoxelBoxSettingsPanel;
import com.thevoxelbox.common.util.ModConfig;
import com.thevoxelbox.common.util.properties.VoxelPropertyAbstractButton;
import com.thevoxelbox.common.util.properties.VoxelPropertyCheckBox;
import com.thevoxelbox.common.util.properties.VoxelPropertyIntField;
import com.thevoxelbox.common.util.properties.VoxelPropertyLabel;

public class GuiVoxelCamSettingsPanel extends GuiVoxelBoxSettingsPanel {

	private static final int max = 999999999;
	private static final int min = 30;

	private VoxelPropertyIntField widthField;
	private VoxelPropertyIntField heightField;

	public GuiVoxelCamSettingsPanel() {
		config = VoxelCamCore.getConfig();
	}

	@Override
	public void initGui() {
		super.initGui();

		// Mega Screenshot settings
		properties.add(new VoxelPropertyLabel(I18n.format("megascreenshotsettings"), PANEL_LEFT + 15, PANEL_TOP + 10));
		widthField = new VoxelPropertyIntFieldEX(VoxelCamCore.getConfig(), VoxelCamConfig.PHOTOWIDTH, I18n.format("width"), PANEL_LEFT + 20, PANEL_TOP + 25, 40);
		heightField = new VoxelPropertyIntFieldEX(VoxelCamCore.getConfig(), VoxelCamConfig.PHOTOHEIGHT, I18n.format("height"), PANEL_LEFT + 20, PANEL_TOP + 45, 40);
		widthField.setMaxFieldValue(max);
		widthField.setMinFieldValue(min);
		properties.add(widthField);
		heightField.setMaxFieldValue(max);
		heightField.setMinFieldValue(min);
		properties.add(heightField);
		properties.add(new VoxelPropertyAbstractButton<ModConfig>(config, null, "720p", PANEL_LEFT + 70, PANEL_TOP + 24) {
			@Override
			protected void onClick() {
				VoxelCamConfig config = VoxelCamCore.getConfig();
				config.setProperty(VoxelCamConfig.PHOTOWIDTH, 1280);
				config.setProperty(VoxelCamConfig.PHOTOHEIGHT, 720);
				updateFields();
			}
		});
		properties.add(new VoxelPropertyAbstractButton<ModConfig>(config, null, "1080p", PANEL_LEFT + 70, PANEL_TOP + 44) {
			@Override
			protected void onClick() {
				VoxelCamConfig config = VoxelCamCore.getConfig();
				config.setProperty(VoxelCamConfig.PHOTOWIDTH, 1920);
				config.setProperty(VoxelCamConfig.PHOTOHEIGHT, 1080);
				updateFields();
			}
		});
		properties.add(new VoxelPropertyAbstractButton<ModConfig>(config, null, "4K (2160p)", PANEL_LEFT + 145, PANEL_TOP + 24) {
			@Override
			protected void onClick() {
				VoxelCamConfig config = VoxelCamCore.getConfig();
				config.setProperty(VoxelCamConfig.PHOTOWIDTH, 3840);
				config.setProperty(VoxelCamConfig.PHOTOHEIGHT, 2160);
				updateFields();
			}
		});
		properties.add(new VoxelPropertyAbstractButton<ModConfig>(config, null, "IMAX", PANEL_LEFT + 145, PANEL_TOP + 44) {
			@Override
			protected void onClick() {
				VoxelCamConfig config = VoxelCamCore.getConfig();
				config.setProperty(VoxelCamConfig.PHOTOWIDTH, 10000);
				config.setProperty(VoxelCamConfig.PHOTOHEIGHT, 7000);
				updateFields();
			}
		});
		properties.add(new VoxelPropertyLabel(I18n.format("memorywarning"), PANEL_LEFT + 10, PANEL_TOP + 65, 0xFF0000));
	
		properties.add(new VoxelPropertyCheckBox(config, VoxelCamConfig.AUTO_UPLOAD, I18n.format("autouploadsocialmedia"), PANEL_LEFT + 10, PANEL_TOP + 80));
		
		properties.add(new VoxelPropertyCheckBox(config, VoxelCamConfig.AUTO_UPLOAD_IMGUR, I18n.format("to") + " Imgur", PANEL_LEFT + 20, PANEL_TOP + 95));
		properties.add(new VoxelPropertyCheckBox(config, VoxelCamConfig.AUTO_UPLOAD_DROPBOX, I18n.format("to") + " Dropbox", PANEL_LEFT + 20, PANEL_TOP + 110));
		properties.add(new VoxelPropertyCheckBox(config, VoxelCamConfig.AUTO_UPLOAD_GOOGLEDRIVE, I18n.format("to") + " Google Drive", PANEL_LEFT + 20, PANEL_TOP + 125));
	}

	protected void updateFields() {
		widthField.update();
		heightField.update();
	}

	@Override
	public String getPanelTitle() {
		return "VoxelCam Settings";
	}

}