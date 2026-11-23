package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * The capture itself needs a live client — {@code beforeBlit()} reads {@code
 * Minecraft.getInstance()} on its very first line — and is covered by the gametest suite. What is
 * reachable here is the pure bookkeeping {@code beforeBlit()} is built on: which slot of the
 * 10-a-second schedule is due, when it considers itself done, none of which touches {@code
 * Minecraft} at all.
 */
class BurstTest {

	private static final int ORIGINAL_SECONDS = Burst.getSeconds();
	private static final int STEP = Burst.FRAME_INTERVAL_MILLIS;

	/** The state machine is static, so a test that leaves one running would follow the next one
	 * in; {@link Burst#setSeconds} is restored for the same reason a leaked locale is in {@code
	 * ScreenshotNamerTest} — a later test asking for the session default should get it. */
	@AfterEach
	void settle() {
		Burst.forgetBurst();
		Burst.setSeconds(ORIGINAL_SECONDS);
		CaptureStatus.forgetAll();
	}

	@Test
	void aBurstTakesOneFrameEveryTenthOfASecondUntilItsTimeIsUp() {
		Burst.beginBurst(5, 1000L);

		for (int slot = 0; slot < 5; slot++) {
			long due = 1000L + slot * STEP;
			assertEquals(slot, Burst.dueSlot(due), "slot " + slot + " is due at its own time");
			assertEquals(slot, Burst.claimFrame(Burst.dueSlot(due)));
			assertEquals(Burst.WAIT, Burst.dueSlot(due + STEP - 1), "nothing more until the next slot's time");
		}
		assertEquals(Burst.DONE, Burst.dueSlot(1000L + 5 * STEP), "five slots at ten a second is half a second");
	}

	@Test
	void aSlotMissedWhileTheCapIsFullIsSkippedNotCaughtUp() {
		Burst.beginBurst(10, 0L);
		assertEquals(0, Burst.claimFrame(Burst.dueSlot(0L)));

		// Slots 1 and 2 pass with every in-flight frame still encoding; the next blit that can
		// issue is in slot 3's time, and takes slot 3 — not slot 1, which would bunch the frames.
		long late = 3 * STEP + 40;
		assertEquals(3, Burst.dueSlot(late));
		assertEquals(1, Burst.claimFrame(Burst.dueSlot(late)), "the second frame written, whatever slot it filled");
		assertEquals(Burst.WAIT, Burst.dueSlot(late + 1));
		assertEquals(4, Burst.dueSlot(4 * STEP));
	}

	@Test
	void theHudHidesOnlyOnFramesTheBurstReadsBack() {
		Burst.beginBurst(10, 0L);
		// Armed from the blit: that frame was decided before the burst existed, and is taken live.
		assertTrue(Burst.capturesThisFrame(), "the frame a burst starts on is its key frame");
		assertEquals(0, Burst.takeFrameSlot(0L));
		Burst.claimFrame(0);

		Burst.settleFrame(16L);
		assertFalse(Burst.capturesThisFrame(), "between slots the recording cue may show");
		assertEquals(Burst.WAIT, Burst.takeFrameSlot(16L));

		Burst.settleFrame(STEP + 3);
		assertTrue(Burst.capturesThisFrame(), "the frame that takes the next slot hides it");
		assertEquals(1, Burst.takeFrameSlot(STEP + 20), "the blit acts on the head's decision");
	}

	@Test
	void aSlotThatComesDueAfterTheHeadWaitsForTheNextFrame() {
		Burst.beginBurst(10, 0L);
		Burst.claimFrame(Burst.takeFrameSlot(0L));

		Burst.settleFrame(STEP - 5);
		assertEquals(Burst.WAIT, Burst.takeFrameSlot(STEP + 5),
				"not due at the head: capturing anyway would put the cue in the frame");
	}

