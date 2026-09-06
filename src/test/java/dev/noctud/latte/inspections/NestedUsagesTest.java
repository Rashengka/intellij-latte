package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * What is written inside a call is code too.
 *
 * Every one of these inspections walks the file with the same shape - match an element, report on
 * it, and descend only into what did not match. So the first call in an expression was looked at
 * and everything written inside it was not: {@code {do outer(inner($v))}} reported the outer name
 * and said nothing about the inner one.
 *
 * It is a missing report rather than a false one, which is why it went unnoticed - and why it
 * matters beyond itself: silence over a nested call proved nothing, so no measurement that relied
 * on "nothing was reported" could be trusted about anything nested.
 */
public class NestedUsagesTest extends BasePlatformTestCase {

    private static final String GLOBAL_PHP =
        "<?php\n"
            + "\n"
            + "function strtoupper(string $string): string {}\n"
            + "\n"
            + "class Known\n"
            + "{\n"
            + "    public const SIZE = 1;\n"
            + "    public static int $count = 0;\n"
            + "    public int $length = 0;\n"
            + "    public static function make(): self {}\n"
            + "}\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("stubs/global.php", GLOBAL_PHP);
        myFixture.enableInspections(
            new MethodUsagesInspection(),
            new ClassUsagesInspection(),
            new ConstantUsagesInspection(),
            new PropertyUsagesInspection(),
            new StaticPropertyUsagesInspection()
        );
    }

    /** The case the finding was written from. */
    public void testAFunctionCalledInsideAnotherCallIsLookedUpToo() {
        assertEquals(
            List.of("WARNING:Function 'noSuchOuter' not found", "WARNING:Function 'noSuchInner' not found"),
            problemsIn("{do $a = noSuchOuter(noSuchInner($v))}\n")
        );
    }

    /** And the outer one being fine must not silence the inner one. */
    public void testTheInnerCallIsLookedUpEvenWhenTheOuterOneIsKnown() {
        assertEquals(
            List.of("WARNING:Function 'noSuchInner' not found"),
            problemsIn("{do $a = strtoupper(noSuchInner($v))}\n")
        );
    }

    /**
     * The counterweight: a call written as a filter argument was found all along. It says the walk
     * is not broken everywhere - what it could not do was descend into a call it had just matched,
     * which is exactly the shape the two cases above have and this one does not.
     */
    public void testACallInsideAFilterArgumentIsLookedUpToo() {
        assertEquals(
            List.of("WARNING:Function 'noSuchInner' not found"),
            problemsIn("{$items|batch: noSuchInner($v)}\n")
        );
    }

    private List<String> problemsIn(String template) {
        myFixture.configureByText("nested.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getSeverity().getName() + ":" + info.getDescription());
            }
        }
        return problems;
    }
}
