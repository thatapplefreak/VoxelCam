package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamConfig;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * The four vanilla-visible signals {@link AutoCapture} arms a screenshot on. Each is a call site
 * rather than a subsystem — the enum is what keeps "one integration point per trigger type" from
 * turning into one mechanism per trigger type, since the dispatcher and the settings screen both
 * iterate {@link #values()} and never name a trigger individually.
 *
 * <p>Each constant points at its own flat {@code boolean} on {@link VoxelCamConfig} through a
 * getter/setter pair rather than being looked up by name. Flat fields keep {@code voxelcam.json}
 * readable and stable the way the four settings before these are; the lambdas are what buy uniform
 * iteration back on top of them. The same trick {@link SortMode} plays with its key extractors.
 *
 * <p>Unlike {@code Burst.getLength()} or {@code BigScreenshot.getSize()}, these have no
 * session-static holder to mirror — like {@code favoritesOnly}, the config <em>is</em> where they
 * live, so {@link #isEnabled()} reads {@link VoxelCamConfig#current()} directly instead of keeping
 * a second copy that could drift.
 */
public enum MomentTrigger {

	ADVANCEMENT("advancement", c -> c.autoCaptureAdvancement, (c, on) -> c.autoCaptureAdvancement = on),
	BOSS_DEFEATED("bossdefeated", c -> c.autoCaptureBossDefeat, (c, on) -> c.autoCaptureBossDefeat = on),
	DEATH("death", c -> c.autoCaptureDeath, (c, on) -> c.autoCaptureDeath = on),
	NEW_DIMENSION("newdimension", c -> c.autoCaptureNewDimension, (c, on) -> c.autoCaptureNewDimension = on);

	/**
	 * Stable across releases: it is written into the PNG as {@code voxelcam:trigger} and read back
	 * by {@link MomentTag#fromTags}, so renaming one silently orphans every screenshot already
	 * tagged with it. The enum constant may be renamed freely; this may not.
	 */
	private final String token;
	private final String labelKey;
	private final Predicate<VoxelCamConfig> enabled;
	private final BiConsumer<VoxelCamConfig, Boolean> setEnabled;

	MomentTrigger(String token, Predicate<VoxelCamConfig> enabled, BiConsumer<VoxelCamConfig, Boolean> setEnabled) {
		this.token = token;
		this.labelKey = "voxelcam.moment." + token;
		this.enabled = enabled;
		this.setEnabled = setEnabled;
	}

	public String token() {
		return token;
	}

	public String labelKey() {
		return labelKey;
	}

	public String tooltipKey() {
		return "voxelcam.tooltip.moment." + token;
	}

	public boolean isEnabled() {
		return enabled.test(VoxelCamConfig.current());
	}

	/**
	 * Writes straight onto {@link VoxelCamConfig#current()}. Deliberately does not save — the same
	 * split {@code Burst.setLength} and {@code SortMode.setCurrent} keep, so that nothing here
	 * reaches {@code FabricLoader} from a no-client unit suite. The settings screen calls {@code
	 * VoxelCamConfig.saveCurrent()} itself, which is where a change actually happens at the
	 * player's hand.
	 */
	public void setEnabled(boolean on) {
		setEnabled.accept(VoxelCamConfig.current(), on);
	}

	/** @return null for an unknown token — a tag written by a future version, or a hand-edited file. */
	public static MomentTrigger fromToken(String token) {
		if (token == null) {
			return null;
		}
		for (MomentTrigger trigger : values()) {
			if (trigger.token.equals(token)) {
				return trigger;
			}
		}
		return null;
	}
}
