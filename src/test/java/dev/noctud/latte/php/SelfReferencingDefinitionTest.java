package dev.noctud.latte.php;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.LatteLanguage;

import java.util.ArrayList;
import java.util.List;

/**
 * A variable defined from itself, which reads its own type forever.
 *
 * <p>{@code {var $a = $a}} is what a template writes when it narrows or re-declares a name it
 * already has, and the type of {@code $a} is then read as: the definition's value, which is
 * {@code $a}, whose last definition is that same one. The walk in {@link LattePhpTypeDetector} had
 * nothing to stop it, so the type of one variable was a {@code StackOverflowError} - and it took
 * the whole inspection pass down with it, not just that one report.
 *
 * <p>It was found on a real template, and the reason it went unnoticed for so long is that the
 * corpus was only ever inspected 400 templates at a time; this one is number 2 255.
 *
 * <p>The answer for a cycle is {@code mixed}: what a template says about its own type in a circle
 * is not something the plugin can work out, and what it cannot work out it says nothing about.
 */
public class SelfReferencingDefinitionTest extends BasePlatformTestCase {

    /**
     * Every inspection the plugin registers, not a chosen few.
     *
     * <p>The first version of this test switched on {@code VariablesInspection} alone and passed
     * against the broken code - the walk that loops is reached from an inspection that reads a
     * type off a variable, and picking inspections by hand is how a reproduction quietly stops
     * reproducing. What an editor has open is all of them.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        List<LocalInspectionTool> tools = new ArrayList<>();
        for (LocalInspectionEP ep : LocalInspectionEP.LOCAL_INSPECTION.getExtensionList()) {
            if (LatteLanguage.INSTANCE.getID().equals(ep.language)) {
                tools.add((LocalInspectionTool) ep.instantiateTool());
            }
        }
        assertFalse("the plugin registers no Latte inspection at all", tools.isEmpty());
        myFixture.enableInspections(tools.toArray(new LocalInspectionTool[0]));
    }

    /**
     * The shape the corpus template had, narrowed to the two lines that are needed.
     *
     * <p>The self-reference alone is not enough and that is worth keeping in the test: printing
     * {@code {$a}} after it never asks the question that loops. It takes a second definition that
     * reads something <em>off</em> the circular name - a member, a method - for the walk to go
     * round.
     */
    public void testANameDefinedFromItselfAndThenReadThroughAMember() {
        highlight("{var $a = $a}\n{var $b = $a->child}\n");
    }

    public void testTheSameThroughAMethodCall() {
        highlight("{var $a = $a}\n{var $b = $a->child()}\n");
    }

    public void testTheSameInsideAForeachOverTheLoopVariable() {
        highlight("{foreach $items as $item}\n{var $item = $item}\n{var $b = $item->child}\n{/foreach}\n");
    }

    /**
     * Two and three names defined from each other close the same circle without any one of them
     * naming itself. Measured: neither of these reproduced the crash against the unfixed code, so
     * they are not what proves the guard - they are here because they are the shapes one reaches
     * for first, and because a later change must not be allowed to make them loop.
     */
    public void testTwoVariablesDefinedFromEachOther() {
        highlight("{var $a = $b}\n{var $b = $a}\n{var $c = $a->child}\n");
    }

    public void testThreeVariablesInACircle() {
        highlight("{var $a = $b}\n{var $b = $c}\n{var $c = $a}\n{var $d = $a->child}\n");
    }

    /** The self-reference on its own, which was quiet before and has to stay quiet. */
    public void testASelfReferenceThatIsOnlyPrintedIsUnchanged() {
        highlight("{var $a = $a}\n{$a}\n");
    }

    /**
     * The counterweight. A type that really is knowable has to stay knowable - a cycle guard that
     * answers "mixed" everywhere would pass every test above and quietly lose every type.
     */
    public void testAnOrdinaryChainOfDefinitionsStillResolves() {
        assertEquals(
            List.of("WARNING:Undefined variable 'unknown'"),
            problemsIn("{var $a = 'text'}\n{var $b = $a}\n{$b}\n{$unknown}\n")
        );
    }

    private void highlight(String template) {
        myFixture.configureByText("self-reference.latte", template);
        myFixture.doHighlighting();
    }

    private List<String> problemsIn(String template) {
        myFixture.configureByText("self-reference.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getSeverity().getName() + ":" + info.getDescription());
            }
        }
        return problems;
    }
}
