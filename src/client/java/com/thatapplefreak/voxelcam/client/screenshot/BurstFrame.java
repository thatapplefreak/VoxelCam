package com.thatapplefreak.voxelcam.client.screenshot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where a screenshot sits within a burst: which group, which position, and how long after the
 * key frame it was taken. Embedded into the file itself as PNG text tags (see {@link
 * PngTextChunk}) at capture time, the same way {@link CaptureContext} is, so it survives rename,
 * copy and share — and so it needs no {@code Favorite.setStarred}-style mtime restoration, since
 * nothing rewrites the file after the fact.
 *
 * <p>Deliberately carries no frame count: nothing reads one back — the manager's burst badge
 * counts frames from a free directory-name scan instead, and {@code Burst} tracks how many
 * frames it actually issued itself, for its own end-of-burst chat confirmation — and a count
 * embedded here would lie about the group's size after an aborted burst.
 */
public record BurstFrame(String groupId, int index, int offsetMillis) {

	private static final String GROUP_KEY = "voxelcam:burst";
	private static final String INDEX_KEY = "voxelcam:burstindex";
	private static final String OFFSET_KEY = "voxelcam:burstoffset";

	public Map<String, String> toTags() {
		Map<String, String> tags = new LinkedHashMap<>();
		tags.put(GROUP_KEY, groupId);
		tags.put(INDEX_KEY, Integer.toString(index));
		tags.put(OFFSET_KEY, Integer.toString(offsetMillis));
		return tags;
	}

	/**
	 * @return null rather than a half-built record if the required tags are missing or
	 * unparsable — every screenshot that isn't part of a burst, which is most of them, simply has
	 * no tags at all.
	 */
	public static BurstFrame fromTags(Map<String, String> tags) {
		String groupId = tags.get(GROUP_KEY);
		if (groupId == null) {
			return null;
		}
		try {
			int index = Integer.parseInt(tags.get(INDEX_KEY));
			int offsetMillis = Integer.parseInt(tags.get(OFFSET_KEY));
			return new BurstFrame(groupId, index, offsetMillis);
		} catch (NumberFormatException | NullPointerException e) {
			return null;
		}
	}
}
