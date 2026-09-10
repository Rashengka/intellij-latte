package dev.noctud.latte.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the reference tables say exists in which Latte version.
 *
 * The tables under {@code docs/latte/} are read out of the engine's own registration code at each
 * tag, and the build ships them as they are. Nothing is transcribed into Java: a second list of
 * what exists in which version would drift from the first, and the drift would be invisible until
 * somebody was told that a filter which has always existed does not.
 *
 * The line names come from the table header rather than from constants here, so a Latte 3.2 column
 * added to the documentation is understood without touching this class.
 */
public final class LatteLanguageReference {

	private static final String TAGS = "/latte-reference/reference-tags.md";

	private static final String FILTERS = "/latte-reference/reference-filters.md";

	private static final String FUNCTIONS = "/latte-reference/reference-functions.md";

	/**
	 * A row: the first cell, then everything after it. Whether it is a row at all is decided by
	 * whether that first cell names anything in backticks - a row does, a header and a separator
	 * do not.
	 */
	private static final Pattern ROW = Pattern.compile("^\\|([^|]*)\\|(.*)$");

	/** An item named in the first cell. A cell may name more than one: {@code `{link}`, `{plink}`}. */
	private static final Pattern NAMED = Pattern.compile("`([^`]+)`");

	/** A minor line, e.g. {@code 3.1} - what a version column of a stamped table is headed with. */
	private static final Pattern LINE = Pattern.compile("\\d+\\.\\d+");

	/** What a version column may say and nothing else. */
	private static final Pattern VERSION_COLUMN = Pattern.compile("yes|no|\\d+\\.\\d+(\\.\\d+)?");

	private static volatile LatteLanguageReference instance;

	private final Map<String, LatteAvailability> tags;

	private final Map<String, LatteAvailability> filters;

	private final Map<String, LatteAvailability> functions;

	private final List<String> documentedLines;

	/**
	 * Which arguments {@code {syntax}} takes, and in which versions.
	 *
	 * <p>Kept apart from the three maps above because the table it comes from is a different shape.
	 * Theirs is headed by minor lines and says, per line, when an item arrived; this one is headed
	 * by stretches of versions - {@code 3.0.0-3.0.1}, {@code 3.0.24+} - because what it describes
	 * changed inside a line. The rows are arguments rather than tags and must not reach the tag
	 * map: nothing in a template is called {@code latte} or {@code single}.
	 */
	private final Map<String, List<LatteVersionRange>> syntaxModes;

	private LatteLanguageReference(
		@NotNull Map<String, LatteAvailability> tags,
		@NotNull Map<String, LatteAvailability> filters,
		@NotNull Map<String, LatteAvailability> functions,
		@NotNull List<String> documentedLines,
		@NotNull Map<String, List<LatteVersionRange>> syntaxModes
	) {
		this.tags = tags;
		this.filters = filters;
		this.functions = functions;
		this.documentedLines = documentedLines;
		this.syntaxModes = syntaxModes;
	}

	/**
	 * Whether {@code {syntax}} takes that argument in that version.
	 *
	 * <p>Yes wherever the answer cannot be placed, which is the rule everywhere in this class: a
	 * project whose version could not be established is told nothing, and neither is one known
	 * only to a line the table splits - a project on "3.0" is on either side of the patch where
	 * {@code latte} went, and the table cannot say which.
	 *
	 * <p>A name the table never lists is not a mode in any version, so it is refused: that answer
	 * comes from the table too, not from the absence of one.
	 */
	public boolean syntaxModeExists(@NotNull String mode, @NotNull LatteVersion version) {
		List<LatteVersionRange> takenIn = syntaxModes.get(mode);
		if (takenIn == null) {
			return syntaxModes.isEmpty();
		}
		if (version.isUndetermined()) {
			return true;
		}
		for (LatteVersionRange range : takenIn) {
			if (range.contains(version)) {
				return true;
			}
		}
		// Not in any range it is taken in. That is a plain no only when every range of that line
		// could be placed; where one of them merely touches the version, the line is split and the
		// version sits on an unknown side of the split.
		return isOnASplitLine(version);
	}

	/** The arguments {@code {syntax}} takes in that version, in the order the table lists them. */
	public @NotNull List<String> syntaxModesIn(@NotNull LatteVersion version) {
		List<String> taken = new ArrayList<>();
		for (Map.Entry<String, List<LatteVersionRange>> mode : syntaxModes.entrySet()) {
			if (syntaxModeExists(mode.getKey(), version)) {
				taken.add(mode.getKey());
			}
		}
		return taken;
	}

