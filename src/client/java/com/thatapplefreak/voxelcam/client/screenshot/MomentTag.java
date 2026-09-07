package com.thatapplefreak.voxelcam.client.screenshot;

import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Why a screenshot exists, for the ones nobody pressed a key for: which {@link MomentTrigger}
 * armed it, and what it was about — the advancement's title, the boss bar's name. Embedded into
 * the file itself as PNG text tags (see {@link PngTextChunk}) at capture time, the same way {@link
 * CaptureContext} and {@link BurstFrame} are, so it survives rename, copy and share.
 *
 * <p>{@link #subject} is plain text on purpose. It comes from a {@code Component}, and the
 * decorated form of an advancement name carries hover data and formatting that have no business in
 * a Latin-1 PNG text chunk — callers pass {@code getString()} of the plain title, not
 * {@code Advancement.name(holder)}.
 */
public record MomentTag(MomentTrigger trigger, String subject) {

	private static final String TRIGGER_KEY = "voxelcam:trigger";
	private static final String SUBJECT_KEY = "voxelcam:momentsubject";

	/** How the moment reads to the player, in chat when it lands and in the manager afterwards.
	 * One method rather than two so the screenshot the player was just told about is described the
	 * same way when they go looking for it. */
	public Component describe() {
		Component label = Component.translatable(trigger.labelKey());
		return subject == null || subject.isEmpty()
				? label
				: Component.translatable("voxelcam.moment.subject", label, subject);
	}

	public Map<String, String> toTags() {
		Map<String, String> tags = new LinkedHashMap<>();
		tags.put(TRIGGER_KEY, trigger.token());
		// Optional, like CaptureContext's world name: a death has no subject worth recording, and an
		// empty tag reads back as a caption the manager would then draw as a blank.
		if (subject != null && !subject.isEmpty()) {
			tags.put(SUBJECT_KEY, subject);
		}
		return tags;
	}

	/**
	 * @return null rather than a half-built record if the trigger tag is missing or unrecognised —
	 * every screenshot the player took by hand, which is most of them, simply has no tag at all.
	 */
	public static MomentTag fromTags(Map<String, String> tags) {
		MomentTrigger trigger = MomentTrigger.fromToken(tags.get(TRIGGER_KEY));
		if (trigger == null) {
			return null;
		}
		return new MomentTag(trigger, tags.get(SUBJECT_KEY));
	}
}
