package com.thatapplefreak.voxelcam.client.screenshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * What was installed when a screenshot was taken: a curated mod list plus, if Iris is
 * present and active, the shader pack name. Embedded the same way {@link CaptureContext}
 * is, into the same PNG {@code iTXt} chunks {@link ScreenshotHandler} already writes.
 */
public record ModProvenance(List<String> mods, String shaderPack) {

	private static final String MODS_KEY = "voxelcam:mods";
	private static final String SHADERPACK_KEY = "voxelcam:shaderpack";

	/**
	 * Fabric API alone splits into 30-plus ids that say nothing about what content is
	 * actually installed; MixinExtras and VoxelCam itself are equally uninformative. A
	 * denylist of infrastructure, rather than an allowlist of "real" mods, is the only
	 * approach that does not need updating for every gameplay mod that might exist.
	 *
	 * "fabricloader" ships a real fabric.mod.json rather than being synthesized the way
	 * "java" and "minecraft" are, so it reads as type "fabric" like any other mod and needs
	 * naming explicitly here — the "fabric-" prefix check below does not match it, since
	 * there is no hyphen after "fabric".
	 */
	private static final Set<String> EXCLUDED_IDS = Set.of("voxelcam", "voxelcam-gametest", "mixinextras", "fabricloader");

	/**
	 * Keeps the tag "a useful line of text rather than a wall of mod IDs" (per the issue)
	 * even for a heavily modded install; the average id is well under 20 characters, so this
	 * still fits a few dozen before truncating.
	 */
	private static final int MAX_TAG_LENGTH = 500;

	/**
	 * The mod list is fixed for the life of the game session, and Iris' active pack can be
	 * read fresh cheaply, so unlike {@link CaptureContext#capture()} this needs no
	 * render-thread snapshot — it is safe to call from the IO thread that does the write.
	 *
	 * Deliberately does not filter on {@code ModContainer.getContainingMod()}: that only
	 * catches real jar-in-jar bundling, which is exactly how some modpacks ship genuine
	 * content mods nested inside an umbrella jar. Dropping those would silently omit the
	 * one thing this feature exists to record. Fabric API's own split, which does need
	 * filtering, shows up as top-level siblings rather than contained mods anyway.
	 */
	public static ModProvenance capture() {
		List<String> mods = FabricLoader.getInstance().getAllMods().stream()
				.map(ModContainer::getMetadata)
				.filter(meta -> !isInfrastructure(meta))
				.map(ModMetadata::getId)
				.sorted()
				.toList();
		return new ModProvenance(mods, ShaderPack.activeName());
	}

	private static boolean isInfrastructure(ModMetadata meta) {
		String id = meta.getId();
		return "builtin".equals(meta.getType())
				|| id.equals("fabric-api") || id.startsWith("fabric-")
				|| EXCLUDED_IDS.contains(id);
	}

	public Map<String, String> toTags() {
		Map<String, String> tags = new LinkedHashMap<>();
		if (!mods.isEmpty()) {
			tags.put(MODS_KEY, modsTagValue());
		}
		if (shaderPack != null && !shaderPack.isEmpty()) {
			tags.put(SHADERPACK_KEY, shaderPack);
		}
		return tags;
	}

	/** Joins as many mod ids as fit under {@link #MAX_TAG_LENGTH}, noting how many did not. */
	private String modsTagValue() {
		StringBuilder joined = new StringBuilder();
		int included = 0;
		for (String mod : mods) {
			String next = joined.isEmpty() ? mod : ", " + mod;
			if (joined.length() + next.length() > MAX_TAG_LENGTH) {
				break;
			}
			joined.append(next);
			included++;
		}
		int omitted = mods.size() - included;
		if (omitted > 0) {
			joined.append(" (+").append(omitted).append(" more)");
		}
		return joined.toString();
	}
}
