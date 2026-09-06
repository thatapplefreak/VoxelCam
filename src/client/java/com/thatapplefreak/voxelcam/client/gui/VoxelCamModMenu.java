package com.thatapplefreak.voxelcam.client.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Points Mod Menu's config button at {@link GuiSettings}. {@code modCompileOnly} in
 * build.gradle, and no "depends" entry in fabric.mod.json — this class is simply never loaded
 * if Mod Menu is absent, so it costs nothing for a player who does not have it installed.
 */
public class VoxelCamModMenu implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return GuiSettings::new;
	}
}
