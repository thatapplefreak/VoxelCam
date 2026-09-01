package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The active Iris shader pack's name, if any. Iris is a soft dependency — there is no
 * compile-time dependency on it at all, since guessing a Maven coordinate for a shader mod
 * is exactly the kind of "guessed file format" the rest of this mod avoids. Detection goes
 * through {@code isModLoaded} and every call after that through reflection, so a missing,
 * renamed, or differently-shaped Iris build can never break a capture — it can only ever
 * silently leave the tag off.
 */
final class ShaderPack {

	private ShaderPack() {
	}

	static String activeName() {
		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			return null;
		}
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			boolean inUse = (boolean) api.getMethod("isShaderPackInUse").invoke(instance);
			if (!inUse) {
				return null;
			}
			return packName(api.getMethod("getCurrentPackName").invoke(instance));
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Debug, not warn: this also catches a shape mismatch against a real Iris
			// build, but "no shader pack tag" is the normal case for most players too, so
			// this must not read as an error every time Iris is simply absent.
			VoxelCamClient.LOGGER.debug("Could not read the active Iris shader pack", e);
			return null;
		}
	}

	/** Iris has returned this as a bare String in the past and an Optional&lt;String&gt;
	 * since; reflection cannot pick an overload, so both shapes are handled explicitly
	 * rather than guessing which one the installed build uses. */
	private static String packName(Object result) {
		if (result instanceof Optional<?> optional) {
			return optional.map(Object::toString).orElse(null);
		}
		return result == null ? null : result.toString();
	}
}
