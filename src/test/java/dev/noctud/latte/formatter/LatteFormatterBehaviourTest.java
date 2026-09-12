package dev.noctud.latte.formatter;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * What the formatter does to a template. Nothing tested it: LatteFormatterTest parses and pairs, and
 * its comment said formatting could not be tested without a platform fixture, which this is.
 *
 * <p>Each case was measured before it was written down. Two properties hold for all of them and are
 * checked on every one: the formatter changes whitespace only, and formatting a second time changes
 * nothing more.
 */
public class LatteFormatterBehaviourTest extends BasePlatformTestCase {

    public void testPairTagsIndentWhatTheyContain() {
        assertFormatted(
            "{block content}\n{if $a}\n{foreach $items as $i}\n<p>{$i}</p>\n{/foreach}\n{/if}\n{/block}\n",
            "{block content}\n    {if $a}\n        {foreach $items as $i}\n            <p>{$i}</p>\n        {/foreach}\n    {/if}\n{/block}\n");
    }

    public void testTagsAndHtmlIndentEachOther() {
        assertFormatted("<div>\n{if $a}\n<p>x</p>\n{/if}\n</div>\n", "<div>\n    {if $a}\n        <p>x</p>\n    {/if}\n</div>\n");
        assertFormatted("{if $a}\n<div>\n<p>x</p>\n</div>\n{/if}\n", "{if $a}\n    <div>\n        <p>x</p>\n    </div>\n{/if}\n");
    }

    public void testBranchesStandAtTheLevelOfTheirIf() {
        assertFormatted("{if $a}\na\n{elseif $b}\nb\n{else}\nc\n{/if}\n", "{if $a}\n    a\n{elseif $b}\n    b\n{else}\n    c\n{/if}\n");
    }

    public void testNAttributesIndentLikeTheElementTheyAreOn() {
        assertFormatted("<ul n:if=\"$items\">\n<li n:foreach=\"$items as $i\">{$i}</li>\n</ul>\n",
            "<ul n:if=\"$items\">\n    <li n:foreach=\"$items as $i\">{$i}</li>\n</ul>\n");
    }

    public void testScriptIsIndentedAsItsOwnLanguage() {
        assertFormatted("<script>\nvar a = {$a};\nif (a) {\nfoo();\n}\n</script>\n",
            "<script>\n    var a = {$a};\n    if (a) {\n        foo();\n    }\n</script>\n");
    }

    public void testWhatIsAlreadyInShapeStaysAsItIs() {
        assertUnchanged("<p>{if $a}x{else}y{/if}</p>\n");
        assertUnchanged("{* a comment\n  over lines *}\n<p>x</p>\n");
        assertUnchanged("{block content}\n    {if $a}\n        <p>x</p>\n    {/if}\n{/block}\n");
    }

    /**
     * A tag, a block element and another tag inside an element: every formatting added one more line
     * before the block element, so the file grew each time it was formatted. Taken apart, it needs all
     * four pieces - without the second tag, with a {@code <p>} instead of a {@code <div>}, or without the
     * element around them it is stable.
     */
    public void testATagBeforeABlockElementAndAnotherAfterItIsStable() {
        assertStable("<div class=\"row\">\n    {control grid}\n    <div class=\"wrap\">\n    </div>\n    {control grid}\n</div>\n");
        assertStable("<div class=\"row\">\n    {$a}\n    <div class=\"wrap\"></div>\n    {$a}\n</div>\n");
        assertStable("<div class=\"row\">\n                {control grid}\n      <div class=\"wrap\">\n                </div>\n"
            + "                {control grid}\n            </div>\n");
    }

    /**
     * A tag that stands alone on its line, then a blank line, then an element. It looks like the shape
     * above but was stable before the fix too; this keeps it so.
     */
    public void testATagOnItsOwnLineBeforeABlankLineIsStable() {
        assertStable("<div>\n{include 'part.latte'}\n\n<table>\n<tr><td>x</td></tr>\n</table>\n</div>\n");
        assertStable("{block content}\n<div>\n{control grid}\n\n<div class=\"wrap\">\n<p>x</p>\n</div>\n</div>\n{/block}\n");
        assertStable("{include 'part.latte'}\n\n<p>x</p>\n");
    }

    /** A tag in the middle of a rule in a stylesheet, where the formatter works on CSS and Latte at once. */
    public void testATagInsideAStyleRuleIsStable() {
        assertStable("<style>\n.item {\ncolor: red;\n{if $a} margin: 0;{/if}\n}\n</style>\n");
    }

    private void assertStable(String template) {
        PsiFile file = myFixture.configureByText("stable.latte", template);
        reformat(file);
        String once = myFixture.getEditor().getDocument().getText();
        assertEquals("the formatter changed more than whitespace", template.replaceAll("\\s+", ""), once.replaceAll("\\s+", ""));
        reformat(file);
        assertEquals("formatting a second time changed it again", once, myFixture.getEditor().getDocument().getText());
    }

    private void assertUnchanged(String template) {
        assertFormatted(template, template);
    }

    private void assertFormatted(String before, String after) {
        PsiFile file = myFixture.configureByText("format.latte", before);
        reformat(file);
        String once = myFixture.getEditor().getDocument().getText();
        assertEquals(after, once);
        assertEquals("the formatter changed more than whitespace", before.replaceAll("\\s+", ""), once.replaceAll("\\s+", ""));
        reformat(file);
        assertEquals("formatting a second time changed it again", once, myFixture.getEditor().getDocument().getText());
    }

    private void reformat(PsiFile file) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            CodeStyleManager.getInstance(getProject()).reformat(file);
        });
    }
}
