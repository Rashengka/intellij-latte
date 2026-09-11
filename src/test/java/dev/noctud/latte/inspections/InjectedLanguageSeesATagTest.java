package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * A Latte tag standing where CSS or JavaScript expects something of their own.
 *
 * <p>The plugin gives the IDE an HTML view of the template so that the markup, the styles and the
 * scripts in it are highlighted and checked, and that view is the template with every Latte tag
 * taken out of the text. What CSS and JavaScript are handed is therefore the template with holes
 * in it, and a hole in the wrong place is not something either of them can parse.
 *
 * <p>Most holes are somewhere they can live with, and those are here as the counterweight: a tag
 * standing for a whole CSS value, for a value with a unit after it, for a property name, for a
 * JavaScript value or for a whole statement is quiet, and has to stay quiet whatever is done about
 * the two that are not.
 *
 * <p>Measured over a corpus of 2 866 templates: six reports of these two shapes, against
 * thirty-three from the same layer that name something genuinely wrong with the template. That
 * ratio is why nothing here is silenced - taking the layer away would take those with it.
 */
public class InjectedLanguageSeesATagTest extends BasePlatformTestCase {

    public void testATagStandingForAWholeCssValueIsQuiet() {
        assertQuiet("<div style=\"background-color: {$colour}\">x</div>\n");
        assertQuiet("<div style=\"width: {$width}px\">x</div>\n");
        assertQuiet("<div style=\"{$property}: red\">x</div>\n");
    }

    public void testATagStandingForAJavaScriptValueOrStatementIsQuiet() {
        assertQuiet("<script>\nvar a = {$value};\n</script>\n");
        assertQuiet("<script>\n{$statement|noescape}\n</script>\n");
        assertQuiet("<script>\n{foreach $items as $i}var a{$i} = 1;\n{/foreach}\n</script>\n");
    }

    /**
     * The hash of a hex colour with the tag standing for its digits, which is how a template writes
     * a colour it was given without one. CSS used to be left with a hash and nothing after it.
     */
    public void testAHashBeforeATagIsQuiet() {
        assertQuiet("<div style=\"background-color: #{$colour}\">x</div>\n");
        assertQuiet("<style>\n.a { color: #{$colour}; }\n</style>\n");
    }

    /**
     * A tag standing for the name of a variable, which is how a template names one per item of a
     * loop. JavaScript used to be left with {@code var  = 1} and read the brace that opened the tag
     * as a binding of its own.
     */
    public void testATagNamingAJavaScriptVariableIsQuiet() {
        assertQuiet("<script>\nvar {$name|noescape} = 1;\n</script>\n");
    }

    /**
     * The counterweight for the rule that only a printing tag gets text in its place. A tag that
     * does not print stands between two statements, and a word there glues onto whatever follows -
     * this one became {@code latte00var} and a report that had never been there before.
     */
    public void testATagThatPrintsNothingLeavesNoWordBehind() {
        assertQuiet("<script>\n{foreach $items as $i}var a{$i} = 1;\n{/foreach}\n</script>\n");
        assertQuiet("<script>\n{if $a}var b = 1;{/if}\n</script>\n");
        assertQuiet("<div n:if=\"$a\">x</div>\n");
    }

    /** And a hash with something after it is a colour like any other. */
    public void testAHashWithDigitsAfterItIsQuiet() {
        assertQuiet("<div style=\"background-color: #ff0000\">x</div>\n");
    }

    /**
     * The counterweight to all of the above. Every one of those is met just as well by a view that
     * hands CSS and JavaScript nothing at all, so what is genuinely broken in them has to go on
     * being reported - with a tag standing next to it, and without one.
     */
    public void testWhatIsGenuinelyBrokenInCssOrJavaScriptIsStillReported() {
        assertFalse(reportsOn("<script>\nvar = 1;\n</script>\n").isEmpty());
        assertFalse(reportsOn("<script>\nvar a = {$value};\nvar = 1;\n</script>\n").isEmpty());
        assertFalse(reportsOn("<style>\n.a { color: ; }\n</style>\n").isEmpty());
        assertFalse(reportsOn("<style>\n.a { color: {$colour}; width: ; }\n</style>\n").isEmpty());
        assertFalse(reportsOn("<div style=\"color: ;\">x</div>\n").isEmpty());
    }

    private void assertQuiet(String template) {
        assertEquals(template, List.of(), reportsOn(template));
    }

    private List<String> reportsOn(String template) {
        myFixture.configureByText("page.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getSeverity() + ":" + info.getDescription());
            }
        }
        return problems;
    }
}
