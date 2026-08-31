package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link CaptureContext#capture()} needs a live {@code Minecraft} instance and is covered by
 * the gametest suite instead; this pins the tag encoding/decoding, which is what every
 * screenshot taken before this feature shipped, or by vanilla F2, has to degrade gracefully
 * against.
 */
class CaptureContextTest {

	@Test
	void roundTripsThroughTags() {
		CaptureContext context = new CaptureContext("minecraft:the_nether", 12, 70, -45, "New World");

		assertEquals(context, CaptureContext.fromTags(context.toTags()));
	}

	@Test
	void worldNameIsOmittedFromTagsWhenAbsent() {
		CaptureContext context = new CaptureContext("minecraft:overworld", 0, 64, 0, null);

		assertEquals(Map.of(
				"voxelcam:dimension", "minecraft:overworld",
				"voxelcam:x", "0",
				"voxelcam:y", "64",
				"voxelcam:z", "0"),
				context.toTags());
	}

	@Test
	void fromTagsIsNullWhenDimensionIsMissing() {
		assertNull(CaptureContext.fromTags(Map.of("voxelcam:x", "1", "voxelcam:y", "2", "voxelcam:z", "3")));
	}

	@Test
	void fromTagsIsNullWhenCoordinatesAreMissingOrUnparsable() {
		assertNull(CaptureContext.fromTags(Map.of("voxelcam:dimension", "minecraft:overworld")));
		assertNull(CaptureContext.fromTags(Map.of(
				"voxelcam:dimension", "minecraft:overworld",
				"voxelcam:x", "not a number",
				"voxelcam:y", "2",
				"voxelcam:z", "3")));
	}

	@Test
	void fromTagsIsNullForAnEmptyMap() {
		assertNull(CaptureContext.fromTags(Map.of()));
	}

	@Test
	void describesLocationWithoutTheDimensionInTheOverworld() {
		CaptureContext context = new CaptureContext("minecraft:overworld", 123, 64, -45, null);

		assertEquals("123, 64, -45", context.describeLocation());
	}

	@Test
	void describesLocationWithAShortDimensionNameElsewhere() {
		CaptureContext nether = new CaptureContext("minecraft:the_nether", 12, 70, -8, null);
		assertEquals("the_nether 12, 70, -8", nether.describeLocation());

		CaptureContext modded = new CaptureContext("somemod:mining_dimension", 1, 2, 3, null);
		assertEquals("mining_dimension 1, 2, 3", modded.describeLocation());
	}
}