	/**
	 * Whether the table divides the version's line into stretches, and the version does not say
	 * which of them it is in.
	 *
	 * <p>Only a version without a patch can be in that position. One that names its patch sits in
	 * exactly one stretch and is answered plainly - asking this about it would turn every honest
	 * no on a divided line into silence, which is how a check stops checking.
	 */
	private boolean isOnASplitLine(@NotNull LatteVersion version) {
		if (version.hasPatchPrecision()) {
			return false;
		}
		for (List<LatteVersionRange> ranges : syntaxModes.values()) {
			for (LatteVersionRange range : ranges) {
				if (!range.isWholeLine() && range.touches(version)) {
					return true;
				}
			}
		}
		return false;
	}

	public static @NotNull LatteLanguageReference getInstance() {
		LatteLanguageReference read = instance;
		if (read == null) {
			synchronized (LatteLanguageReference.class) {
				read = instance;
				if (read == null) {
					read = load();
					instance = read;
				}
			}
		}
		return read;
	}

	/** Every line the tables describe, oldest first. */
	public @NotNull List<String> getDocumentedLines() {
		return documentedLines;
	}

	/**
	 * @return what the tables say about this tag; {@link LatteAvailability#ALWAYS} when they say
	 *         nothing. An item the reference does not mention is one the plugin has no grounds to
	 *         withhold - a tag defined by the project itself, say.
	 */
	public @NotNull LatteAvailability availabilityOfTag(@NotNull String name) {
		return tags.getOrDefault(name, LatteAvailability.ALWAYS);
	}

