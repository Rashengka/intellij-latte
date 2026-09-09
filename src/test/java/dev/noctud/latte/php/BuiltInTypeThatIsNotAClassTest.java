package dev.noctud.latte.php;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import dev.noctud.latte.BasePsiParsingTestCase;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.psi.LattePhpVariable;
import dev.noctud.latte.settings.LatteSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

/**
 * A built-in type that names no class is that type, not a class of the same name.
 *
 * <p>{@code void} used to come out of {@code {varType void $a}} as the class {@code \void}. It
 * looked harmless because a class nobody can find is silent, but the type shown was a lie and the
 * settings table flagged it as an undefined class. {@code never} and {@code false} were kept away
 * from the same fate only by a list of words the walk refuses to read as a name - so they said
 * nothing at all, which is not the same as saying what they are.
 *
 * <p>{@code self}, {@code static} and {@code parent} are the other half of this and they stay
 * silent on purpose. Latte throws the type away - {@code VarTypeNode::print()} returns an empty
 * string, and so does {@code TemplateTypeNode::print()} - so no class encloses a type written in a
 * template and there is nothing for {@code self} to be. {@code {templateType X}} says where the
 * parameters come from, not what class the template is written inside; reading {@code self} as
 * {@code X} would be an invention, and an invented type is how a false report starts.
 *
 * <p>{@code true} is not here because Latte refuses it: its token set has {@code Php_False} and no
 * {@code Php_True}, so a type of {@code true} never reaches the plugin from a template.
 */
public class BuiltInTypeThatIsNotAClassTest extends BasePsiParsingTestCase {

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
    public void testABuiltInTypeIsNamedRatherThanReadAsAClass() {
        Assert.assertEquals("void", typeOf("void"));
        Assert.assertEquals("never", typeOf("never"));
        Assert.assertEquals("false", typeOf("false"));
    }

    /** Named, and therefore not a class: nothing asks the index for {@code \void}. */
    @Test
    public void testNoneOfThemContributesAClass() {
        for (String word : new String[]{"void", "never", "false"}) {
            NettePhpType type = NettePhpType.create(word);
            Assert.assertEquals(word, type.toString());
            Assert.assertFalse(word + " is not a class", type.containsClasses());
            Assert.assertEquals(0, type.findClasses().length);
        }
    }

    /** The words that stand for a class without naming one have no class to stand for. */
    @Test
    public void testTheWordsThatStandForAnEnclosingClassStaySilent() {
        for (String word : new String[]{"self", "static", "parent"}) {
            Assert.assertEquals(word + " has no class to name in a template", "mixed", typeOf(word));
        }
    }

    /** And the types that did work go on working, in every spelling. */
    @Test
    public void testTheTypesThatWorkedAreUnchanged() {
        Assert.assertEquals("string", typeOf("string"));
        Assert.assertEquals("mixed", typeOf("mixed"));
        Assert.assertEquals("null", typeOf("null"));
        Assert.assertEquals("\\App\\Model\\Thing", typeOf("App\\Model\\Thing"));
        Assert.assertEquals("\\Thing", typeOf("Thing"));
    }

    private String typeOf(String writtenType) {
        PsiFile file = createPsiFile("a", "{varType " + writtenType + " $a}\n{$a}");
        StringBuilder found = new StringBuilder();
        file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof LattePhpVariable && found.length() == 0) {
                    found.append(((LattePhpVariable) element).getReturnType().toString());
                }
                super.visitElement(element);
            }
        });
        return found.toString();
    }
}
