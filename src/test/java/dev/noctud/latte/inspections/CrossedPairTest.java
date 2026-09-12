package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;


/**
 * A pair tag closed by the closing tag of the pair around it - {@code {if}{block a}x{/if}} - is
 * refused by both ends of the supported range with the same words, "Unexpected {/if}, expecting
 * {/block}". The plugin said nothing about it.
 *
 * <p>Measured with 2.11.7 and 3.1.6 for every shape below. The other half is what keeps a check from
 * reporting everything: the same pairs closed in order compile on both, and a {@code {block}} left open
 * at the end of the file is closed by Latte itself on both - that one must stay quiet.
 */
public class CrossedPairTest extends BasePlatformTestCase {

    /** Refused by 2.11.7 and 3.1.6. */
    private static final String[] CROSSED = {
        "{if true}{block a}x{/if}\n",
        "{block a}{if true}x{/block}\n",
        "{foreach [1] as $x}{if $x}y{/foreach}{/if}\n",
        "{if true}{define a}x{/if}\n",
        "{ifset $a}{block b}x{/ifset}\n",
        "{if true}{capture $c}x{/if}\n",
        // what Latte closes at the end of the file is one block, at the top, and nothing else
        "{block a}{block b}x\n",
        "{foreach [1] as $x}{block a}y{/foreach}\n",
        "{define a}x\n",
        "{capture $c}x\n",
    };

    /** Taken by 2.11.7 and 3.1.6. */
    private static final String[] IN_ORDER = {
        "{if true}{block a}x{/block}{/if}\n",
        "{block a}{if true}x{/if}{/block}\n",
        "{foreach [1] as $x}{if $x}y{/if}{/foreach}\n",
        "{if true}{define a}x{/define}{/if}\n",
        "{ifset $a}{block b}x{/block}{/ifset}\n",
        "{if true}{capture $c}x{/capture}{/if}\n",
        "{block a}x\n",
        "{block a}x{/block}{block b}y\n",
        "<div>{block a}x</div>\n",
        "{block a}{if true}x{/if}\n",
        "{define a}x{/define}{block b}y\n",
        // a block closed by its own tag is closed, wherever it stands
        "{block a /}\n",
        "{if true}{block a /}{/if}\n",
        "{block a/}{block b}x\n",
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(ExpectedErrorsTest.registeredLatteInspections());
    }

    public void testAPairClosedByTheTagAroundItIsAnError() {
        List<String> quiet = new ArrayList<>();
        for (String template : CROSSED) {
            if (errorsIn(template).isEmpty()) {
                quiet.add(template.trim());
            }
        }
        assertEquals("Latte refuses these at both ends of the range, so the plugin has to say so", List.of(), quiet);
    }

    public void testPairsClosedInOrderAreQuiet() {
        List<String> reported = new ArrayList<>();
        for (String template : IN_ORDER) {
            List<String> errors = errorsIn(template);
            if (!errors.isEmpty()) {
                reported.add(template.trim() + " -> " + errors);
            }
        }
        assertEquals("Latte takes these at both ends of the range", List.of(), reported);
    }

    private List<String> errorsIn(String template) {
        myFixture.configureByText("crossed.latte", template);
        List<String> errors = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getSeverity() == HighlightSeverity.ERROR && info.getDescription() != null) {
                errors.add(info.getDescription());
            }
        }
        return errors;
    }
}