	@Test
	void secondsLeftCountsDownToTheEndOfTheBurst() {
		Burst.beginBurst(30, 1000L);
		assertEquals(3F, Burst.secondsLeft(1000L), 1e-6);
		assertEquals(0.5F, Burst.secondsLeft(3500L), 1e-6);
		assertEquals(0F, Burst.secondsLeft(9000L), 1e-6);
		Burst.finishBurst();
		assertEquals(0F, Burst.secondsLeft(1000L), 1e-6, "idle has nothing left");
	}

	@Test
	void secondsBecomeFramesAtTenASecond() {
		assertEquals(10, Burst.FRAMES_PER_SECOND);
		assertEquals(30, Burst.framesFor(3));
		assertEquals(Burst.MAX_FRAMES, Burst.framesFor(Burst.MAX_SECONDS));
		assertTrue(Burst.MAX_FRAMES <= 99, "frame names keep two digits");
	}

	@Test
	void everyRingOptionIsALengthTheSettingAccepts() {
		for (int seconds : Burst.DIAL_OPTIONS) {
			Burst.setSeconds(seconds);
			assertEquals(seconds, Burst.getSeconds());
		}
	}

	@Test
	void isBusyIsTrueOnlyWhileRunning() {
		assertFalse(Burst.isBusy(), "idle before anything is requested");

		Burst.beginBurst(3, 1000L);
		assertTrue(Burst.isBusy());

		Burst.finishBurst();
		assertFalse(Burst.isBusy(), "finishing returns the state machine to idle");
	}

	@Test
	void aSecondBurstWhileOneIsRecordingIsRefusedAsRecording() {
		Burst.beginBurst(3, 1000L);

		Burst.request();

		List<CaptureStatus.Notice> notices = CaptureStatus.visibleAt(Util.getMillis());
		assertEquals(1, notices.size());
		assertEquals("voxelcam.recordingpleasewait", notices.get(0).key());
	}

	@Test
	void offsetIsMeasuredFromTheBurstsOwnStart() {
		Burst.beginBurst(3, 1_000L);

		assertEquals(0, Burst.offsetAt(1_000L), "the key frame is offset zero from itself");
		assertEquals(420, Burst.offsetAt(1_420L));
	}

	/** Re-arming has to reset the counters, not carry them over from whatever ran before — a
	 * second burst in the same session must start counting frames and slots from zero again. */
	@Test
	void aFreshBurstStartsItsIndexAndScheduleAtZero() {
		Burst.beginBurst(4, 500L);
		Burst.claimFrame(Burst.dueSlot(500L));
		Burst.claimFrame(Burst.dueSlot(500L + STEP));

		Burst.beginBurst(4, 900L);

		assertEquals(0, Burst.dueSlot(900L), "re-arming must restart the schedule");
		assertEquals(0, Burst.claimFrame(0), "re-arming must reset the index, not continue the last burst's count");
	}

	@Test
	void setSecondsClampsToTheValidRange() {
		Burst.setSeconds(0);
		assertEquals(Burst.MIN_SECONDS, Burst.getSeconds(), "shorter than this cannot hold a moment from start to finish");

		Burst.setSeconds(Burst.MAX_SECONDS + 50);
		assertEquals(Burst.MAX_SECONDS, Burst.getSeconds());
	}

	/** {@code beginBurst} takes an explicit slot count so a test can drive an arbitrary run without
	 * touching the session preference {@link Burst#setSeconds} controls. */
	@Test
	void beginBurstDoesNotChangeTheSessionLengthPreference() {
		Burst.setSeconds(4);

		Burst.beginBurst(2, 1000L);

		assertEquals(4, Burst.getSeconds(), "the running burst's own length must not overwrite the session default");
	}

