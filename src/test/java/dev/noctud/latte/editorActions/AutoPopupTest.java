package dev.noctud.latte.editorActions;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;

/**
 * Where typing a character opens the completion popup in a template.
 *
 * <p>The popup is asked for on {@code : $ | { \} anywhere in a Latte file, including the HTML text,
 * which read like a popup on every colon in a sentence. It is not: with nothing to offer the popup does
 * not show, and in the text there is nothing to offer. Both directions are held here, because either
 * one alone would pass a popup that shows everywhere or nowhere.
 */
public class AutoPopupTest extends BasePlatformTestCase {

    private CompletionAutoPopupTester tester;

    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tester = new CompletionAutoPopupTester(myFixture);
    }

    public void testTypingInTheTextOpensNoPopup() throws Throwable {
        assertNoPopup("<p>Note<caret></p>\n", ":");
        assertNoPopup("<p>a <caret></p>\n", "|");
        assertNoPopup("<p>costs <caret></p>\n", "$");
        assertNoPopup("<p>C:<caret></p>\n", "\\");
    }

    public void testTypingInATagOpensThePopup() throws Throwable {
        assertPopup("{<caret>}\n", "$");
        assertPopup("{$a<caret>}\n", "|");
        assertPopup("{$a|truncate<caret>}\n", ":");
    }

    private void assertPopup(String template, String typed) throws Throwable {
        assertTrue("no popup after typing " + typed + " in " + template, popupAfterTyping(template, typed));
    }

    private void assertNoPopup(String template, String typed) throws Throwable {
        assertFalse("a popup after typing " + typed + " in " + template, popupAfterTyping(template, typed));
    }

    private boolean popupAfterTyping(String template, String typed) throws Throwable {
        boolean[] shown = {false};
        tester.runWithAutoPopupEnabled(() -> {
            EdtTestUtil.runInEdtAndWait(() -> myFixture.configureByText("popup.latte", template));
            tester.typeWithPauses(typed);
            EdtTestUtil.runInEdtAndWait(() -> {
                shown[0] = LookupManager.getActiveLookup(myFixture.getEditor()) != null;
                LookupManager.hideActiveLookup(getProject());
            });
        });
        return shown[0];
    }
}
