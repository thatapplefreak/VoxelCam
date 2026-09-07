package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The same round-trip {@code BurstFrameTest} and {@code CaptureContextTest} cover for their own
 * tags, including the tolerance every reader of a PNG somebody else wrote needs. */
class MomentTagTest {

	@Test
	void roundTripsThroughItsTags() {
		MomentTag original = new MomentTag(MomentTrigger.ADVANCEMENT, "Stone Age");

		MomentTag read = MomentTag.fromTags(original.toTags());

		assertEquals(original, read);
	}

	@Test
	void aMomentWithNothingToSayAboutItselfStillRoundTrips() {
		MomentTag original = new MomentTag(MomentTrigger.DEATH, null);

		Map<String, String> tags = original.toTags();

		assertFalse(tags.containsValue(""), "an empty caption is worse than none: the manager would draw it");
		assertEquals(original, MomentTag.fromTags(tags));
	}

	@Test
	void anEmptySubjectIsTreatedAsNoSubject() {
		MomentTag read = MomentTag.fromTags(new MomentTag(MomentTrigger.BOSS_DEFEATED, "").toTags());

		assertEquals(new MomentTag(MomentTrigger.BOSS_DEFEATED, null), read);
	}

	/** Most screenshots are ones the player took by hand, and every screenshot taken before this
	 * feature shipped has no tag at all. */
	@Test
	void aScreenshotWithNoMomentTagReadsAsNoMoment() {
		assertNull(MomentTag.fromTags(new HashMap<>()));
	}

	/** A tag written by a future version, or a hand-edited file: unreadable is not a reason to throw
	 * on a file the manager still has to list. */
	@Test
	void anUnknownTriggerTokenReadsAsNoMoment() {
		Map<String, String> tags = new HashMap<>();
		tags.put("voxelcam:trigger", "somethingtheversionafterthisoneadded");
		tags.put("voxelcam:momentsubject", "whatever it was");

		assertNull(MomentTag.fromTags(tags));
	}

	/** The tokens are written into files and read back out of them, so renaming one silently orphans
	 * every screenshot already carrying it. This is the tripwire for that. */
	@Test
	void everyTriggerHasAStableDistinctToken() {
		Map<String, MomentTrigger> byToken = new HashMap<>();
		for (MomentTrigger trigger : MomentTrigger.values()) {
			assertNull(byToken.put(trigger.token(), trigger), "two triggers share the token " + trigger.token());
			assertEquals(trigger, MomentTrigger.fromToken(trigger.token()));
		}
		assertEquals("advancement", MomentTrigger.ADVANCEMENT.token());
		assertEquals("bossdefeated", MomentTrigger.BOSS_DEFEATED.token());
		assertEquals("death", MomentTrigger.DEATH.token());
		assertEquals("newdimension", MomentTrigger.NEW_DIMENSION.token());
	}
}
