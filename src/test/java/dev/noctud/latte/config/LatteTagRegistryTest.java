package dev.noctud.latte.config;

import dev.noctud.latte.settings.LatteTagSettings;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertTrue;

/**
 * Guards the tag registry in both directions.
 *
 * A tag Latte defines but the plugin does not know is reported as an unknown tag on a correct
 * template. A tag the plugin knows but Latte does not never fires at all, so a typo goes
 * unreported - quieter, but still a claim about Latte that is not true.
 *
 * The list below is the core-tag table from docs/latte/reference-tags.md, which is derived from
 * the registration code of every release between 2.11.7 and 3.1.6. Tags provided by other
 * packages - nette/application, nette/forms, nette/caching, nette/assets - are registered under
 * their own vendor and are deliberately not part of this comparison.
 */
public class LatteTagRegistryTest {

	private static final List<String> LATTE_CORE_TAGS = Arrays.asList(
		"=", "_", "attr", "block", "breakIf", "capture", "case", "class", "contentType",
		"continueIf", "debugbreak", "default", "define", "do", "dump", "else", "elseif",
		"elseifset", "embed", "exitIf", "extends", "first", "for", "foreach", "if", "ifchanged",
		"ifcontent", "ifset", "import", "include", "includeblock", "iterateWhile", "l", "last",
		"layout", "parameters", "php", "r", "rollback", "sandbox", "sep", "skipIf", "snippet",
		"snippetArea", "spaceless", "switch", "syntax", "tag", "templatePrint", "templateType",
		"trace", "translate", "try", "var", "varPrint", "varType", "while"
	);

	@Test
	public void testEveryLatteCoreTagIsRegistered() {
		Set<String> registered = latteTags().keySet();
		Set<String> missing = new TreeSet<>(LATTE_CORE_TAGS);
		missing.removeAll(registered);
		assertTrue("Tags Latte defines but the plugin does not know: " + missing, missing.isEmpty());
	}

	/**
	 * {catch} is the case this test was written for. It was registered as a Latte tag but appears
	 * in no release between 2.11.7 and 3.1.6 - verified against the sources at both ends of the
	 * range, where the name occurs only as a PHP keyword and never in a tag registration. The
	 * construct is {try}/{else}/{rollback}.
	 */
	@Test
	public void testNoTagIsRegisteredThatLatteDoesNotDefine() {
		Set<String> unknown = new TreeSet<>(latteTags().keySet());
		unknown.removeAll(new HashSet<>(LATTE_CORE_TAGS));
		assertTrue("Tags the plugin claims Latte defines but it does not: " + unknown, unknown.isEmpty());
	}

	/**
	 * {@code {PHP_EOL}} prints a constant, so LatteAnnotator reads a tag name written in upper case
	 * as one instead of reporting an unknown tag. That is only safe while no tag is spelled that
	 * way: one that were would be unreportable when misspelled, and quietly so.
	 */
	@Test
	public void testNoTagIsSpelledTheWayAConstantIs() {
		LatteDefaultConfiguration defaults = LatteDefaultConfiguration.getInstance();
		Set<String> constantLike = new TreeSet<>();
		for (LatteConfiguration.Vendor vendor : defaults.getVendors()) {
			for (String name : defaults.getTags(vendor).keySet()) {
				if (name.equals(name.toUpperCase()) && !name.equals(name.toLowerCase())) {
					constantLike.add(name);
				}
			}
		}
		assertTrue("Tags spelled the way a constant is, which makes them unreportable: " + constantLike, constantLike.isEmpty());
	}

	private Map<String, LatteTagSettings> latteTags() {
		return LatteDefaultConfiguration.getInstance().getTags(LatteConfiguration.Vendor.LATTE);
	}
}
