package dev.noctud.latte.psi;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * A closing tag that matches nothing, in a template that says it is part of something else.
 *
 * <p>The plugin gives the IDE an HTML view of a Latte file, and the IDE checks that view the way
 * it checks any HTML: a {@code </div>} with no {@code <div>} in front of it is a closing tag that
 * matches nothing. That check assumes the file is a whole document. A template that declares a
 * parent is not one - the layout opens the element and the block closes it, and each file on its
 * own is unbalanced on purpose.
 *
 * <p>197 of the reports over a corpus of 2 867 templates were this, more than every other report
 * from that layer put together, and every one of them underlined a template that was right.
 *
 * <p>Only a template that declares a parent is quietened, and only this one report in it. A
 * template that declares none may well be a whole document, so its closing tags are checked as
 * before - and so are all the other things the layer says about markup that really is broken.
 */
public class DanglingCloseTagInAFragmentTest extends BasePlatformTestCase {

    private static final String DANGLING = "Closing tag matches nothing";

    public void testATemplateWithALayoutIsQuiet() {
        assertQuiet("{layout '@layout.latte'}\n{block content}\n</div>\n{/block}\n");
    }

    public void testATemplateThatExtendsAnotherIsQuiet() {
        assertQuiet("{extends '@layout.latte'}\n{block content}\n</div>\n{/block}\n");
    }

    /** The closing tag need not be inside a block; the whole file is a fragment. */
    public void testTheReportIsQuietAnywhereInSuchATemplate() {
        assertQuiet("{layout '@layout.latte'}\n</div>\n</section>\n");
    }

    /** The counterweight. A template that names no parent may be a whole document. */
    public void testATemplateWithNoParentIsStillReported() {
        assertReported("</div>\n");
        assertReported("{block content}\n</div>\n{/block}\n");
    }

    /** And one that says it has no parent is saying exactly that it is a whole document. */
    public void testATemplateThatSaysItHasNoParentIsStillReported() {
        assertReported("{layout none}\n</div>\n");
        assertReported("{extends none}\n</div>\n");
    }

    /**
     * The other half of the counterweight: nothing else the layer says is touched, in a fragment
     * or anywhere else. A start tag closed by the wrong one is a mistake wherever it is written.
     */
    public void testWhatIsGenuinelyBrokenIsStillReported() {
        assertHas("{layout '@layout.latte'}\n<table><tr><th>x</td></tr></table>\n",
            "Start tag has wrong closing tag");
        assertHas("{layout '@layout.latte'}\n<div class=\"a\" class=\"b\">x</div>\n",
            "Duplicate attribute");
    }

    private void assertQuiet(String template) {
        assertFalse(template + " -> " + reportsOn(template), reportsOn(template).contains(DANGLING));
    }

    private void assertReported(String template) {
        assertTrue(template + " -> " + reportsOn(template), reportsOn(template).contains(DANGLING));
    }

    private void assertHas(String template, String description) {
        List<String> reports = reportsOn(template);
        assertTrue(template + " -> " + reports,
            reports.stream().anyMatch(one -> one.contains(description)));
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
