package dev.noctud.latte.parser;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import dev.noctud.latte.BasePsiParsingTestCase;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.psi.LattePairMacro;
import dev.noctud.latte.settings.LatteSettings;
import dev.noctud.latte.settings.LatteTagSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {syntax} switches the tag delimiters for the rest of the template and {/syntax} switches them
 * back. Closing it is optional in every supported version, so a lone {syntax off} is correct code
 * and must not be parsed as a pair macro missing its closing tag - that is what made the annotator
 * report "Unclosed tag syntax" on a template that compiles.
 *
 * @see dev.noctud.latte.annotator.LatteSyntaxModeTest for the accepted mode arguments
 */
public class SyntaxTagPairingTest extends BasePsiParsingTestCase {

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
    public void testUnclosedSyntaxTagIsNotAPairMacro() {
        PsiFile file = parseFile("UnclosedSyntax.latte", "{syntax off}\nBraces here are plain text: { $a }\n");

        Assert.assertEquals(
            "A {syntax} tag with no {/syntax} must not be parsed as a pair macro",
            0,
            pairMacros(file, "syntax").size()
        );
    }

    @Test
    public void testClosedSyntaxTagIsAPairMacro() {
        PsiFile file = parseFile("ClosedSyntax.latte", "{syntax off}\nplain text\n{/syntax}\n");

        List<LattePairMacro> macros = pairMacros(file, "syntax");
        Assert.assertEquals("A {syntax} tag closed by {/syntax} is a pair macro", 1, macros.size());
        Assert.assertNotNull("The pair macro keeps its closing tag", macros.get(0).getCloseTag());
    }

    /**
     * Only the last {syntax} of the three has a {/syntax}; the other two switch the mode and are
     * left open. This is the shape that was reported from a real template.
     */
    @Test
    public void testOnlyTheClosedOneOfSeveralSyntaxTagsIsAPairMacro() {
        PsiFile file = parseFile(
            "SeveralSyntaxModes.latte",
            "{syntax latte}\n{var $a = 1}\n{$a}\n{syntax double}\n{{$a}}\n{syntax off}\nplain\n{/syntax}\n"
        );

        Assert.assertEquals(1, pairMacros(file, "syntax").size());
    }

    @Test
    public void testSyntaxIsRegisteredAsATagThatNeedNotBeClosed() {
        LatteTagSettings syntax = LatteConfiguration.getInstance(getProject()).getTag("syntax");

        Assert.assertNotNull(syntax);
        Assert.assertEquals(
            "{syntax} must not be registered as PAIR - {/syntax} is optional",
            LatteTagSettings.Type.AUTO_EMPTY,
            syntax.getType()
        );
    }

    private List<LattePairMacro> pairMacros(@NotNull PsiFile file, @NotNull String macroName) {
        List<LattePairMacro> found = new ArrayList<>();
        file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof LattePairMacro
                    && macroName.equals(((LattePairMacro) element).getOpenTag().getMacroName())) {
                    found.add((LattePairMacro) element);
                }
                super.visitElement(element);
            }
        });
        return found;
    }
}
