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
 * A type has more than one name, and a name the plugin does not know is read as a class.
 *
 * <p>That is how {@code {varType integer $a}} came to mean the class {@code \integer}, and with it
 * {@code list}, {@code resource}, {@code boolean}, {@code double}, {@code scalar}, {@code numeric}
 * and {@code noreturn} - the same hole {@code void} was in before it was given a name of its own.
 * A class nobody can find reports nothing, so none of them was ever noisy; the type shown was
 * simply wrong, and the settings table called it an undefined class.
 *
 * <p>The hyphenated PHPDoc types are the other half. {@code class-string} and {@code positive-int}
 * say something narrower than {@code string} and {@code int}, and the plugin cannot say the
 * narrower thing - but it can say the wider one, which is true and is more than the silence they
 * used to get. A hyphen is not part of a PHP name, so none of them can be confused with a class
 * and the spelling is read whatever its case.
 *
 * <p>The plain words are read only in lower case, and that is the point of the distinction rather
 * than an oversight. {@code Integer}, {@code Resource} and {@code Numeric} are all legal class
 * names; {@code integer} in a type is PHPDoc for {@code int} and nobody spells a class that way.
 * Reading a real class as {@code int} would take the members off it, which is the failure this
 * whole area exists to remove.
 */
public class TypeWrittenUnderAnotherNameTest extends BasePsiParsingTestCase {

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
    public void testAnotherNameForANativeTypeIsThatType() {
        Assert.assertEquals("int", typeOf("integer"));
        Assert.assertEquals("bool", typeOf("boolean"));
        Assert.assertEquals("float", typeOf("double"));
        Assert.assertEquals("array", typeOf("list"));
        Assert.assertEquals("never", typeOf("noreturn"));
    }

    /**
     * A word that is a type of its own names itself, the way {@code void} does, and like every
     * other type of the plugin's own it is matched without regard to case - which is how PHP
     * matches a type name and how {@code String} and {@code Array} have always been read here.
     * That is the whole difference from the names below: {@code integer} is somebody else's word
     * for a type the plugin has, so it is read as written, in the spelling PHPDoc uses.
     */
    @Test
    public void testATypeWithNoClassAndNoWiderNameNamesItself() {
        Assert.assertEquals("resource", typeOf("resource"));
        Assert.assertEquals("resource", typeOf("Resource"));
        Assert.assertFalse(NettePhpType.create("resource").containsClasses());
    }

    @Test
    public void testANameForSeveralTypesIsAllOfThem() {
        assertNamesTypes(typeOf("numeric"), "int", "float");
        assertNamesTypes(typeOf("scalar"), "bool", "float", "int", "string");
    }

    @Test
    public void testAPhpDocTypeIsTheTypeItNarrows() {
        Assert.assertEquals("string", typeOf("class-string"));
        Assert.assertEquals("string", typeOf("non-empty-string"));
        Assert.assertEquals("string", typeOf("numeric-string"));
        Assert.assertEquals("int", typeOf("positive-int"));
        Assert.assertEquals("int", typeOf("int-mask"));
        Assert.assertEquals("array", typeOf("non-empty-array"));
    }

    /** Nullable and array-of are spelled round these names as they are round any other. */
    @Test
    public void testTheSpellingsRoundThemAreReadToo() {
        assertNamesTypes(typeOf("?positive-int"), "int", "null");
        Assert.assertEquals("string[]", typeOf("class-string[]"));
        Assert.assertEquals("int[]", typeOf("integer[]"));
        assertNamesTypes(typeOf("?integer"), "int", "null");
    }

    /**
     * The counterweight, and the reason the plain words are read in lower case only. Each of these
     * is a legal class name and a template that names one means the class.
     */
    @Test
    public void testACapitalisedNameIsStillTheClassItNames() {
        Assert.assertEquals("\\Integer", typeOf("Integer"));
        Assert.assertEquals("\\Numeric", typeOf("Numeric"));
        Assert.assertEquals("\\Scalar", typeOf("Scalar"));
        Assert.assertEquals("\\List", typeOf("List"));
    }

    /** A hyphen cannot be in a class name, so those are read however they are spelled. */
    @Test
    public void testAHyphenatedNameIsReadInAnyCase() {
        Assert.assertEquals("string", typeOf("CLASS-STRING"));
        Assert.assertEquals("int", typeOf("Positive-Int"));
    }

    /**
     * {@code empty} is a language construct rather than a type, so there is nothing to read it as
     * and the plugin goes on saying nothing about it.
     */
    @Test
    public void testAWordThatIsNotATypeAtAllStaysSilent() {
        Assert.assertEquals("mixed", typeOf("empty"));
    }

    private void assertNamesTypes(String actual, String... expected) {
        java.util.List<String> parts = java.util.Arrays.asList(actual.split("\\|"));
        for (String one : expected) {
            Assert.assertTrue("expected " + one + " in " + actual, parts.contains(one));
        }
        Assert.assertEquals("in " + actual, expected.length, parts.size());
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
