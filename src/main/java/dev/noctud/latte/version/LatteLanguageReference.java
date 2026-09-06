package dev.noctud.latte.version;

import org.jetbrains.annotations.NotNull;

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

	/** A row: the item in backticks, then its columns. Anything else in the file is prose. */
	private static final Pattern ROW = Pattern.compile("^\\|\\s*`([^`]+)`\\s*\\|(.*)$");

	/** A header row naming the lines, e.g. {@code | Filter | 2.11 | 3.0 | 3.1 | Notes |}. */
	private static final Pattern LINE = Pattern.compile("\\d+\\.\\d+");

	/** What a version column may say and nothing else. */
	private static final Pattern VERSION_COLUMN = Pattern.compile("yes|no|\\d+\\.\\d+(\\.\\d+)?");

	private static volatile LatteLanguageReference instance;

	private final Map<String, LatteAvailability> tags;

	private final Map<String, LatteAvailability> filters;

	private final List<String> documentedLines;

	private LatteLanguageReference(
		@NotNull Map<String, LatteAvailability> tags,
		@NotNull Map<String, LatteAvailability> filters,
		@NotNull List<String> documentedLines
	) {
		this.tags = tags;
		this.filters = filters;
		this.documentedLines = documentedLines;
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

	private static @NotNull LatteLanguageReference load() {
		List<String> lines = new ArrayList<>();
		Map<String, LatteAvailability> tags = read(TAGS, lines);
		Map<String, LatteAvailability> filters = read(FILTERS, lines);
		return new LatteLanguageReference(tags, filters, List.copyOf(lines));
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
				if (matcher.find()) {
					String name = normalise(matcher.group(1));
					if (name != null) {
						found.putIfAbsent(name, availabilityIn(matcher.group(2), header));
					}
					continue;
				}
				// A header names the lines. It has to be told apart from a row that merely mentions
				// a version - the tag table's "Paired" column says "2.11 yes / 3.x no", and reading
				// that as a header emptied the line list and made every row below it look available
				// in everything. A header is a row with no item in backticks and at least two
				// columns that are nothing but a line number.
				List<String> named = headerLinesIn(line);
				if (named.size() >= 2) {
					header = named;
					if (documentedLines.isEmpty()) {
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

	private static @NotNull List<String> headerLinesIn(@NotNull String header) {
		List<String> lines = new ArrayList<>();
		for (String column : header.split("\\|")) {
			String value = column.trim();
			if (LINE.matcher(value).matches()) {
				lines.add(value);
			}
		}
		return lines;
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
