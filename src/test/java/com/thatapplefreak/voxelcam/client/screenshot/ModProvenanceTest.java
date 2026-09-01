package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ModProvenance#capture()} needs a live {@code FabricLoader} and is covered by the
 * gametest suite instead; this pins the tag formatting — curation and truncation happen
 * before this record is built, so what is left here is just turning a mod list into text.
 */
class ModProvenanceTest {

	@Test
	void listsModsAsASingleCommaJoinedTag() {
		ModProvenance provenance = new ModProvenance(List.of("sodium", "lithium"), null);

		assertEquals(Map.of("voxelcam:mods", "sodium, lithium"), provenance.toTags());
	}

	@Test
	void emptyModListAndNoShaderPackProduceNoTags() {
		assertEquals(Map.of(), new ModProvenance(List.of(), null).toTags());
	}

	@Test
	void shaderPackIsOmittedWhenNullOrEmpty() {
		assertEquals(Map.of(), new ModProvenance(List.of(), null).toTags());
		assertEquals(Map.of(), new ModProvenance(List.of(), "").toTags());
	}

	@Test
	void shaderPackIsIncludedAlongsideMods() {
		ModProvenance provenance = new ModProvenance(List.of("sodium"), "Complementary Reimagined");

		assertEquals(Map.of("voxelcam:mods", "sodium", "voxelcam:shaderpack", "Complementary Reimagined"),
				provenance.toTags());
	}

	@Test
	void shaderPackAloneIsValidWithNoMods() {
		assertEquals(Map.of("voxelcam:shaderpack", "BSL"), new ModProvenance(List.of(), "BSL").toTags());
	}

	/** A modpack's worth of ids must not balloon the tag into an unreadable wall of text. */
	@Test
	void aLongModListIsTruncatedWithACount() {
		List<String> mods = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			mods.add(String.format("some-gameplay-mod-%03d", i));
		}

		String tag = new ModProvenance(mods, null).toTags().get("voxelcam:mods");

		assertTrue(tag.contains("some-gameplay-mod-000"), tag);
		assertFalse(tag.contains("some-gameplay-mod-099"), tag);
		assertTrue(tag.matches(".*\\(\\+\\d+ more\\)$"), tag);
	}

	/** A list that already fits needs no truncation marker at all. */
	@Test
	void aShortModListIsNotTruncated() {
		List<String> mods = List.of("sodium", "lithium", "iris");

		String tag = new ModProvenance(mods, null).toTags().get("voxelcam:mods");

		assertEquals("sodium, lithium, iris", tag);
	}
}
