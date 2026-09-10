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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A type may name more than one thing, and until now only the grammar's spellings of that were
 * read.
 *
 * <p>{@code string|null} worked because the grammar reads it. {@code Foo|null} did not, and
 * neither did {@code Foo|Bar} or {@code Foo|string}, for the reason a bare {@code Foo} did not
 * until recently: the text kept for the grammar to fail on was matched against one name and a
 * union is more than one. The intersections {@code A&B} and {@code (A&B)|null} were never read at
 * all.
 *
 * <p>All of them are read as the plugin's several-classes type, the union included and the
 * intersection with it. Every inspection that looks a name up on a type is satisfied when any one
 * of the classes has it, which is what an intersection needs: a value that is both an {@code A}
 * and a {@code B} has the members of both, and reading only the first name would report the
 * second one's members as missing - a false report, and the kind this plugin exists to remove.
 *
 * <p>The price is that the plugin cannot then tell {@code A|B} from {@code A&B}. That is the
 * lenient direction: it can fail to report something that is genuinely absent from one arm of a
 * union, and it cannot invent an absence. Brackets are dropped for the same reason - they group an
 * intersection inside a union, and once both are read the same way the grouping says nothing.
 */
public class SeveralNamesInOneTypeTest extends BasePsiParsingTestCase {

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
    public void testAUnionOfNamesIsAllOfThem() {
        assertNames(typeOf("Foo|Bar"), "\\Foo", "\\Bar");
        assertNames(typeOf("Foo|null"), "\\Foo", "null");
        assertNames(typeOf("null|Foo"), "\\Foo", "null");
        assertNames(typeOf("Foo|string"), "\\Foo", "string");
        assertNames(typeOf("App\\Model\\Thing|Foo"), "\\App\\Model\\Thing", "\\Foo");
    }

    @Test
    public void testAnIntersectionIsAllOfThemToo() {
        assertNames(typeOf("A&B"), "\\A", "\\B");
        assertNames(typeOf("A&B&C"), "\\A", "\\B", "\\C");
        assertNames(typeOf("(A&B)"), "\\A", "\\B");
        assertNames(typeOf("(A&B)|null"), "\\A", "\\B", "null");
        assertNames(typeOf("?A&B"), "\\A", "\\B", "null");
    }

    /** Arrays and the other names for a type are read inside a union as they are outside one. */
    @Test
    public void testTheOtherSpellingsGoOnWorkingInside() {
        assertNames(typeOf("Foo[]|null"), "\\Foo[]", "null");
        assertNames(typeOf("class-string|null"), "string", "null");
        assertNames(typeOf("Foo|Bar[]"), "\\Foo", "\\Bar[]");
    }

    /**
     * A name with no meaning anywhere in it takes the whole type down with it. Dropping the part
     * and keeping the rest would answer a question that was not asked - {@code self|null} is not
     * {@code null}.
     */
    @Test
    public void testANameThatMeansNothingSilencesTheWholeType() {
        Assert.assertEquals("mixed", typeOf("self|null"));
        Assert.assertEquals("mixed", typeOf("Foo&self"));
        Assert.assertEquals("mixed", typeOf("Foo|a-b"));
    }

    /**
     * And a shape that is still not read stays where it was. A generic is read now - see
     * {@link GenericElementTypeTest} - so what is left is the array shape, which names keys
     * rather than a type.
     */
    @Test
    public void testWhatIsStillNotReadIsStillSilent() {
        Assert.assertEquals("mixed", typeOf("array{a: int}"));
        Assert.assertEquals("mixed", typeOf("array{a: int, b: string}"));
    }

    /** The one name case, unchanged by any of this. */
    @Test
    public void testOneNameIsStillOneName() {
        Assert.assertEquals("\\Foo", typeOf("Foo"));
        Assert.assertEquals("string", typeOf("string"));
        assertNames(typeOf("?Foo"), "\\Foo", "null");
        Assert.assertEquals("\\Foo[]", typeOf("Foo[]"));
    }

    private void assertNames(String actual, String... expected) {
        Set<String> parts = new HashSet<>(Arrays.asList(actual.split("\\|")));
        Assert.assertEquals(actual, new HashSet<>(Arrays.asList(expected)), parts);
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
