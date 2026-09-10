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

import java.util.ArrayList;
import java.util.List;

/**
 * {@code array<int, Foo>} says the same thing as {@code Foo[]} and was answered as {@code mixed}.
 *
 * <p>Everything downstream of the element type already worked for the bracket spelling: a
 * {@code {foreach}} names its value, an index names what it indexes, a nested array gives an array
 * one level shallower, and each of those is the depth the type already carries. The generic
 * spelling never reached any of it, so a template that wrote the one Latte and PHPDoc prefer got
 * nothing while the same template writing brackets got everything.
 *
 * <p>The container is not a class. {@code array}, {@code list} and {@code iterable} name types the
 * plugin has, and reading them as classes invented {@code \array} and {@code \list} - the same
 * defect that {@code integer} and {@code list} had before they were given their names. A backslash
 * belongs to a class name and to nothing else.
 *
 * <p>A container that <em>is</em> a class keeps it. {@code Collection<Foo>} is a
 * {@code \Collection} whose members have to go on resolving, and an array of {@code \Foo} one
 * level down - which is what the depth already means.
 */
public class GenericElementTypeTest extends BasePsiParsingTestCase {

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
    public void testABuiltInContainerIsTheArrayItDescribes() {
        Assert.assertEquals("\\Foo[]", typeOf("array<Foo>"));
        Assert.assertEquals("\\Foo[]", typeOf("array<int, Foo>"));
        Assert.assertEquals("\\Foo[]", typeOf("list<Foo>"));
        Assert.assertEquals("\\Foo[]", typeOf("iterable<Foo>"));
        Assert.assertEquals("\\Foo[]", typeOf("non-empty-list<Foo>"));
        Assert.assertEquals("\\Foo[]", typeOf("non-empty-array<string, Foo>"));
    }

    /** The key is dropped rather than guessed at; carrying one is separate work. */
    @Test
    public void testTheValueIsTakenAndTheKeyIsNotInvented() {
        Assert.assertEquals("string[]", typeOf("array<int, string>"));
        Assert.assertEquals("mixed[]", typeOf("array<string, mixed>"));
    }

    /** Written with spaces round the brackets, which four of the ten in the corpus are. */
    @Test
    public void testTheSpacedSpellingIsTheSameType() {
        Assert.assertEquals("\\Foo[]", typeOf("array < int , Foo >"));
        Assert.assertEquals("\\App\\Model\\Thing[]", typeOf("array < int , App\\Model\\Thing >"));
    }

    /** A container that is a class keeps the class, and the element sits one level under it. */
    @Test
    public void testAClassContainerKeepsItsClass() {
        assertNames(typeOf("Collection<Foo>"), "\\Collection", "\\Foo[]");
        assertNames(typeOf("Generator<Foo>"), "\\Generator", "\\Foo[]");
    }

    /** An array of arrays is an array of arrays, at every level. */
    @Test
    public void testANestedGenericIsReadAllTheWayDown() {
        Assert.assertEquals("\\Foo[][]", typeOf("array<int, array<string, Foo>>"));
        Assert.assertEquals("mixed[][]", typeOf("array<int, array<string, mixed>>"));
        Assert.assertEquals("\\Foo[][][]", typeOf("array<array<array<Foo>>>"));
    }

    @Test
    public void testAGenericSpelledInAUnionIsReadToo() {
        assertNames(typeOf("array<int, Foo>|null"), "\\Foo[]", "null");
        assertNames(typeOf("array<int, array<string, mixed>>|null"), "mixed[][]", "null");
    }

    /** The whole point: what the element type feeds, and it feeds it already. */
    @Test
    public void testWhatTheElementTypeIsFor() {
        assertEquals("\\Foo", variableIn("{varType array<int, Foo> $a}\n{foreach $a as $v}{$v}{/foreach}", "v"));
        assertEquals("\\Foo", variableIn("{varType array<int, Foo> $a}\n{var $x = $a[0]}\n{$x}", "x"));
        assertEquals("\\Foo[]", variableIn(
            "{varType array<int, array<string, Foo>> $a}\n{foreach $a as $row}{$row}{/foreach}", "row"));
        assertEquals("\\Foo", variableIn(
            "{varType array<int, array<string, Foo>> $a}\n"
                + "{foreach $a as $row}{foreach $row as $cell}{$cell}{/foreach}{/foreach}", "cell"));
    }

    /**
     * The counterweight. A generic is brackets round a name; a shape and a range are not, and the
     * plugin goes on saying nothing about the first and reading the second as the type it narrows.
     */
    @Test
    public void testWhatIsNotAContainerIsNotReadAsOne() {
        Assert.assertEquals("mixed", typeOf("array{a: int, b: string}"));
        Assert.assertEquals("int", typeOf("int<0, 100>"));
        Assert.assertEquals("mixed", typeOf("array<"));
        Assert.assertEquals("mixed", typeOf("array<>"));
    }

    /** And nothing that worked before moves. */
    @Test
    public void testTheBracketSpellingIsUnchanged() {
        Assert.assertEquals("\\Foo[]", typeOf("Foo[]"));
        Assert.assertEquals("\\Foo[][]", typeOf("Foo[][]"));
        Assert.assertEquals("string[]", typeOf("string[]"));
        Assert.assertEquals("array", typeOf("array"));
        Assert.assertEquals("\\Foo", typeOf("Foo"));
    }

    private void assertNames(String actual, String... expected) {
        java.util.Set<String> parts = new java.util.HashSet<>(java.util.Arrays.asList(actual.split("\\|")));
        Assert.assertEquals(actual, new java.util.HashSet<>(java.util.Arrays.asList(expected)), parts);
    }

    private String typeOf(String writtenType) {
        return variableIn("{varType " + writtenType + " $a}\n{$a}", "a");
    }

    private String variableIn(String template, String name) {
        PsiFile file = createPsiFile("a" + Math.abs(template.hashCode()), template);
        List<String> found = new ArrayList<>();
        file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof LattePhpVariable
                    && name.equals(((LattePhpVariable) element).getVariableName())) {
                    found.add(((LattePhpVariable) element).getReturnType().toString());
                }
                super.visitElement(element);
            }
        });
        return found.isEmpty() ? "(not found)" : found.get(found.size() - 1);
    }
}
