package dev.noctud.latte.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In which Latte versions one tag or one filter exists.
 *
 * Shaped after the reference tables rather than after anything convenient: a row there says, per
 * minor line, either that the item is absent, or that it is present, or the patch it arrived in.
 * Keeping that shape means a line added to the tables needs no change here.
 *
 * The two edges are where the care is. Above the newest documented line the answer is <b>yes</b>,
 * not "unknown": a Latte released after these tables were written will have added things and
 * removed almost nothing, so refusing to recognise them would report what is correct. Below the
 * oldest documented line the answer is <b>yes</b> as well, for the plainer reason that the plugin
 * does not claim to know that Latte at all. Silence outside the documented range is the rule, and
 * both edges obey it.
 */
public final class LatteAvailability {

	/** Present for the whole line, with no patch to wait for. */
	static final String WHOLE_LINE = "";

	/** Available everywhere - what an item with no version columns of its own gets. */
	public static final LatteAvailability ALWAYS = new LatteAvailability(Collections.emptyMap(), true, false);

	/**
	 * In no line the tables describe. A row that says "no" three times is kept there for history -
	 * {@code &#123;?&#125;} was removed in 2.11.0 and is listed because 2.10 templates still hold
	 * it - so it exists in the documentation and not in the language.
	 */
	public static final LatteAvailability NEVER = new LatteAvailability(Collections.emptyMap(), false, true);

	/** Line ("3.1") to the patch it arrived in, or {@link #WHOLE_LINE}. A line absent means absent. */
	private final Map<String, String> lines;

	private final boolean everywhere;

	private final boolean nowhere;

	private LatteAvailability(@NotNull Map<String, String> lines, boolean everywhere, boolean nowhere) {
		this.lines = lines;
		this.everywhere = everywhere;
		this.nowhere = nowhere;
	}

	static @NotNull LatteAvailability inLines(@NotNull Map<String, String> lines) {
		return lines.isEmpty() ? NEVER : new LatteAvailability(Map.copyOf(lines), false, false);
	}

	/**
	 * Both rows about one name, read as one answer: present wherever either of them says present.
	 *
	 * The tables describe the language, and one name in the registry can be described by more than
	 * one row of it. {@code &#123;attr&#125;} the tag was dropped in Latte 3 while {@code n:attr}
	 * the attribute was not, and the registry knows a single entry that serves both. Taking either
	 * row alone would withhold that entry from half the templates that legitimately use it.
	 *
	 * Union is the only safe direction. Withholding is what produces a report, so a disagreement
	 * between two rows has to resolve towards saying nothing.
	 */
	@NotNull LatteAvailability or(@NotNull LatteAvailability other) {
		if (everywhere || other.everywhere) {
			return ALWAYS;
		}
		if (nowhere && other.nowhere) {
			return NEVER;
		}
		Map<String, String> union = new LinkedHashMap<>(lines);
		for (Map.Entry<String, String> entry : other.lines.entrySet()) {
			union.merge(entry.getKey(), entry.getValue(), LatteAvailability::earlier);
		}
		return inLines(union);
	}

	/** Of two boundaries within one line, the one that lets more versions through. */
	private static @NotNull String earlier(@NotNull String left, @NotNull String right) {
		if (WHOLE_LINE.equals(left) || WHOLE_LINE.equals(right)) {
			return WHOLE_LINE;
		}
		return compare(left, right) <= 0 ? left : right;
	}

	/**
	 * @param version         the version in hand
	 * @param documentedLines every line the tables describe, newest last. It has to come from the
	 *                        tables and not from this item, because "this item is absent from 3.0"
	 *                        and "the tables say nothing about 3.2" are opposite answers and an
	 *                        item's own lines cannot tell them apart.
	 */
	public boolean covers(@NotNull LatteVersion version, @NotNull List<String> documentedLines) {
		if (everywhere) {
			return true;
		}
		if (version.isUndetermined()) {
			// Nothing is known about the project, so nothing is withheld - except what the tables
			// say is in no version at all, which no project can have.
			return !nowhere;
		}
		if (nowhere) {
			return false;
		}
		String line = version.line();
		if (line == null || documentedLines.isEmpty()) {
			return true;
		}
		String newest = documentedLines.get(documentedLines.size() - 1);
		if (isNewerThan(line, newest)) {
			line = newest;
		} else if (!documentedLines.contains(line)) {
			// Older than anything described, or a line between two described ones. Either way the
			// tables do not know it, and what is not known is not reported on.
			return true;
		}
		String arrivedIn = lines.get(line);
		if (arrivedIn == null) {
			return false;
		}
		if (WHOLE_LINE.equals(arrivedIn) || !version.hasPatchPrecision()) {
			return true;
		}
		String[] boundary = arrivedIn.split("\\.");
		return version.isAtLeast(parse(boundary[0]), parse(boundary[1]), boundary.length > 2 ? parse(boundary[2]) : 0);
	}

	private static boolean isNewerThan(@NotNull String line, @NotNull String other) {
		return compare(line, other) > 0;
	}

	static int compare(@NotNull String left, @NotNull String right) {
		String[] a = left.split("\\.");
		String[] b = right.split("\\.");
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? parse(a[i]) : 0;
			int y = i < b.length ? parse(b[i]) : 0;
			if (x != y) {
				return Integer.compare(x, y);
			}
		}
		return 0;
	}

	private static int parse(@NotNull String part) {
		try {
			return Integer.parseInt(part.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
