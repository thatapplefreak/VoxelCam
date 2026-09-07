package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins the tag encoding/decoding {@link CaptureContext}'s tests already pin for its own record —
 * what every screenshot that isn't part of a burst, which is most of them, has to degrade
 * gracefully against.
 */
class BurstFrameTest {

	@Test
	void roundTripsThroughTags() {
		BurstFrame frame = new BurstFrame("2026-09-05_14.30.00", 3, 420);

		assertEquals(frame, BurstFrame.fromTags(frame.toTags()));
	}

	@Test
	void toTagsMatchesTheHouseKeyShape() {
		BurstFrame frame = new BurstFrame("2026-09-05_14.30.00", 0, 0);

		assertEquals(Map.of(
				"voxelcam:burst", "2026-09-05_14.30.00",
				"voxelcam:burstindex", "0",
				"voxelcam:burstoffset", "0"),
				frame.toTags());
	}

	@Test
	void fromTagsIsNullWhenTheGroupIdIsMissing() {
		assertNull(BurstFrame.fromTags(Map.of("voxelcam:burstindex", "1", "voxelcam:burstoffset", "100")));
	}

	@Test
	void fromTagsIsNullWhenIndexOrOffsetAreMissingOrUnparsable() {
		assertNull(BurstFrame.fromTags(Map.of("voxelcam:burst", "key")));
		assertNull(BurstFrame.fromTags(Map.of(
				"voxelcam:burst", "key",
				"voxelcam:burstindex", "not a number",
				"voxelcam:burstoffset", "0")));
	}

	@Test
	void fromTagsIsNullForAnEmptyMap() {
		assertNull(BurstFrame.fromTags(Map.of()));
	}
}
