package dev.noctud.latte.config;

import dev.noctud.latte.settings.LatteFilterSettings;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Every filter Latte itself defines has to resolve, otherwise the modifier inspection reports a
 * correct template as using an undefined filter. The names below come from
 * docs/latte/reference-filters.md, which is derived from the Latte sources.
 */
public class LatteFilterRegistryTest {

	/** Filters registered by Latte in every version from 2.11.7 to 3.1.6. */
	private static final List<String> ALWAYS_PRESENT = Arrays.asList(
		"batch", "breakLines", "bytes", "capitalize", "ceil", "checkUrl", "clamp", "dataStream",
		"date", "escapeCss", "escapeHtml", "escapeHtmlComment", "escapeICal", "escapeJs",
		"escapeUrl", "escapeXml", "explode", "first", "firstUpper", "floor", "implode", "indent",
		"join", "last", "length", "lower", "number", "padLeft", "padRight", "query", "random",
		"repeat", "replace", "replaceRe", "reverse", "round", "slice", "sort", "spaceless",
		"split", "strip", "stripHtml", "stripTags", "substr", "translate", "trim", "truncate",
		"upper", "webalize"
	);

	/** Lowercase aliases Latte 2.11.1 added for forward compatibility with Latte 3. */
	private static final List<String> LOWERCASE_ALIASES = Arrays.asList(
		"breaklines", "datastream", "replaceRE", "striphtml", "striptags"
	);

	/** Added inside the 3.0 and 3.1 lines. Accepted everywhere, since the version is not known. */
	private static final List<String> ADDED_IN_LATTE_3 = Arrays.asList(
		"column", "commas", "escape", "filter", "firstLower", "group", "limit", "localDate", "map"
	);

	/**
	 * Not in any filter registry - Latte recognises them by name while compiling and removes them
	 * from the chain. An inspection driven by the registry alone reports each one as undefined.
	 */
	private static final List<String> COMPILER_DIRECTIVES = Arrays.asList(
		"noescape", "nocheck", "noCheck", "noiterator", "toggle", "accept", "json"
	);

	@Test
	public void testFiltersPresentInEveryVersionResolve() {
		assertAllResolve(ALWAYS_PRESENT);
	}

	@Test
	public void testLowercaseAliasesResolve() {
		assertAllResolve(LOWERCASE_ALIASES);
	}

	@Test
	public void testFiltersAddedInLatte3Resolve() {
		assertAllResolve(ADDED_IN_LATTE_3);
	}

	@Test
	public void testCompilerDirectivesResolve() {
		assertAllResolve(COMPILER_DIRECTIVES);
	}

	/**
	 * Latte 2.11 matches filter names case-insensitively, so a template written for it may spell
	 * a filter any way at all. Latte 3 matches exactly, but until the plugin knows the version,
	 * accepting both is the direction that cannot produce a false error.
	 */
	@Test
	public void testLookupIgnoresCase() {
		Map<String, LatteFilterSettings> filters = latteFilters();
		assertNotNull(LatteConfiguration.findIgnoringCase(filters, "UPPER"));
		assertNotNull(LatteConfiguration.findIgnoringCase(filters, "escapeurl"));
		assertNotNull(LatteConfiguration.findIgnoringCase(filters, "ESCAPEURL"));
		assertNotNull(LatteConfiguration.findIgnoringCase(filters, "TrUnCaTe"));
	}

	@Test
	public void testLookupStillMissesUnknownNames() {
		Map<String, LatteFilterSettings> filters = latteFilters();
		assertNull(LatteConfiguration.findIgnoringCase(filters, "notAFilter"));
		assertNull(LatteConfiguration.findIgnoringCase(filters, ""));
	}

	private void assertAllResolve(List<String> names) {
		Map<String, LatteFilterSettings> filters = latteFilters();
		StringBuilder missing = new StringBuilder();
		for (String name : names) {
			if (LatteConfiguration.findIgnoringCase(filters, name) == null) {
				missing.append(missing.length() == 0 ? "" : ", ").append(name);
			}
		}
		assertTrue("Filters Latte defines but the plugin does not resolve: " + missing, missing.length() == 0);
	}

	private Map<String, LatteFilterSettings> latteFilters() {
		return LatteDefaultConfiguration.getInstance().getFilters(LatteConfiguration.Vendor.LATTE);
	}
}
