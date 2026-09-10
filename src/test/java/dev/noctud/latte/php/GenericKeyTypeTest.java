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
 * What a {@code {foreach}} calls its key, when the type wrote one down.
 *
 * <p>{@code array<string, Foo>} says two things and only one of them was read. The value became
 * the element type and the key was dropped on the floor, so {@code {foreach $a as $k => $v}} named
 * {@code $v} and said {@code mixed} about {@code $k} - which is also what it said for
 * {@code Foo[]}, where nobody ever wrote a key down at all. The two cases looked identical and
 * only one of them was ignorance.
 *
 * <p>A key is only ever answered where one was written. {@code Foo[]} and {@code array<Foo>} name
 * no key, so they go on saying {@code mixed} rather than guessing at {@code int} - PHP would use
 * an int there, but a type that does not say so is a type that does not say so.
 *
 * <p>Nesting keeps them apart: in {@code array<int, array<string, Foo>>} the outer key is an int
 * and the inner one a string, and each {@code {foreach}} gets its own.
 */
public class GenericKeyTypeTest extends BasePsiParsingTestCase {

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
    public void testTheKeyIsTheOneTheTypeNames() {
        Assert.assertEquals("int", keyOf("array<int, Foo>"));
        Assert.assertEquals("string", keyOf("array<string, Foo>"));
        Assert.assertEquals("string", keyOf("non-empty-array<string, Foo>"));
    }

    @Test
    public void testAKeyThatNamesAClassIsThatClass() {
        Assert.assertEquals("\\Foo", keyOf("array<Foo, Bar>"));
    }

    /** A type that names no key says nothing about it, rather than assuming the int PHP would use. */
    @Test
    public void testAKeyNobodyWroteDownIsNotInvented() {
        Assert.assertEquals("mixed", keyOf("Foo[]"));
        Assert.assertEquals("mixed", keyOf("array<Foo>"));
        Assert.assertEquals("mixed", keyOf("array"));
        Assert.assertEquals("mixed", keyOf("list<Foo>"));
    }

    /** Each level keeps its own key, which is the whole reason the nesting is read. */
    @Test
    public void testEachLevelHasItsOwnKey() {
        String template = "{varType array<int, array<string, Foo>> $a}\n"
            + "{foreach $a as $outerKey => $row}{foreach $row as $innerKey => $cell}"
            + "{$outerKey}{$innerKey}{$cell}{/foreach}{/foreach}";
        Assert.assertEquals("int", typeIn(template, "outerKey"));
        Assert.assertEquals("string", typeIn(template, "innerKey"));
        Assert.assertEquals("\\Foo", typeIn(template, "cell"));
        Assert.assertEquals("\\Foo[]", typeIn(template, "row"));
    }

    /** The counterweight: naming the key must not cost the value. */
    @Test
    public void testTheValueIsStillTheValue() {
        String template = "{varType array<string, Foo> $a}\n{foreach $a as $k => $v}{$k}{$v}{/foreach}";
        Assert.assertEquals("string", typeIn(template, "k"));
        Assert.assertEquals("\\Foo", typeIn(template, "v"));
        Assert.assertEquals("\\Foo[]", typeIn(template, "a"));
    }

    /** A union says nothing about a key unless its arms agree about one. */
    @Test
    public void testAUnionThatDisagreesAboutTheKeySaysNothing() {
        Assert.assertEquals("int", keyOf("array<int, Foo>|null"));
        Assert.assertEquals("mixed", keyOf("array<int, Foo>|array<string, Bar>"));
    }

    private String keyOf(String writtenType) {
        return typeIn("{varType " + writtenType + " $a}\n{foreach $a as $k => $v}{$k}{$v}{/foreach}", "k");
    }

    private String typeIn(String template, String name) {
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
