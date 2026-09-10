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
     * a colour it was given without one. CSS is left with a hash and nothing after it.
     */
    public void testAHashBeforeATagIsReported() {
        assertReports("<div style=\"background-color: #{$colour}\">x</div>\n", "Term expected");
        assertReports("<style>\n.a { color: #{$colour}; }\n</style>\n", "Term expected");
    }

    /**
     * A tag standing for the name of a variable, which is how a template names one per item of a
     * loop. JavaScript is left with {@code var  = 1} and reads the brace that opened the tag as a
     * binding of its own.
     */
    public void testATagNamingAJavaScriptVariableIsReported() {
        assertReports("<script>\nvar {$name|noescape} = 1;\n</script>\n", "Newline or semicolon expected");
    }

    /** And a hash with something after it is a colour like any other. */
    public void testAHashWithDigitsAfterItIsQuiet() {
        assertQuiet("<div style=\"background-color: #ff0000\">x</div>\n");
    }

    private void assertQuiet(String template) {
        assertEquals(template, List.of(), reportsOn(template));
    }

    private void assertReports(String template, String description) {
        assertEquals(template, List.of("ERROR(400):" + description), reportsOn(template));
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
