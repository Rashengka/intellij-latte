package dev.noctud.latte.editorActions;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * What typing a bracket does in a template.
 *
 * <p>A brace opens a Latte tag anywhere in the text, so typing one closes it as well. A parenthesis
 * or a square bracket means something only inside a tag; in the HTML text around it, pairing it put a
 * character there the author did not type. Typing a closing brace in front of an existing one steps
 * over it inside a tag - that is the tag's own brace - and outside one it is a character like any
 * other, which stepping over would swallow.
 *
 * <p>The cases that did not change are here with those that did, measured before the change.
 */
public class TypingInATemplateTest extends BasePlatformTestCase {

    public void testABraceInTextOpensATagAndClosesIt() {
        assertTyped("<p><caret></p>\n", "{", "<p>{<caret>}</p>\n");
    }

    public void testBracketsInsideATagArePaired() {
        assertTyped("<p>{if <caret>}</p>\n", "(", "<p>{if (<caret>)}</p>\n");
        assertTyped("<p>{var $a = <caret>}</p>\n", "[", "<p>{var $a = [<caret>]}</p>\n");
        assertTyped("{var $a = <caret>}\n", "'", "{var $a = '<caret>'}\n");
    }

    public void testBracketsInHtmlTextAreJustCharacters() {
        assertTyped("<p>x <caret></p>\n", "(", "<p>x (<caret></p>\n");
        assertTyped("<p>x <caret></p>\n", "[", "<p>x [<caret></p>\n");
    }

    public void testAClosingBraceInsideATagStepsOverTheTagsOwn() {
        assertTyped("<p>{$a<caret>}</p>\n", "}", "<p>{$a}<caret></p>\n");
        assertTyped("{if foo(<caret>)}x{/if}\n", ")", "{if foo()<caret>}x{/if}\n");
    }

    public void testAClosingBraceInTextIsTyped() {
        assertTyped("<p><caret>}</p>\n", "}", "<p>}<caret>}</p>\n");
    }

    /** Script and style are the embedded languages' business; what they do there is left as it was. */
    public void testScriptAndStyleKeepTheirOwnPairing() {
        assertTyped("<script>\nfoo<caret>\n</script>\n", "(", "<script>\nfoo(<caret>)\n</script>\n");
        assertTyped("<style>\np <caret>\n</style>\n", "{", "<style>\np {<caret>}\n</style>\n");
    }

    private void assertTyped(String before, String typed, String after) {
        myFixture.configureByText("typing.latte", before);
        myFixture.type(typed);
        myFixture.checkResult(after);
    }
}
