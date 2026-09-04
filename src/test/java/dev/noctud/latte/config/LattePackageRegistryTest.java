package dev.noctud.latte.config;

import dev.noctud.latte.settings.LatteFunctionSettings;
import dev.noctud.latte.settings.LatteTagSettings;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertTrue;

/**
 * The same guard {@link LatteTagRegistryTest} puts on the core tags, for the tags and functions
 * the Nette packages register - nette/application, nette/forms, nette/caching and nette/assets.
 * Five of them were missing and every one was reported as an error on a template that is correct
 * for the package version that introduced it, which is the worst shape a false report can take.
 *
 * <p>The lists below are the "from other packages" tables of docs/latte/reference-tags.md and
 * reference-functions.md, read out of the bridge classes. Nothing here is gated on the package
 * version: a construct that exists somewhere in the range is accepted everywhere in it, the same
 * decision the {syntax} argument list and the filters carry. What that costs is a template using a
 * newer construct on an older package going unreported; what the other choice costs is an error on
 * a template that is right.
 *
 * <p>Which vendor a tag is filed under is deliberately not asserted. It decides the colour in the
 * settings and nothing else, and the plugin files nette/assets under nette/application today.
 */
public class LattePackageRegistryTest {

    /**
     * {@code n:href}, {@code n:nonce} and {@code n:name} are registered under the name without the
     * prefix, which is what the annotator looks up. {@code {extends}}, {@code {layout}},
     * {@code {templatePrint}}, {@code {snippet}} and {@code {snippetArea}} are in the core table
     * as well and are covered by {@link LatteTagRegistryTest}; a bridge only overrides them.
     */
    private static final List<String> PACKAGE_TAGS = Arrays.asList(
        // nette/application
        "control", "link", "plink", "linkBase", "ifCurrent", "href", "nonce",
        // nette/caching
        "cache",
        // nette/forms
        "form", "formContext", "formContainer", "formPrint", "formClassPrint",
        "input", "inputError", "label", "name",
        // nette/assets
        "asset", "asset?", "preload"
    );

    private static final List<String> PACKAGE_FUNCTIONS = Arrays.asList(
        "isLinkCurrent", "isModuleCurrent", "asset", "tryAsset"
    );

    @Test
    public void testEveryTagAPackageRegistersIsKnown() {
        Set<String> missing = new TreeSet<>(PACKAGE_TAGS);
        missing.removeAll(registeredTags());
        assertTrue("Tags a Nette package registers but the plugin does not know: " + missing, missing.isEmpty());
    }

    @Test
    public void testNoTagIsRegisteredThatNoPackageProvides() {
        Set<String> unknown = new TreeSet<>(registeredTags());
        unknown.removeAll(new HashSet<>(PACKAGE_TAGS));
        assertTrue("Tags the plugin attributes to a Nette package that does not register them: " + unknown, unknown.isEmpty());
    }

    @Test
    public void testEveryFunctionAPackageRegistersIsKnown() {
        Set<String> missing = new TreeSet<>(PACKAGE_FUNCTIONS);
        missing.removeAll(registeredFunctions());
        assertTrue("Functions a Nette package registers but the plugin does not know: " + missing, missing.isEmpty());
    }

    @Test
    public void testNoFunctionIsRegisteredThatNoPackageProvides() {
        Set<String> unknown = new TreeSet<>(registeredFunctions());
        unknown.removeAll(new HashSet<>(PACKAGE_FUNCTIONS));
        assertTrue("Functions the plugin attributes to a Nette package that does not register them: " + unknown, unknown.isEmpty());
    }

    private Set<String> registeredTags() {
        return names(LatteConfiguration.Vendor.NETTE_APPLICATION, LatteConfiguration.Vendor.NETTE_FORMS, true);
    }

    private Set<String> registeredFunctions() {
        return names(LatteConfiguration.Vendor.NETTE_APPLICATION, LatteConfiguration.Vendor.NETTE_FORMS, false);
    }

    private Set<String> names(LatteConfiguration.Vendor first, LatteConfiguration.Vendor second, boolean tags) {
        LatteDefaultConfiguration defaults = LatteDefaultConfiguration.getInstance();
        Set<String> names = new TreeSet<>();
        for (LatteConfiguration.Vendor vendor : List.of(first, second)) {
            if (tags) {
                for (LatteTagSettings tag : defaults.getTags(vendor).values()) {
                    names.add(tag.getMacroName());
                }
            } else {
                for (LatteFunctionSettings function : defaults.getFunctions(vendor).values()) {
                    names.add(function.getFunctionName());
                }
            }
        }
        return names;
    }
}