	public @NotNull LatteAvailability availabilityOfFilter(@NotNull String name) {
		LatteAvailability exact = filters.get(name);
		if (exact != null) {
			return exact;
		}
		// Latte 2.11 matches filter names case-insensitively and the registry looks them up the
		// same way, so the reference has to be reachable by the name the lookup used.
		for (Map.Entry<String, LatteAvailability> entry : filters.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name)) {
				return entry.getValue();
			}
		}
		return LatteAvailability.ALWAYS;
	}

	/**
	 * Functions are matched by their exact name. Latte 3 resolves them through a compiler pass
	 * keyed on the registered name, and Latte 2 through a plain array lookup, so neither line ever
	 * matched one spelled differently - which is why this is not the filter lookup with a
	 * case-insensitive fallback.
	 */
	public @NotNull LatteAvailability availabilityOfFunction(@NotNull String name) {
		return functions.getOrDefault(name, LatteAvailability.ALWAYS);
	}

	/**
	 * How to say that this version does not have the item - "was removed in Latte 3.0", "does not
	 * exist before Latte 3.0.5" - or null when it has it, when the version is not known, or when
	 * the tables place the item in no version at all.
	 *
	 * The difference is worth the arithmetic. "Unknown tag" sends the reader looking for a typo;
	 * "was removed in Latte 3.0" tells them what actually happened to their template, and neither
	 * fact is anywhere in the plugin except in these tables.
	 */
	public @Nullable String absenceOf(@NotNull LatteAvailability availability, @NotNull LatteVersion version) {
		String line = version.line();
		if (line == null || availability.covers(version, documentedLines)) {
			return null;
		}
		String arrivedInThisLine = availability.arrivalIn(line);
		if (arrivedInThisLine != null && !arrivedInThisLine.isEmpty()) {
			return "does not exist before Latte " + arrivedInThisLine;
		}
		List<String> held = availability.linesAmong(documentedLines);
		if (held.isEmpty()) {
			// In no version the tables describe. Calling that a version problem would be a worse
			// answer than "unknown", which is what the caller says when this returns null.
			return null;
		}
		String newestWithIt = held.get(held.size() - 1);
		if (LatteAvailability.compare(newestWithIt, line) < 0) {
			return "was removed in Latte " + lineAfter(newestWithIt);
		}
		String oldestWithIt = held.get(0);
		if (LatteAvailability.compare(oldestWithIt, line) > 0) {
			String arrival = availability.arrivalIn(oldestWithIt);
			return "does not exist before Latte "
				+ (arrival == null || arrival.isEmpty() ? oldestWithIt : arrival);
		}
		return "does not exist in Latte " + line;
	}

	/** The documented line after this one - where something present in it stopped being present. */
	private @NotNull String lineAfter(@NotNull String line) {
		int at = documentedLines.indexOf(line);
		return at >= 0 && at + 1 < documentedLines.size() ? documentedLines.get(at + 1) : line;
	}

	private static @NotNull LatteLanguageReference load() {
		List<String> lines = new ArrayList<>();
		Map<String, LatteAvailability> tags = read(TAGS, lines);
		Map<String, LatteAvailability> filters = read(FILTERS, lines);
		Map<String, LatteAvailability> functions = read(FUNCTIONS, lines);
		return new LatteLanguageReference(tags, filters, functions, List.copyOf(lines), readRanged(TAGS));
	}

	/**
	 * The one table in the files whose header is stretches of versions rather than minor lines.
	 *
	 * <p>It is read on its own pass rather than inside {@link #read}, because everything about it
	 * differs: its columns are ranges, its rows are arguments and not tags, and mixing it into that
	 * loop is what made it need excluding in the first place.
	 */
	private static @NotNull Map<String, List<LatteVersionRange>> readRanged(@NotNull String resource) {
		Map<String, List<LatteVersionRange>> found = new LinkedHashMap<>();
		try (InputStream stream = LatteLanguageReference.class.getResourceAsStream(resource)) {
			if (stream == null) {
				return found;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			String line;
			List<LatteVersionRange> header = List.of();
			while ((line = reader.readLine()) != null) {
				Matcher matcher = ROW.matcher(line);
				if (!matcher.find()) {
					continue;
				}
				if (!NAMED.matcher(matcher.group(1)).find()) {
					List<LatteVersionRange> named = rangedHeaderIn(line);
					if (named != null) {
						header = named;
					}
					continue;
				}
				if (header.isEmpty()) {
					continue;
				}
				String[] columns = matcher.group(2).split("\\|", -1);
				if (columns.length < header.size()) {
					continue;
				}
				// Every column of a row in this table is a plain yes or no. Anything else belongs
				// to one of the tables that follow it in the same file - which have their own
				// shape and their own meaning for a column that happens to say "yes", and which
				// this loop swallowed whole until a playground template caught it saying that
				// {syntax} accepts {cache} and n:href.
				List<LatteVersionRange> takenIn = new ArrayList<>();
				boolean everyColumnIsPlain = true;
				for (int i = 0; i < header.size(); i++) {
					String value = columns[i].trim();
					if ("yes".equals(value)) {
						takenIn.add(header.get(i));
					} else if (!"no".equals(value)) {
						everyColumnIsPlain = false;
						break;
					}
				}
				if (!everyColumnIsPlain) {
					continue;
				}
				Matcher name = NAMED.matcher(matcher.group(1));
				if (name.find()) {
					found.put(name.group(1).trim(), List.copyOf(takenIn));
				}
			}
		} catch (IOException e) {
			return found;
		}
		return found;
	}

	/**
	 * The ranges a header names, or null for a line that is not a header of the ranged kind.
	 *
	 * <p>A header qualifies only when every one of its version columns reads as a range and at
	 * least one of them is a piece of a line rather than a whole one. The second half is what tells
	 * this table from the ones headed by plain lines, which {@link #read} handles and which must
	 * not be read twice.
	 */
	private static List<LatteVersionRange> rangedHeaderIn(@NotNull String header) {
		List<LatteVersionRange> ranges = new ArrayList<>();
		boolean anyPartial = false;
		for (String column : header.split("\\|")) {
			String value = column.trim();
			if (value.isEmpty() || !LINE.matcher(value).find()) {
				continue;
			}
			LatteVersionRange range = LatteVersionRange.parse(value);
			if (range == null) {
				return null;
			}
			anyPartial |= !range.isWholeLine();
			ranges.add(range);
		}
		return anyPartial && ranges.size() >= 2 ? ranges : null;
	}

	/**
	 * @param documentedLines filled in from the header the first time one is seen, so that both
	 *                        tables agree on which lines exist rather than each carrying its own.
	 */
	private static @NotNull Map<String, LatteAvailability> read(
		@NotNull String resource, @NotNull List<String> documentedLines
	) {
		Map<String, LatteAvailability> found = new LinkedHashMap<>();
		try (InputStream stream = LatteLanguageReference.class.getResourceAsStream(resource)) {
			if (stream == null) {
				// The tables are a build input; without them the plugin still has to work, and
				// working means claiming nothing about versions rather than claiming everything.
				return found;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			String line;
			List<String> header = new ArrayList<>();
			while ((line = reader.readLine()) != null) {
				Matcher matcher = ROW.matcher(line);
				if (matcher.find() && NAMED.matcher(matcher.group(1)).find()) {
					LatteAvailability availability = availabilityIn(matcher.group(2), header);
					for (String name : namesIn(matcher.group(1))) {
						found.merge(name, availability, LatteAvailability::or);
					}
					continue;
				}
				// A header names the lines. It has to be told apart from a row that merely mentions
				// a version - the tag table's "Paired" column says "2.11 yes / 3.x no", and reading
				// that as a header emptied the line list and made every row below it look available
				// in everything. A header is a line with nothing in backticks whose version columns
				// are nothing but line numbers.
				List<String> named = headerLinesIn(line);
				if (named != null) {
					header = named;
					if (!named.isEmpty() && documentedLines.isEmpty()) {
						documentedLines.addAll(named);
					}
				}
			}
		} catch (IOException e) {
			return found;
		}
		return found;
	}

	/**
	 * The name a lookup will use, or null for a row that names no tag or filter.
	 *
	 * The tag table writes its items as they are written in a template - {@code &#123;foreach&#125;},
	 * {@code n:href} - while the registry knows them bare. The same file also holds a second table
	 * listing what {@code &#123;syntax&#125;} accepts; those are arguments rather than tags and are
	 * skipped, which is what the brace or the n: prefix is being asked about.
	 */
	private static @NotNull List<String> namesIn(@NotNull String cell) {
		List<String> names = new ArrayList<>();
		Matcher matcher = NAMED.matcher(cell);
		while (matcher.find()) {
			String name = normalise(matcher.group(1).trim());
			if (name != null) {
				names.add(name);
			}
		}
		return names;
	}

	private static String normalise(@NotNull String written) {
		if (written.startsWith("{")) {
			String bare = written.substring(1).replaceAll("}$", "");
			bare = bare.startsWith("/") ? bare.substring(1) : bare;
			return bare.isEmpty() ? null : bare;
		}
		if (written.startsWith("n:")) {
			return written.substring(2);
		}
		// A filter row, whose name is already bare. The syntax-argument table is excluded by the
		// version columns it does not have, not here.
		return written.contains(" ") ? null : written;
	}

	/**
	 * The lines a header names, an empty list for a header this cannot read, or null for a line
	 * that is not a header at all.
	 *
	 * The middle case is the {@code &#123;syntax&#125;} argument table, headed
	 * {@code | Argument | 2.11 | 3.0.0-3.0.1 | 3.0.2-3.0.23 | 3.0.24+ | 3.1 |}. Two of its columns
	 * are bare lines and the rest are ranges, so reading it as a three-line header would line the
	 * columns up wrongly and stamp its rows with somebody else's answers. An empty header means
	 * the rows below it are available everywhere, which is what the plugin owes a table it cannot
	 * read: it withholds only what a readable row proves absent.
	 */
	private static List<String> headerLinesIn(@NotNull String header) {
		List<String> lines = new ArrayList<>();
		boolean readable = true;
		for (String column : header.split("\\|")) {
			String value = column.trim();
			if (LINE.matcher(value).matches()) {
				lines.add(value);
			} else if (LINE.matcher(value).find()) {
				readable = false;
			}
		}
		if (lines.size() + (readable ? 0 : 1) < 2) {
			return null;
		}
		return readable ? lines : List.of();
	}

	/**
	 * A row whose first columns are not version columns belongs to the other table in the file -
	 * the one naming the package a filter comes from. Those carry no version and are available
	 * whatever the line is.
	 */
	private static @NotNull LatteAvailability availabilityIn(@NotNull String rest, @NotNull List<String> header) {
		String[] columns = rest.split("\\|", -1);
		if (header.isEmpty() || columns.length < header.size()) {
			return LatteAvailability.ALWAYS;
		}
		Map<String, String> present = new LinkedHashMap<>();
		for (int i = 0; i < header.size(); i++) {
			String value = columns[i].trim();
			if (!VERSION_COLUMN.matcher(value).matches()) {
				return LatteAvailability.ALWAYS;
			}
			if (!"no".equals(value)) {
				present.put(header.get(i), "yes".equals(value) ? LatteAvailability.WHOLE_LINE : value);
			}
		}
		return LatteAvailability.inLines(present);
	}
}
