package dev.noctud.latte.psi;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * An element opened in one branch of an {if} and closed in the matching branch of a later one.
 *
 * <p>{@code {if $a}<a href="#">{else}<em>{/if}x{if $a}</a>{else}</em>{/if}} renders either an
 * {@code a} or an {@code em}, and closes the one it opened. The HTML view of the template is the
 * text of every branch at once - {@code <a><em>x</a></em>} - so the HTML parser closes the
 * {@code em} early at {@code </a>} and finds nothing left for {@code </em>} to close. The report
 * is about a document the template never renders.
 *
 * <p>Only a closing tag that stands in a branch of a condition is quietened, and only when a start
 * tag of the same name stands in a branch of a condition before it - the shape in which the two
 * really may pair up at render time. A closing tag with no such start tag is still reported, in a
 * branch or not.
 */
public class ElementSplitAcrossBranchesTest extends BasePlatformTestCase {

    private static final String DANGLING = "Closing tag matches nothing";

    public void testAnElementOpenedAndClosedInMatchingBranchesIsQuiet() {
        assertQuiet("{varType bool $a}\n{if $a}<a href=\"#\">{else}<em>{/if}x{if $a}</a>{else}</em>{/if}\n");
    }

    public void testTheSameWithTheBranchesOnTheirOwnLinesIsQuiet() {
        assertQuiet("{varType bool $a}\n<p>\n{if $a}\n<a href=\"#\">\n{else}\n<em>\n{/if}\nx\n"
            + "{if $a}\n</a>\n{else}\n</em>\n{/if}\n</p>\n");
    }

    /** The counterweight: no start tag of that name anywhere, so nothing it could close. */
    public void testAClosingTagInABranchWithNothingToCloseIsStillReported() {
        assertReported("{varType bool $a}\n{if $a}</em>{/if}\n");
    }

    /** A start tag of that name outside any condition does not make a later branch balanced. */
    public void testAClosingTagInABranchAfterAnUnconditionalStartIsStillReported() {
        assertReported("{varType bool $a}\n<em>x</em>\n{if $a}</em>{/if}\n");
    }

    /** And a stray closing tag outside any condition is reported as it always was. */
    public void testAStrayClosingTagOutsideAConditionIsStillReported() {
        assertReported("{varType bool $a}\n{if $a}<em>{/if}\n</em>\n</em>\n");
    }

    private void assertQuiet(String template) {
        assertFalse(template + " -> " + reportsOn(template), reportsOn(template).contains(DANGLING));
    }

    private void assertReported(String template) {
        assertTrue(template + " -> " + reportsOn(template), reportsOn(template).contains(DANGLING));
    }

    private List<String> reportsOn(String template) {
        myFixture.configureByText("page.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getDescription());
            }
        }
        return problems;
    }
}
