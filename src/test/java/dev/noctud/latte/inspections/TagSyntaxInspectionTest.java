package dev.noctud.latte.inspections;

import dev.noctud.latte.BasePsiParsingTestCase;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.inspections.utils.LatteInspectionInfo;
import dev.noctud.latte.settings.LatteSettings;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Syntax inside a tag that Latte refuses is reported; syntax Latte takes is left alone.
 *
 * <p>The grammar reads the content of a tag leniently on purpose - anything it does not recognise is
 * kept as a run of tokens - so a tag Latte cannot compile used to go through without a word:
 * {@code {= [1, 2}}, {@code {= $a +}}, {@code {if}}. Only an unterminated tag and an unterminated
 * string made the parser say anything.
 *
 * <p>Every entry below was measured against both ends of the supported range, 2.11.7 and 3.1.6:
 * {@code compile()}, then {@code php -l} over the generated code. 2.11.7 compiles about half of the
 * refused shapes and emits PHP that does not parse, which is a refusal all the same. The three lists
 * are the whole point, and the second and third are the counterweight: a check that reported
 * everything would pass the first.
 */
public class TagSyntaxInspectionTest extends BasePsiParsingTestCase {

    /** Refused by both 2.11.7 and 3.1.6. */
    private static final String[] BOTH_REFUSE = {
        // a bracket left open
        "{= [1, 2}", "{= foo(}", "{= (}", "{= [}", "{= $a[}", "{= foo(1,}",
        // a bracket that closes nothing
        "{= )}", "{= ]}", "{= $a)}",
        // the same in an array spread over several lines, where a closing bracket comes together with
        // what follows it as one run of arguments
        "{php dump([\n\"parent\" => [\n\"name\" => $a->getName(),\n],\n)}",
        "{php dump([\n\"parent\" => [\n\"name\" => $a->getName(),\n])}",
        "{php dump([\n\"parent\" => [\n\"name\" => $a->getName(),\n]],\n])}",
        // an operator with nothing after it
        "{= $a +}", "{= $a -}", "{= $a *}", "{= $a .}", "{= $a &&}", "{= $a ??}", "{= $a ?}", "{= $a ?:}",
        "{= $a ? 1 :}", "{= $a->}", "{= $a::}", "{= $a =>}", "{= $a instanceof}", "{= $a = }",
        "{do $a = ;}", "{php $a = ;}",
        // a filter with no name
        "{$a|}", "{$a|:}",
        // an expression that starts with an operator
        "{= ,}", "{= * $a}",
        // two operators where only the second could be read as a sign
        "{= $a * * $b}", "{= $a + * $b}", "{do $a +* 2}",
        // a foreach that names nothing to iterate into
        "{foreach $items as}{/foreach}",
        // a tag that needs its argument and has none
        "{=}", "{= }", "{do}", "{if}\n{/if}", "{if $a}x{elseif}y{/if}", "{while}\n{/while}",
        "{foreach}\n{/foreach}", "{for}\n{/for}", "{ifset}\n{/ifset}",
    };

    /** Taken by both 2.11.7 and 3.1.6. */
    private static final String[] BOTH_TAKE = {
        "{= $a + 1}", "{= [1, 2]}", "{= [1, 2][0]}", "{= strlen($a)}", "{foreach $items as $i}{$i}{/foreach}",
        "{= 'ok'}", "{$a|upper}", "{= $a ? 1 : 2}", "{var $a = 1}", "{do $a = 1}", "{if true}x{/if}",
        "{= $a?->b}", "{= $a['x'] ?? 1}", "{block a}x{/block}",
        "{= $a ?: 1}", "{= $a ?? 1}", "{= -1}", "{= $a + -1}", "{= !$a}", "{= $a * -$b}",
        "{$a|upper|lower}", "{$a|truncate:10}", "{$a|replace:'a','b'}", "{= fn($x) => $x}", "{= [1 => 2]}",
        "{foreach $items as $k => $v}{/foreach}", "{= $a::class}", "{= Foo::BAR}", "{= $a->b()}",
        "{do $a[] = 1}", "{= \"a{$b}c\"}", "{= '}'}", "{= foo(a: 1)}", "{= foo(...$a)}",
        "{var $a = [1, 2,]}", "{= max(1, 2,)}", "{= $a instanceof Foo}", "{= (int) $a}", "{= $a++}",
        "{= $a--}", "{= $a <=> $b}", "{= $a ** 2}", "{= $a . 'x'}", "{do $a .= 'x'}", "{= $a & $b}",
        "{= $a?->b?->c}", "{do $a = $b = 1}", "{if $a && ($b || $c)}x{/if}", "{= $a[$b[0]]}", "{= @$a}",
        "{= new Foo}", "{= match($a) { 1 => 'x', default => 'y' }}", "{= $a->{'b'}}",
        // the short ternary: a ? with no : after it is taken by both
        "{= $a ? 1}",
        // arrays spread over several lines, where the lexer hands over a closing bracket together with
        // what follows it as one run of arguments - "]," and "])"
        "{php dump([\n\"parent\" => [\n\"name\" => $a->getName(),\n],\n\"child\" => [\n\"name\" => $a->getName(),\n],\n])}",
        "{php dump([\n\"total\" => $a[\"year\"] . \"/\" . $a[\"month\"],\n\"\\$b\" => $b,\n\"\\$c\" => null !== $b ? $a - $b : null,\n])}",
    };

    /** Taken by one end of the range and refused by the other: nothing is said about these. */
    private static final String[] ONE_END_TAKES = {
        "{= $a /* ) */}",           // 2.11.7 refuses the bracket in the comment
        "{php $a = 1; $b = 2;}",    // 3.1.6 refuses the semicolons
        "{= $a ? $b : $c ?: $d}",   // 2.11.7 refuses the unparenthesised nesting
        "{= $a,}",                  // 2.11.7 takes the trailing comma
        "{$a|truncate:}",           // 2.11.7 takes a filter argument left empty
        "{switch}\n{/switch}",      // 3.1.6 takes a switch without a subject
        // 3.1.6 refuses the semicolon, 2.11.7 takes the whole tag - brackets closed across lines included
        "{php $items[] = [\n'from' => $a,\n'label' => ($a && $b) ? $a->format('j') . ' - ' . $b->format('j') : '',\n];}",
        "{php array_unshift($items, [\n'from' => null,\n'label' => '',\n]);}",
        "{php $items[] = [1, 2];}",
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LatteConfiguration.getInstance(getProject());
        getProject().registerService(LatteSettings.class);
    }

    @Override
    protected String getTestDataPath() {
        return "";
    }

    @Test
    public void testEverythingBothEndsRefuseIsReported() {
        List<String> quiet = new ArrayList<>();
        for (String template : BOTH_REFUSE) {
            if (problemsIn(template).isEmpty()) {
                quiet.add(template);
            }
        }
        Assert.assertEquals("Latte refuses these at both ends of the range, so the plugin has to say so",
            List.of(), quiet);
    }

    @Test
    public void testEverythingBothEndsTakeIsQuiet() {
        assertQuiet("Latte takes these at both ends of the range", BOTH_TAKE);
    }

    @Test
    public void testWhatOneEndTakesIsQuietToo() {
        assertQuiet("one end of the range takes these, so the plugin cannot prove them wrong", ONE_END_TAKES);
    }

    /**
     * A {@code {var}} left without its value is LatteTagVar's to report, and it does. Saying it twice
     * would put two errors on one mistake.
     */
    @Test
    public void testAVarWithoutItsValueIsLeftToTheVarInspection() {
        Assert.assertEquals(List.of(), problemsIn("{var $a = }"));
    }

    private void assertQuiet(String why, String[] templates) {
        List<String> reported = new ArrayList<>();
        for (String template : templates) {
            List<String> problems = problemsIn(template);
            if (!problems.isEmpty()) {
                reported.add(template + " -> " + problems);
            }
        }
        Assert.assertEquals(why + ", so none of them may be reported", List.of(), reported);
    }

    private List<String> problemsIn(String template) {
        List<String> descriptions = new ArrayList<>();
        for (LatteInspectionInfo problem : new TagSyntaxInspection().checkFile(createPsiFile("a", template))) {
            descriptions.add(problem.getDescription());
        }
        return descriptions;
    }
}