	/**
	 * {@code frameCompleted} and the state leaving {@code RUNNING} can arrive in either order —
	 * every frame can finish writing before the burst stops issuing, or the burst can stop
	 * issuing before its last frame reports in — and this is what previously kept the "saved"
	 * announcement pinned to whichever ordering frame 0 happened to land in first, rather than
	 * the whole group actually finishing. Neither ordering has an observable effect without a
	 * client ({@code names} stays null outside {@link Burst#request}, which is what keeps {@code
	 * announceCompletion} from ever posting a notice here), so this is a smoke
	 * test for the bookkeeping rather than the message itself — the gametest suite covers the
	 * real timing end to end.
	 */
	@Test
	void completionCanBeReportedBeforeOrAfterTheStateLeavesRunning() {
		Burst.beginBurst(2, 1000L);
		Burst.claimFrame(0);
		Burst.claimFrame(1);
		Burst.frameCompleted(true);
		Burst.frameCompleted(true);
		Burst.finishBurst();
		assertFalse(Burst.isBusy());

		Burst.beginBurst(2, 2000L);
		Burst.claimFrame(0);
		Burst.claimFrame(1);
		Burst.finishBurst();
		Burst.frameCompleted(true);
		Burst.frameCompleted(true);
		assertFalse(Burst.isBusy());
	}

	/** A readback failure mid-burst still has to report a completion of its own — see {@code
	 * ScreenshotHandler.captureBurstFrame}'s catch — or the group never finishes accounting for
	 * every frame it claimed and the completion check waits forever. */
	@Test
	void aFailedFrameStillCountsAsCompleted() {
		Burst.beginBurst(2, 1000L);
		Burst.claimFrame(0);
		Burst.claimFrame(1);
		Burst.frameCompleted(false);
		Burst.frameCompleted(true);
		Burst.finishBurst();
		assertFalse(Burst.isBusy());
	}

	/** Drives a 3 s burst that keeps up with its schedule for its first {@code frames} slots. */
	private static void issueFrames(int frames) {
		Burst.beginBurst(Burst.framesFor(3), 0L);
		for (int slot = 0; slot < frames; slot++) {
			Burst.claimFrame(Burst.dueSlot((long) slot * STEP));
		}
	}

	/** The playtest: a 3 s burst, the player killed about three quarters of a second in. The
	 * death screen stops it by design; what the player is owed is being told so. */
	@Test
	void aBurstStoppedBeforeItsLengthSaysItWasCutShortWithItsRealFrameCount() {
		issueFrames(8);
		Burst.abortAt("the death screen opened", Burst.CUT_SHORT_BY_GATE, 750L);
		for (int i = 0; i < 8; i++) {
			Burst.frameCompleted(true);
		}

		CaptureStatus.Notice notice = Burst.completionNotice("key.png", 0L);
		assertEquals(CaptureStatus.Kind.SAVED, notice.kind(), "the frames it did take were saved");
		assertEquals("voxelcam.burst.cutshort", notice.key());
		assertEquals(List.of(8, "key.png"), notice.args());
	}

	@Test
	void aBurstStoppedByAFailedReadbackSaysSoToo() {
		issueFrames(5);
		Burst.frameCompleted(false);
		Burst.abortAt("a readback failed", Burst.CUT_SHORT_BY_FAILURE, 450L);
		for (int i = 0; i < 4; i++) {
			Burst.frameCompleted(true);
		}

		CaptureStatus.Notice notice = Burst.completionNotice("key.png", 0L);
		assertEquals(CaptureStatus.Kind.SAVED, notice.kind());
		assertEquals("voxelcam.burst.cutshort.failed", notice.key());
		assertEquals(List.of(4, "key.png"), notice.args(), "only the frames that landed are counted");
	}

