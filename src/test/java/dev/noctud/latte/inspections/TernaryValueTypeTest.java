package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * The value of a ternary is one of its two branches, never its condition.
 *
 * <p>{@code {var $k = $o ? [1] : [2]}} gave {@code $k} the type of {@code $o}: the type of a
 * definition was read from the first statement after the {@code =}, and in a ternary that is the
 * condition. A nullable string as the condition made {@code {foreach $k as $v}} report that a
 * {@code string|null} cannot be walked, while both branches are arrays.
 *
 * <p>The short form {@code $o ?: [2]} gives back the condition itself when it is truthy, with the
 * falsy part of its type gone; the plugin does not narrow types like that, so it says nothing
 * about the result rather than something wrong.
 */
public class TernaryValueTypeTest extends BasePlatformTestCase {

    public void testBothBranchesArraysIsQuiet() {
        assertEquals(List.of(), reportsOn("{varType string|null $o}\n{var $k = $o ? [1] : [2]}\n"));
        assertEquals(List.of(), reportsOn("{varType bool $o}\n{var $k = $o ? [1] : [2]}\n"));
    }

    public void testTheShortFormSaysNothing() {
        assertEquals(List.of(), reportsOn("{varType string|null $o}\n{var $k = $o ?: [2]}\n"));
    }

    /** The counterweight: branches that cannot be walked are reported by what they are. */
    public void testBranchesThatCannotBeWalkedAreStillReported() {
        List<String> reports = reportsOn("{varType array $o}\n{var $k = $o ? 'a' : 'b'}\n");
        assertEquals(reports.toString(), 1, reports.size());
        assertTrue(reports.get(0), reports.get(0).contains("'string' provided"));
    }

    private List<String> reportsOn(String definitions) {
        myFixture.enableInspections(new LatteIterableTypeInspection());
        myFixture.configureByText("probe.latte", definitions + "{foreach $k as $v}{$v}{/foreach}\n");
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null && info.getDescription().contains("foreach")) {
                problems.add(info.getDescription());
            }
        }
        return problems;
    }
}
