package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * A call is looked up by name alone, so the shape of the function's parameter list has no say in
 * whether it is found. Written down because {@code max} was reported as not existing while
 * {@code count} and {@code implode} were not, which reads as the lookup tripping over a variadic
 * signature - and it does not. What was missing was the function itself: a fixture project indexes
 * only the PHP given to it, and the standard library stub that stands in for the runtime named
 * four functions, {@code max} not among them.
 *
 * <p>The shapes below are the ones the standard library is actually written in - variadic,
 * optional, by-reference, and a mixture - so a lookup that started depending on any of them would
 * be caught here rather than by whoever next opened a template that calls one.
 */
public class FunctionSignatureLookupTest extends BasePlatformTestCase {

    private static final String STANDARD_LIBRARY =
        "<?php\n"
            + "\n"
            + "function count(mixed $value, int $mode = 0): int {}\n"
            + "function implode(string $separator, array $array): string {}\n"
            + "function max(mixed ...$values): mixed {}\n"
            + "function min(mixed ...$values): mixed {}\n"
            + "function array_merge(array ...$arrays): array {}\n"
            + "function array_filter(array $array, ?callable $callback = null, int $mode = 0): array {}\n"
            + "function sprintf(string $format, mixed ...$values): string {}\n"
            + "function sort(array &$array, int $flags = 0): bool {}\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("stubs/standard-library.php", STANDARD_LIBRARY);
        myFixture.enableInspections(new MethodUsagesInspection());
    }

    public void testAVariadicFunctionIsFoundAtEveryArity() {
        assertEquals(List.of(), problemsIn("{do $a = max($b)}\n"));
        assertEquals(List.of(), problemsIn("{do $a = max(0, $b)}\n"));
        assertEquals(List.of(), problemsIn("{do $a = min(0, $b, 2)}\n"));
        assertEquals(List.of(), problemsIn("{do $a = array_merge($b, $c)}\n"));
    }

    public void testAFixedFunctionAndAMixedOneAreFoundToo() {
        assertEquals(List.of(), problemsIn("{implode(', ', $items)}\n"));
        assertEquals(List.of(), problemsIn("{sprintf('%s', $name)}\n"));
        assertEquals(List.of(), problemsIn("{do $a = count($items)}\n"));
    }

    public void testAByReferenceParameterDoesNotHideTheFunction() {
        assertEquals(List.of(), problemsIn("{do sort($items)}\n"));
    }

    public void testTheCallIsFoundWhereverInTheExpressionItStands() {
        assertEquals(List.of(), problemsIn("{do $a['rest'] = max(0, ($a['total'] - $a['shown']))}\n"));
        assertEquals(List.of(), problemsIn("<p title=\"{max(0, $b)}\">t</p>\n"));
        assertEquals(List.of(), problemsIn("{if max(0, $b) > 1}t{/if}\n"));
    }

    /**
     * A name that is in no index at all still has to be reported, or the four assertions above
     * would pass just as well on a lookup that gave up on function calls.
     */
    public void testAFunctionThatIsNowhereIsStillReported() {
        assertEquals(
            List.of("WARNING:Function 'no_such_function' not found"),
            problemsIn("{do $a = no_such_function(1)}\n")
        );
    }

    private List<String> problemsIn(String template) {
        myFixture.configureByText("template.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getSeverity().getName() + ":" + info.getDescription());
            }
        }
        return problems;
    }
}
