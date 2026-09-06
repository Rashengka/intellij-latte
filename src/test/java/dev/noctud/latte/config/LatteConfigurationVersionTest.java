package dev.noctud.latte.config;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.settings.LatteSettings;
import dev.noctud.latte.settings.LatteTagSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the registry answers once it knows which Latte the templates are written for.
 *
 * Both directions are asserted for every item, and the second one is the one worth having. A tag
 * withheld from the version that does have it is not a missing feature, it is an "Unknown tag" on
 * a template that compiles - and the plugin exists to remove those, not to add them.
 *
 * The version is forced through the setting rather than through a composer.lock, because what is
 * under test is the registry and not detection; detection has its own tests.
 */
public class LatteConfigurationVersionTest extends BasePlatformTestCase {

    private PsiFile template;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        template = myFixture.configureByText("template.latte", "{foreach $items as $item}{$item}{/foreach}");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            LatteSettings.getInstance(getProject()).latteVersionOverride = "";
            LatteSettings.getInstance(getProject()).tagSettings.clear();
        } finally {
            super.tearDown();
        }
    }

    /** {includeblock} is Latte 2 only, and is the plainest case of a tag that was removed. */
    public void testATagRemovedInLatte3IsGoneOnlyUnderLatte3() {
        assertNotNull(tagUnder("2.11", "includeblock"));
        assertNull(tagUnder("3.1", "includeblock"));
    }

    /** {exitIf} arrived in 3.0.5, so under 2.11 there is nothing to offer. */
    public void testATagAddedInLatte3IsAbsentUnderLatte2() {
        assertNull(tagUnder("2.11", "exitIf"));
        assertNotNull(tagUnder("3.1", "exitIf"));
    }

    /**
     * The state the plugin spends most of its time in. A project whose Latte could not be
     * established gets the whole registry: withholding on a guess would report correct templates,
     * and the plugin says nothing it cannot prove.
     */
    public void testAnUndeterminedVersionWithholdsNothing() {
        LatteSettings.getInstance(getProject()).latteVersionOverride = "";

        assertNotNull(LatteConfiguration.getInstance(getProject()).getTag("includeblock", template));
        assertNotNull(LatteConfiguration.getInstance(getProject()).getTag("exitIf", template));
    }

    /** Without a template in hand there is no version to apply, and the registry stays whole. */
    public void testAskingWithoutATemplateWithholdsNothing() {
        LatteSettings.getInstance(getProject()).latteVersionOverride = "3.1";

        assertNotNull(LatteConfiguration.getInstance(getProject()).getTag("includeblock"));
        assertNotNull(LatteConfiguration.getInstance(getProject()).getTag("includeblock", null));
    }

    /**
     * n:attr survived into Latte 3 even though {attr} the tag did not, and the registry holds one
     * entry for both. Withholding it would take n:attr away from every Latte 3 template.
     */
    public void testAnAttributeThatOutlivedItsTagStays() {
        assertNotNull(tagUnder("3.1", "attr"));
        assertNotNull(tagUnder("3.1", "class"));
        assertNotNull(tagUnder("3.1", "ifcontent"));
    }

    /** {snippet} is registered by nette/application under Latte 3, not by the engine. */
    public void testATagThatMovedToAnotherPackageStays() {
        assertNotNull(tagUnder("3.1", "snippet"));
        assertNotNull(tagUnder("3.1", "snippetArea"));
    }

    /** column() arrived in 3.1.3, group() in 3.0.16. */
    public void testFiltersAndFunctionsFollowTheSameVersions() {
        assertNull(LatteConfiguration.getInstance(getProject()).getFilter("column", under("2.11")));
        assertNotNull(LatteConfiguration.getInstance(getProject()).getFilter("column", under("3.1")));

        assertNull(LatteConfiguration.getInstance(getProject()).getFunction("group", under("2.11")));
        assertNotNull(LatteConfiguration.getInstance(getProject()).getFunction("group", under("3.1")));
    }

    /**
     * A tag the project defined itself has no version at all, whatever the reference tables happen
     * to say about a name it shares. The tables describe the engine, and a custom tag is not the
     * engine's to remove.
     */
    public void testATagTheProjectDefinedItselfIsNeverWithheld() {
        LatteSettings settings = LatteSettings.getInstance(getProject());
        settings.tagSettings.add(new LatteTagSettings("includeblock", LatteTagSettings.Type.UNPAIRED));

        assertNotNull(tagUnder("3.1", "includeblock"));
    }

    private @Nullable LatteTagSettings tagUnder(@NotNull String line, @NotNull String name) {
        return LatteConfiguration.getInstance(getProject()).getTag(name, under(line));
    }

    private @NotNull PsiFile under(@NotNull String line) {
        LatteSettings.getInstance(getProject()).latteVersionOverride = line;
        return template;
    }
}
