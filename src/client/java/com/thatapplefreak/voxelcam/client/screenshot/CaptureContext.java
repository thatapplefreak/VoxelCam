package com.thatapplefreak.voxelcam.client.screenshot;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;

/**
 * Where a screenshot was taken: dimension, coordinates, and world/server identity. Embedded
 * into the file itself as PNG text chunks (see {@link PngTextChunk}) rather than a sidecar,
 * so it survives rename, copy, and share — {@code VoxelCamIO.rename()}/{@code delete()} only
 * ever touch the PNG, and a sidecar keyed by filename would silently orphan on rename and
 * leak on delete.
 */
public record CaptureContext(String dimension, int x, int y, int z, String worldName) {

	private static final String DIMENSION_KEY = "voxelcam:dimension";
	private static final String X_KEY = "voxelcam:x";
	private static final String Y_KEY = "voxelcam:y";
	private static final String Z_KEY = "voxelcam:z";
	private static final String WORLD_KEY = "voxelcam:world";

	/**
	 * @return null if there is no player to read a position from — capturing from the title
	 * screen, where the manager is also reachable, has no world at all.
	 */
	public static CaptureContext capture() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || player.level() == null) {
			return null;
		}

		BlockPos pos = player.blockPosition();
		String dimension = player.level().dimension().identifier().toString();
		return new CaptureContext(dimension, pos.getX(), pos.getY(), pos.getZ(), worldName(client));
	}

	/**
	 * The singleplayer world's own display name, or the multiplayer server list entry's name.
	 * Deliberately not the server's address: that would bake a real leak into a file a share
	 * flow might later upload.
	 */
	private static String worldName(Minecraft client) {
		if (client.hasSingleplayerServer()) {
			IntegratedServer server = client.getSingleplayerServer();
			return server != null ? server.getWorldData().getLevelName() : null;
		}
		ServerData server = client.getCurrentServer();
		return server != null ? server.name : null;
	}

	public Map<String, String> toTags() {
		Map<String, String> tags = new LinkedHashMap<>();
		tags.put(DIMENSION_KEY, dimension);
		tags.put(X_KEY, Integer.toString(x));
		tags.put(Y_KEY, Integer.toString(y));
		tags.put(Z_KEY, Integer.toString(z));
		if (worldName != null && !worldName.isEmpty()) {
			tags.put(WORLD_KEY, worldName);
		}
		return tags;
	}

	/**
	 * @return null rather than a half-built record if the required tags are missing or
	 * unparsable — every screenshot taken before this feature shipped (and anything from
	 * vanilla F2 or another mod sharing the folder) has no tags at all.
	 */
	public static CaptureContext fromTags(Map<String, String> tags) {
		String dimension = tags.get(DIMENSION_KEY);
		if (dimension == null) {
			return null;
		}
		try {
			int x = Integer.parseInt(tags.get(X_KEY));
			int y = Integer.parseInt(tags.get(Y_KEY));
			int z = Integer.parseInt(tags.get(Z_KEY));
			return new CaptureContext(dimension, x, y, z, tags.get(WORLD_KEY));
		} catch (NumberFormatException | NullPointerException e) {
			return null;
		}
	}

	/** e.g. {@code "123, 64, -45"} for the overworld, {@code "the_nether -32, 70, 12"} otherwise. */
	public String describeLocation() {
		String coords = x + ", " + y + ", " + z;
		if ("minecraft:overworld".equals(dimension)) {
			return coords;
		}
		int colon = dimension.indexOf(':');
		String shortDimension = colon >= 0 ? dimension.substring(colon + 1) : dimension;
		return shortDimension + " " + coords;
	}
}