	@Test
	void aBurstThatRanItsLengthAnnouncesAsBeforeEvenWithSkippedSlots() {
		Burst.beginBurst(Burst.framesFor(3), 0L);
		int issued = 0;
		for (int slot = 0; slot < Burst.framesFor(3); slot++) {
			// Every third slot passes with the in-flight cap full: skipped, not an early end.
			if (slot % 3 != 2) {
				Burst.claimFrame(Burst.dueSlot((long) slot * STEP));
				issued++;
			}
		}
		assertEquals(Burst.DONE, Burst.dueSlot(3000L));
		Burst.finishBurst();
		for (int i = 0; i < issued; i++) {
			Burst.frameCompleted(true);
		}

		CaptureStatus.Notice notice = Burst.completionNotice("key.png", 0L);
		assertEquals(CaptureStatus.Kind.SAVED, notice.kind());
		assertEquals("voxelcam.burst.saved", notice.key());
		assertEquals(List.of(20, "key.png"), notice.args());
	}

	/** Stopped after its last slot had come due, a burst lost nothing to the stop: every slot it
	 * had was already taken or skipped, so calling it cut short would be the wrong lie instead. */
	@Test
	void aBurstStoppedInItsLastSlotRanItsLength() {
		issueFrames(Burst.framesFor(3));
		Burst.abortAt("a screen opened", Burst.CUT_SHORT_BY_GATE, 2950L);
		for (int i = 0; i < Burst.framesFor(3); i++) {
			Burst.frameCompleted(true);
		}

		assertEquals("voxelcam.burst.saved", Burst.completionNotice("key.png", 0L).key());
	}

	@Test
	void aBurstCutShortWithNothingSavedStillReportsAFailure() {
		issueFrames(2);
		Burst.abortAt("a screen opened", Burst.CUT_SHORT_BY_GATE, 150L);
		Burst.frameCompleted(false);
		Burst.frameCompleted(false);

		CaptureStatus.Notice notice = Burst.completionNotice("key.png", 0L);
		assertEquals(CaptureStatus.Kind.FAILED, notice.kind());
		assertEquals("voxelcam.burst.failed", notice.key());
	}

	@Test
	void aBurstStoppedBeforeItsFirstFrameHasNothingToAnnounce() {
		Burst.beginBurst(Burst.framesFor(3), 0L);
		Burst.abortAt("a screen opened", Burst.CUT_SHORT_BY_GATE, 0L);

		assertNull(Burst.completionNotice("key.png", 0L));
	}

	/** The default font's advance per character, from vanilla's {@code ascii.png}: most glyphs are
	 * 5 pixels and a gap. The same table {@code CaptionFitTest} measures with. */
	private static final ToIntFunction<String> FONT = text -> text.codePoints().map(c -> switch (c) {
		case ' ', 't', 'I', '(', ')', '[', ']' -> 4;
		case 'l', '`' -> 3;
		case ':', '.', ',', 'i', '!', '|', '\'', ';' -> 2;
		case 'k', 'f', '<', '>' -> 5;
		default -> 6;
	}).sum();

	/**
	 * The default 854x480 window at GUI scale 2 is 427 wide: a notice's text starts at x=11 and its
	 * plate may reach x=423. The file name is the end of the line and the one part a player reads
	 * the notice for, so a line the window cuts loses exactly that.
	 */
	private static final int NOTICE_TEXT_WIDTH = 423 - 11;

	@Test
	void everyEndOfBurstNoticeFitsTheDefaultWindowWithItsFileName() throws IOException {
		for (String lang : List.of("en_us", "en_pt")) {
			JsonObject strings = lang(lang);
			for (String key : List.of("voxelcam.burst.saved", Burst.CUT_SHORT_BY_GATE, Burst.CUT_SHORT_BY_FAILURE)) {
				String line = String.format(Locale.ROOT, strings.get(key).getAsString(),
						99, "2026-11-23_09.02.57_1.png");
				assertTrue(FONT.applyAsInt(line) <= NOTICE_TEXT_WIDTH,
						lang + " " + key + " is " + FONT.applyAsInt(line) + " wide: " + line);
			}
		}
	}

	private static JsonObject lang(String name) throws IOException {
		try (InputStream in = BurstTest.class.getResourceAsStream("/assets/voxelcam/lang/" + name + ".json")) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
