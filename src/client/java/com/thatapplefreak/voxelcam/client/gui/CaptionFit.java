package com.thatapplefreak.voxelcam.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * What a list row and the details line under the preview say when they cannot say everything —
 * which at the default window is most of the time: a row has about 80 GUI pixels for its text.
 *
 * <p>The rule: a line is a list of whole facts, each with a rank. While the line is too wide, the
 * lowest-ranked fact still shown is dropped, and an ellipsis stands where it was. A fact is never
 * cut inside, except the last one standing when even it does not fit, and then only if it is
 * free text (a name, an advancement title) — cut with a trailing ellipsis. A number never loses
 * its unit. Which fact outranks which is decided in the three builders below, and nowhere else.
 *
 * <p>Free of Minecraft types, with text width supplied by the caller, so {@code CaptionFitTest}
 * can sweep widths without a font — the reason {@code FilmstripLayout} sits beside the manager.
 */
final class CaptionFit {

	static final String ELLIPSIS = "…";
	/** Between a capture's day and its time, which read as one name when both fit. */
	static final String NAME_SEPARATOR = " ";
	/** A row has the least room on the screen, so its facts sit closer than the details line's. */
	static final String ROW_SEPARATOR = " · ";
	static final String DETAILS_SEPARATOR = "  ·  ";

	/** @param rank lower is kept longer. */
	record Fact(String text, int rank, boolean cuttable) {
	}

	private CaptionFit() {
	}

	/**
	 * A row's first line. A capture's name is its day and its time to the second, and the time
	 * outranks the day: the list is usually sorted by date, so neighbours share the day, and the
	 * seconds are what tell apart the screenshots taken in the same minute. A name the player gave
	 * is one fact, cut with an ellipsis if it must be.
	 *
	 * @param day null for a name the player gave, which is then all of {@code name}.
	 */
	static List<Fact> rowName(String day, String name) {
		if (day == null) {
			return List.of(new Fact(name, 0, true));
		}
		return List.of(new Fact(day, 1, false), new Fact(name, 0, false));
	}

	/**
	 * A row's second line: the dimensions outrank the size. A big screenshot is told from its
	 * neighbours by its dimensions; the size is on the details line as well.
	 *
	 * @param dimensions null when the header would not read.
	 */
	static List<Fact> rowFacts(String dimensions, String size) {
		List<Fact> facts = new ArrayList<>(2);
		if (dimensions != null) {
			facts.add(new Fact(dimensions, 0, false));
		}
		facts.add(new Fact(size, 1, false));
		return facts;
	}

	/**
	 * The line under the preview, in rank order, so what is dropped goes from the end. What the
	 * image above cannot show comes first: that it is an animation, then why an automatic capture
	 * exists, then where it was taken. Dimensions and size follow, and the file name is last — the
	 * row already shows it, or the time it was taken in its place.
	 *
	 * <p>Every argument but the file name may be null for a screenshot that has no such fact.
	 */
	static List<Fact> details(String animation, String moment, String location,
			String dimensions, String size, String fileName) {
		List<Fact> facts = new ArrayList<>(6);
		addIfPresent(facts, animation, false);
		addIfPresent(facts, moment, true);
		addIfPresent(facts, location, false);
		addIfPresent(facts, dimensions, false);
		addIfPresent(facts, size, false);
		addIfPresent(facts, fileName, true);
		return facts;
	}

	private static void addIfPresent(List<Fact> facts, String text, boolean cuttable) {
		if (text != null && !text.isEmpty()) {
			facts.add(new Fact(text, facts.size(), cuttable));
		}
	}

	/**
	 * The facts as they fit in {@code width}, by the rule in the class comment. Costs one width
	 * measurement when everything fits, which is the case on a wide window — this runs for every
	 * visible row on every extracted frame.
	 */
	static String fit(List<Fact> facts, String separator, int width, ToIntFunction<String> widthOf) {
		if (facts.isEmpty()) {
			return "";
		}
		boolean[] shown = new boolean[facts.size()];
		java.util.Arrays.fill(shown, true);
		int remaining = facts.size();
		while (true) {
			String line = render(facts, shown, separator);
			if (widthOf.applyAsInt(line) <= width) {
				return line;
			}
			if (remaining == 1) {
				break;
			}
			shown[lowestRankedShown(facts, shown)] = false;
			remaining--;
		}
		Fact last = null;
		for (int i = 0; i < facts.size(); i++) {
			if (shown[i]) {
				last = facts.get(i);
			}
		}
		if (last.cuttable()) {
			return cut(last.text(), width, widthOf);
		}
		return widthOf.applyAsInt(ELLIPSIS) <= width ? ELLIPSIS : "";
	}

	/** Ties go to the later fact, so equal ranks drop from the end. */
	private static int lowestRankedShown(List<Fact> facts, boolean[] shown) {
		int worst = -1;
		for (int i = 0; i < facts.size(); i++) {
			if (shown[i] && (worst < 0 || facts.get(i).rank() >= facts.get(worst).rank())) {
				worst = i;
			}
		}
		return worst;
	}

	/** Shown facts joined by the separator, with one ellipsis for each run of dropped ones. */
	private static String render(List<Fact> facts, boolean[] shown, String separator) {
		StringBuilder line = new StringBuilder();
		boolean inDroppedRun = false;
		for (int i = 0; i < facts.size(); i++) {
			if (!shown[i] && inDroppedRun) {
				continue;
			}
			inDroppedRun = !shown[i];
			if (!line.isEmpty()) {
				line.append(separator);
			}
			line.append(shown[i] ? facts.get(i).text() : ELLIPSIS);
		}
		return line.toString();
	}

	/** The longest prefix that still fits with the ellipsis after it, found by bisection. */
	private static String cut(String text, int width, ToIntFunction<String> widthOf) {
		int fits = 0;
		int doesNot = text.length();
		while (doesNot - fits > 1) {
			int mid = (fits + doesNot) >>> 1;
			if (widthOf.applyAsInt(text.substring(0, mid).stripTrailing() + ELLIPSIS) <= width) {
				fits = mid;
			} else {
				doesNot = mid;
			}
		}
		if (fits == 0) {
			return widthOf.applyAsInt(ELLIPSIS) <= width ? ELLIPSIS : "";
		}
		// Never split a surrogate pair: half of one draws as a missing glyph.
		if (Character.isHighSurrogate(text.charAt(fits - 1))) {
			fits--;
		}
		return text.substring(0, fits).stripTrailing() + ELLIPSIS;
	}
}
