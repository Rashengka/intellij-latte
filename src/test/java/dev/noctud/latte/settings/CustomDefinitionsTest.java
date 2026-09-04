package dev.noctud.latte.settings;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.inspections.MethodUsagesInspection;
import dev.noctud.latte.inspections.ModifierDefinitionInspection;
import dev.noctud.latte.inspections.ModifierNotAllowedInspection;
import dev.noctud.latte.inspections.VariablesInspection;
import dev.noctud.latte.ui.LatteCustomMacroSettingsForm;
import dev.noctud.latte.ui.LatteCustomModifierSettingsForm;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects define their own filters, tags, functions and variables, and most of the filter names in
 * a real code base are project ones rather than Latte's. Whatever the settings hold has to reach the
 * parser, the annotator, the inspections and code completion, because a definition the plugin does
 * not see is reported as an error on correct code.
 *
 * <p>The whole path is exercised as the IDE runs it: the plugin's own settings service, a real Latte
 * file, real highlighting. The counterpart is {@link LatteSettingsPersistenceTest}, which checks
 * that the same definitions are still there after a restart.
 */
public class CustomDefinitionsTest extends BasePlatformTestCase {

    private LatteSettings settings;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The light project - and with it the settings service - is shared between the tests in this
        // class, so each starts from lists of its own instead of what the previous one left behind.
        settings = LatteSettings.getInstance(getProject());
        settings.tagSettings = new ArrayList<>();
        settings.filterSettings = new ArrayList<>();
        settings.functionSettings = new ArrayList<>();
        settings.variableSettings = new ArrayList<>();
        settings.enableCustomMacros = true;
        settings.enableCustomModifiers = true;
        settings.enableCustomFunctions = true;
        settings.enableDefaultVariables = true;

