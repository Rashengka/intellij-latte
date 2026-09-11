package dev.noctud.latte.annotator;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * The report on a pair tag that nobody closed.
 *
 * <p>{@code SyntaxTagPairingTest} holds one side of it: {@code {syntax}} may be left open, so a
 * lone one must not be reported. An annotator that never reported an unclosed tag at all would pass
 * that just as well, and the path in {@link LatteAnnotator} that says "Unclosed tag" was run by no
 * test in the suite.
 */
public class UnclosedTagTest extends BasePlatformTestCase {

    public void testAPairTagLeftOpenIsReported() {
        assertTrue(problemsIn("{if true}x\n").contains("ERROR:Unclosed tag if"));
        assertTrue(problemsIn("{foreach [1, 2] as $b}x\n").contains("ERROR:Unclosed tag foreach"));
    }

    public void testTheSameTagsClosedAreNot() {
        assertEquals(List.of(), problemsIn("{if true}x{/if}\n"));
        assertEquals(List.of(), problemsIn("{foreach [1, 2] as $b}{$b}{/foreach}\n"));
    }

    /** The side SyntaxTagPairingTest holds, repeated here so the two halves sit together. */
    public void testASyntaxTagLeftOpenIsNot() {
        assertEquals(List.of(), problemsIn("{syntax off}\nx\n"));
    }

    private List<String> problemsIn(String template) {
        myFixture.configureByText("unclosed.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getSeverity().getName() + ":" + info.getDescription());
            }
        }
        return problems;
    }
}
