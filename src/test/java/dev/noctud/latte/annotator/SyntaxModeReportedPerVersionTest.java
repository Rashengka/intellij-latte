package dev.noctud.latte.annotator;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.settings.LatteSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * What {@code {syntax}} is told about its argument, in the Latte the project is written for.
 *
 * <p>The accepted modes changed inside a line: {@code latte} is taken in 2.11 and in 3.0.0 and
 * 3.0.1 and refused from 3.0.2, and {@code single} arrives in 3.0.24. The plugin used to carry the
 * union of all of them as a constant, because nothing could read the table that says so - its
 * header names stretches of versions rather than lines.
 *
 * <p>The union reported nothing, which was the right answer while it was the only one available: a
 * report about a template that is correct costs more than a missing report. It is the wrong answer
 * once the table can be read, and a second copy of what the table says besides.
 *
 * <p>Silence where the version cannot place the mode is kept, and both of its shapes are here: a
 * project whose Latte could not be established, and one known only to a line the table splits.
 */
public class SyntaxModeReportedPerVersionTest extends BasePlatformTestCase {

    public void testTheModeThatWentAwayIsReportedOnlyAfterItDid() {
        assertQuiet("2.11", "{syntax latte}");
        assertReported("3.1", "{syntax latte}");
    }

    public void testTheModeThatArrivedIsReportedOnlyBeforeItDid() {
        assertReported("2.11", "{syntax single}");
        assertQuiet("3.1", "{syntax single}");
    }

    public void testTheModesEveryVersionTakesAreQuietEverywhere() {
        for (String line : new String[]{"2.11", "3.0", "3.1"}) {
            assertQuiet(line, "{syntax off}");
            assertQuiet(line, "{syntax double}");
        }
    }

    /** A name no version takes is reported wherever the version could be placed. */
    public void testAModeNoVersionTakesIsReported() {
        assertReported("2.11", "{syntax triple}");
        assertReported("3.1", "{syntax triple}");
    }

    /** A project the plugin could not place is told nothing at all. */
    public void testAProjectWithNoVersionIsToldNothing() {
        assertQuiet("", "{syntax latte}");
        assertQuiet("", "{syntax single}");
    }

    /**
     * And neither is one on a line the table splits: 3.0 holds both sides of the patch where
     * {@code latte} went and {@code single} arrived, and "3.0" does not say which side.
     */
    public void testALineTheTableSplitsIsToldNothingEither() {
        assertQuiet("3.0", "{syntax latte}");
        assertQuiet("3.0", "{syntax single}");
    }

    /** The attribute form goes through the same check as the tag. */
    public void testTheAttributeFormIsCheckedTheSameWay() {
        assertReported("3.1", "<div n:syntax=\"latte\">x</div>");
        assertQuiet("2.11", "<div n:syntax=\"latte\">x</div>");
    }

    private void assertQuiet(String line, String template) {
        assertEquals(line + " " + template, List.of(), syntaxReportsOn(line, template));
    }

    private void assertReported(String line, String template) {
        assertFalse(line + " " + template + " should be reported", syntaxReportsOn(line, template).isEmpty());
    }

    private List<String> syntaxReportsOn(String line, String template) {
        LatteSettings.getInstance(getProject()).latteVersionOverride = line;
        myFixture.configureByText("page.latte", template + "\n");
        List<String> reports = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null && info.getDescription().contains("syntax mode")) {
                reports.add(info.getDescription());
            }
        }
        return reports;
    }
}