        myFixture.enableInspections(
            new ModifierDefinitionInspection(),
            new ModifierNotAllowedInspection(),
            new VariablesInspection(),
            new MethodUsagesInspection()
        );
    }

    /**
     * Nothing below means anything unless the same names are reported when they are not defined.
     * Without this the whole class would still pass with every lookup returning "known".
     */
    public void testNamesThatAreNotDefinedAnywhereAreReported() {
        assertProblems(
            "{var $a = 1}\n{$a|myFilter}\n{myTag}\n<div n:myAttr=\"1\">x</div>\n{myFunction(1)}\n{$myVar}\n",
            "WARNING:Undefined latte filter 'myFilter'",
            "ERROR:Unknown tag {myTag}",
            "ERROR:Unknown attribute tag n:myAttr",
            "WARNING:Function 'myFunction' not found",
            "WARNING:Undefined variable 'myVar'"
        );
    }

    public void testCustomFilterWithArgumentsIsAccepted() {
        // One colon: the filter takes one argument.
        settings.filterSettings.add(new LatteFilterSettings("myFilter", "", "arg", ":"));

        assertNoProblems("{var $a = 1}\n{$a|myFilter:5}\n");
    }

    public void testCustomFilterMissingItsArgumentIsReported() {
        settings.filterSettings.add(new LatteFilterSettings("myFilter", "", "arg", ":"));

        assertProblems("{var $a = 1}\n{$a|myFilter}\n", "WARNING:Missing required filter parameters (1 required)");
    }

    public void testCustomUnpairedTagIsAccepted() {
        settings.tagSettings.add(new LatteTagSettings("myTag", LatteTagSettings.Type.UNPAIRED));

        assertNoProblems("{myTag}\n");
    }

    public void testCustomPairTagIsAccepted() {
        settings.tagSettings.add(new LatteTagSettings("myBlock", LatteTagSettings.Type.PAIR));

        assertNoProblems("{myBlock}\ntext\n{/myBlock}\n");
    }

    /**
     * A pair tag is also an n:attribute, including the inner- and tag- prefixes, and a closing tag
     * inside the element must not be expected on top of it.
     */
    public void testCustomPairTagIsAcceptedAsANetteAttribute() {
        settings.tagSettings.add(new LatteTagSettings("myBlock", LatteTagSettings.Type.PAIR));

        assertNoProblems("<div n:myBlock>text</div>\n");
        assertNoProblems("<div n:inner-myBlock>text</div>\n");
        assertNoProblems("<div n:tag-myBlock>text</div>\n");
    }

    public void testCustomAttributeOnlyTagIsAcceptedAsANetteAttribute() {
        settings.tagSettings.add(new LatteTagSettings("myAttr", LatteTagSettings.Type.ATTR_ONLY));

        assertNoProblems("<div n:myAttr=\"1\">text</div>\n");
    }

    public void testCustomFunctionIsAccepted() {
        settings.functionSettings.add(new LatteFunctionSettings("myFunction", "string", "($count)"));

        assertNoProblems("{myFunction(1)}\n");
    }

    public void testCustomVariableIsAccepted() {
        settings.variableSettings.add(new LatteVariableSettings("myVar", "string"));

        assertNoProblems("{$myVar}\n");
    }

    /**
     * A tag that may not take filters is the reason the tag definition carries the flag at all, so
     * the flag has to arrive at the inspection and not only the name.
     */
    public void testFiltersOnACustomTagThatForbidsThemAreReported() {
        settings.tagSettings.add(new LatteTagSettings("myTag", LatteTagSettings.Type.UNPAIRED));
        settings.filterSettings.add(new LatteFilterSettings("myFilter"));

        assertProblems("{myTag 1|myFilter}\n", "ERROR:Filters are not allowed here");
    }

    public void testFiltersOnACustomTagThatAllowsThemAreAccepted() {
        settings.tagSettings.add(new LatteTagSettings(
            "myTag", LatteTagSettings.Type.UNPAIRED, true, false, dev.noctud.latte.config.LatteConfiguration.Vendor.CUSTOM, ""
        ));
        settings.filterSettings.add(new LatteFilterSettings("myFilter"));

        assertNoProblems("{myTag 1|myFilter}\n");
    }

    /** Each kind has a switch of its own, and one switch must not silence another kind. */
    public void testTurningOneKindOffLeavesTheOthersAlone() {
        settings.tagSettings.add(new LatteTagSettings("myTag", LatteTagSettings.Type.UNPAIRED));
        settings.filterSettings.add(new LatteFilterSettings("myFilter"));
        settings.enableCustomModifiers = false;

        assertProblems("{myTag}\n{var $a = 1}\n{$a|myFilter}\n", "WARNING:Undefined latte filter 'myFilter'");
    }

    public void testTurningCustomTagsOffMakesThemUnknownAgain() {
        settings.tagSettings.add(new LatteTagSettings("myTag", LatteTagSettings.Type.UNPAIRED));
        settings.enableCustomMacros = false;

        assertProblems("{myTag}\n", "ERROR:Unknown tag {myTag}");
    }

    public void testTurningCustomFunctionsOffMakesThemUnknownAgain() {
        settings.functionSettings.add(new LatteFunctionSettings("myFunction"));
        settings.enableCustomFunctions = false;

        assertProblems("{myFunction(1)}\n", "WARNING:Function 'myFunction' not found");
    }

    public void testTurningCustomVariablesOffMakesThemUnknownAgain() {
        settings.variableSettings.add(new LatteVariableSettings("myVar", "string"));
        settings.enableDefaultVariables = false;

        assertProblems("{$myVar}\n", "WARNING:Undefined variable 'myVar'");
    }

    // Completion is the other half of "the plugin knows about it": a definition that resolves but is
    // never offered still leaves the name to be typed from memory.

    public void testCustomTagsAreOfferedInCompletion() {
        settings.tagSettings.add(new LatteTagSettings("myTag", LatteTagSettings.Type.UNPAIRED));
        settings.tagSettings.add(new LatteTagSettings("myBlock", LatteTagSettings.Type.PAIR));

        assertTrue(completionAt("{my<caret>}\n").containsAll(List.of("myTag", "myBlock")));
    }

    public void testCustomFiltersAreOfferedInCompletion() {
        settings.filterSettings.add(new LatteFilterSettings("myFilter"));
        settings.filterSettings.add(new LatteFilterSettings("myOtherFilter"));

        assertTrue(completionAt("{var $a = 1}\n{$a|my<caret>}\n").containsAll(List.of("myFilter", "myOtherFilter")));
    }

    public void testCustomNetteAttributesAreOfferedInCompletion() {
        settings.tagSettings.add(new LatteTagSettings("myAttr", LatteTagSettings.Type.ATTR_ONLY));
        settings.tagSettings.add(new LatteTagSettings("myBlock", LatteTagSettings.Type.PAIR));

        assertTrue(completionAt("<div n:my<caret>>x</div>\n")
            .containsAll(List.of("n:myAttr", "n:myBlock", "n:inner-myBlock", "n:tag-myBlock")));
    }

    // Pressing Apply has to reach the templates that are already open. Reporting the old errors
    // until the file is edited or reopened reads as the setting not having worked at all.

    public void testApplyingANewTagReachesAnAlreadyOpenTemplate() throws ConfigurationException {
        myFixture.configureByText("open.latte", "{myTag}\n");
        assertEquals(List.of("ERROR:Unknown tag {myTag}"), problemsInTheOpenFile());

        settings.tagSettings.add(new LatteTagSettings("myTag", LatteTagSettings.Type.UNPAIRED));
        new LatteCustomMacroSettingsForm(getProject()).apply();

        assertEquals(List.of(), problemsInTheOpenFile());
    }

    public void testApplyingANewFilterReachesAnAlreadyOpenTemplate() throws ConfigurationException {
        myFixture.configureByText("open.latte", "{var $a = 1}\n{$a|myFilter}\n");
        assertEquals(List.of("WARNING:Undefined latte filter 'myFilter'"), problemsInTheOpenFile());

        settings.filterSettings.add(new LatteFilterSettings("myFilter"));
        new LatteCustomModifierSettingsForm(getProject()).apply();

        assertEquals(List.of(), problemsInTheOpenFile());
    }

    /**
     * Whether a tag is a pair one is decided while the file is parsed, so a repaint is not enough
     * here: the cached tree still says the tag is unknown and its closing tag is a stray one.
     */
    public void testApplyingANewPairTagReachesAnAlreadyOpenTemplate() throws ConfigurationException {
        myFixture.configureByText("open.latte", "{myBlock}text{/myBlock}\n");
        assertFalse(problemsInTheOpenFile().isEmpty());

        settings.tagSettings.add(new LatteTagSettings("myBlock", LatteTagSettings.Type.PAIR));
        new LatteCustomMacroSettingsForm(getProject()).apply();

        assertEquals(List.of(), problemsInTheOpenFile());
    }

    private void assertNoProblems(String template) {
        assertEquals(List.of(), problemsIn(template));
    }

    private void assertProblems(String template, String... expected) {
        assertEquals(List.of(expected), problemsIn(template));
    }

    private List<String> problemsIn(String template) {
        myFixture.configureByText("template.latte", template);
        return problemsInTheOpenFile();
    }

    private List<String> problemsInTheOpenFile() {
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            problems.add(info.getSeverity().getName() + ":" + info.getDescription());
        }
        return problems;
    }

    private List<String> completionAt(String template) {
        myFixture.configureByText("completion.latte", template);
        myFixture.completeBasic();
        List<String> offered = myFixture.getLookupElementStrings();
        return offered == null ? List.of() : offered;
    }
}
