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
 * A class named without a namespace is a type like any other.
 *
 * <p>{@code {varType App\Model\Thing $a}} has resolved to that class for as long as the plugin
 * has existed and {@code {varType Thing $a}} resolved to nothing, for the same reason a class
 * named before a {@code ::} was invisible: a name with no backslash never became a class
 * reference. Since the type in a tag is read as a run of tokens, the text is there to read.
 *
 * <p>Only a name is read here, optionally nullable and optionally an array of. The words that
 * look like a name and are not a class - {@code self}, {@code static}, {@code parent} - stay
 * unresolved, because reading them as a class would say a template uses a class called
 * {@code \self}, and then say its methods are missing.
 */
public class BareClassNameAsTypeTest extends BasePsiParsingTestCase {

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
    public void testABareNameIsTheClassItNames() {
        Assert.assertEquals("\\Thing", typeOf("Thing"));
        Assert.assertEquals("\\Thing", typeOf("\\Thing"));
    }

    @Test
    public void testANullableBareNameNamesTheClassAndNull() {
        String type = typeOf("?Thing");
        Assert.assertTrue("expected \\Thing and null in " + type, type.contains("\\Thing") && type.contains("null"));
    }

    @Test
    public void testAnArrayOfABareNameIsAnArrayOfThatClass() {
        Assert.assertEquals("\\Thing[]", typeOf("Thing[]"));
    }

    /** The qualified spelling, which worked before and has to keep working. */
    @Test
    public void testAQualifiedNameIsUnchanged() {
        Assert.assertEquals("\\App\\Model\\Thing", typeOf("App\\Model\\Thing"));
        Assert.assertEquals("string", typeOf("string"));
        Assert.assertEquals("string[]", typeOf("string[]"));
    }

    /**
     * The counterweight. These are one name each and none of them is a class, so reading them as
     * one would invent {@code \self} and report methods missing from it. They name the class a
     * type is written inside and a template is not written inside one, so they say nothing -
     * {@link BuiltInTypeThatIsNotAClassTest} is where that is argued and where {@code never},
     * {@code false} and {@code void} are shown naming themselves instead.
     */
    @Test
    public void testTheWordsThatLookLikeANameAndAreNotAClassStaySilent() {
        for (String word : new String[]{"true", "self", "static", "parent"}) {
            Assert.assertEquals(word + " is not a class", "mixed", typeOf(word));
        }
    }

    /** And the shapes that are more than a name are still nobody's business yet. */
    @Test
    public void testTheShapesThatAreMoreThanANameStaySilent() {
        Assert.assertEquals("mixed", typeOf("A&B"));
        Assert.assertEquals("mixed", typeOf("(A&B)|null"));
        Assert.assertEquals("mixed", typeOf("class-string"));
        Assert.assertEquals("mixed", typeOf("array<int, Thing>"));
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
