package dev.noctud.latte.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The versions one column of a reference table stands for.
 *
 * <p>The tag, filter and function tables head their columns with a minor line - {@code 2.11},
 * {@code 3.1} - and for a long time that was all the reader could take. The {@code {syntax}}
 * argument table needs more, because what it describes changed inside a line: {@code latte} is
 * accepted in 3.0.0 and 3.0.1 and refused from 3.0.2 on. Its header says so already, in
 * {@code 3.0.0-3.0.1} and {@code 3.0.24+}, and the whole table was skipped for want of
 * understanding it.
 *
 * <p>Three spellings, and nothing else is read: a bare line, a closed range with both ends named,
 * and an open one that runs to the end of the line it starts in. There is deliberately no spelling
 * for "from here on for ever" - a column of a table describes a stretch of the language somebody
 * has looked at, and the edge past the last documented line is a question the table cannot answer.
 */
public final class LatteVersionRange {

	private static final Pattern LINE = Pattern.compile("(\\d+)\\.(\\d+)");

	private static final Pattern CLOSED =
		Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\s*-\\s*(\\d+)\\.(\\d+)\\.(\\d+)");

	private static final Pattern OPEN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\+");

	private final int major;

	private final int minor;

	/** First patch in the range, inclusive. */
	private final int fromPatch;

	/** Last patch in the range, inclusive, or {@link Integer#MAX_VALUE} to the end of the line. */
	private final int toPatch;

	private LatteVersionRange(int major, int minor, int fromPatch, int toPatch) {
		this.major = major;
		this.minor = minor;
		this.fromPatch = fromPatch;
		this.toPatch = toPatch;
	}

	/** The range that column stands for, or null when the column is not one. */
	public static @Nullable LatteVersionRange parse(@NotNull String column) {
		String text = column.trim();

		Matcher closed = CLOSED.matcher(text);
		if (closed.matches() && closed.group(1).equals(closed.group(4)) && closed.group(2).equals(closed.group(5))) {
			return new LatteVersionRange(number(closed.group(1)), number(closed.group(2)),
				number(closed.group(3)), number(closed.group(6)));
		}
		Matcher open = OPEN.matcher(text);
		if (open.matches()) {
			return new LatteVersionRange(number(open.group(1)), number(open.group(2)),
				number(open.group(3)), Integer.MAX_VALUE);
		}
		Matcher line = LINE.matcher(text);
		if (line.matches()) {
			return new LatteVersionRange(number(line.group(1)), number(line.group(2)), 0, Integer.MAX_VALUE);
		}
		return null;
	}

	/** The whole minor line this range lies in - every range lies in exactly one. */
	public @NotNull String line() {
		return major + "." + minor;
	}

	/** Whether this range is the whole of its line rather than a piece of it. */
	public boolean isWholeLine() {
		return fromPatch == 0 && toPatch == Integer.MAX_VALUE;
	}

	/**
	 * Whether the version is certainly inside the range.
	 *
	 * <p>A version known only to its line is inside only when the range holds that whole line.
	 * Where the range holds part of it the answer is no rather than yes, and {@link #touches} is
	 * how a caller tells that apart from a version the range plainly excludes - the difference
	 * between "not in this column" and "cannot say", which is the difference between reporting and
	 * keeping quiet.
	 */
	public boolean contains(@NotNull LatteVersion version) {
		if (!isSameLine(version)) {
			return false;
		}
		if (!version.hasPatchPrecision()) {
			return isWholeLine();
		}
		return version.isAtLeast(major, minor, fromPatch)
			&& (toPatch == Integer.MAX_VALUE || !version.isAtLeast(major, minor, toPatch + 1));
	}

	/** Whether any version this range holds could be the one in hand. */
	public boolean touches(@NotNull LatteVersion version) {
		return isSameLine(version) && (!version.hasPatchPrecision() || contains(version));
	}

	private boolean isSameLine(@NotNull LatteVersion version) {
		return !version.isUndetermined() && version.isLine(major, minor);
	}

	private static int number(@NotNull String digits) {
		return Integer.parseInt(digits);
	}
}
