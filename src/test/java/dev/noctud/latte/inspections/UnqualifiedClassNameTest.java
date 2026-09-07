package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * A class named without a namespace is the same class, so it is reported the same way.
 *
 * <p>It was not. {@code App\Model\NoSuchClass::make()} said the class does not exist and
 * {@code NoSuchClass::make()} said nothing at all, and the difference was in the parser rather
 * than in the inspection: {@code phpClassUsage} began at a backslash, so an unqualified name never
 * became a class reference and there was no element for anything to look at. The inspection had
 * known all four places PHP requires a class since {@code isClassPosition} was written.
 *
 * <p>Every case here is therefore written as a pair - the unqualified spelling next to the
 * qualified one - because the fix is a symmetry and not a new report. Whatever the qualified
 * spelling has been doing since it shipped is what the unqualified one has to do now.
 *
 * <p>Three positions are covered: in front of {@code ::}, after {@code new} and after
 * {@code instanceof}. A written-out type ({@code {varType Foo $x}}) is not, and that is deliberate
 * - see {@code .ai/plans/20-neznama-trida-ve-vsech-pozicich.md}.
 */
public class UnqualifiedClassNameTest extends BasePlatformTestCase {

    private static final String GLOBAL_PHP =
        "<?php\n"
            + "\n"
            + "function strtoupper(string $string): string {}\n"
            + "\n"
            + "class Known\n"
            + "{\n"
            + "    public const SIZE = 1;\n"
            + "    public static int $count = 0;\n"
            + "    public static function make(): self {}\n"
            + "}\n";

    private static final String ARTICLE_PHP =
        "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace App\\Model;\n"
            + "\n"
            + "final class Article\n"
            + "{\n"
            + "    public const SIZE = 1;\n"
            + "    public static int $count = 0;\n"
            + "    public static function make(): self {}\n"
            + "}\n";

    private static final String UNDEFINED = "WARNING:Undefined class '\\NoSuchClass'";
    private static final String UNDEFINED_QUALIFIED = "WARNING:Undefined class '\\App\\Model\\NoSuchClass'";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("stubs/global.php", GLOBAL_PHP);
        myFixture.addFileToProject("app/Model/Article.php", ARTICLE_PHP);
        myFixture.enableInspections(
            new MethodUsagesInspection(),
            new ClassUsagesInspection(),
            new ConstantUsagesInspection(),
            new PropertyUsagesInspection(),
            new StaticPropertyUsagesInspection()
        );
    }

    public void testAStaticCallNamesAClass() {
        assertEquals(List.of(UNDEFINED), problemsIn("{do $a = NoSuchClass::make()}\n"));
        assertEquals(List.of(UNDEFINED_QUALIFIED), problemsIn("{do $a = App\\Model\\NoSuchClass::make()}\n"));
    }

    public void testAClassConstantNamesAClass() {
        assertEquals(List.of(UNDEFINED), problemsIn("{do $a = NoSuchClass::SIZE}\n"));
        assertEquals(List.of(UNDEFINED_QUALIFIED), problemsIn("{do $a = App\\Model\\NoSuchClass::SIZE}\n"));
    }

    public void testAStaticPropertyNamesAClass() {
        assertEquals(List.of(UNDEFINED), problemsIn("{do $a = NoSuchClass::$count}\n"));
        assertEquals(List.of(UNDEFINED_QUALIFIED), problemsIn("{do $a = App\\Model\\NoSuchClass::$count}\n"));
    }

    public void testNewNamesAClassWithoutBracketsToo() {
        assertEquals(List.of(UNDEFINED), problemsIn("{var $x = new NoSuchClass}\n"));
        assertEquals(List.of(UNDEFINED_QUALIFIED), problemsIn("{var $x = new App\\Model\\NoSuchClass}\n"));
    }

    /** With brackets it is a method element, and it has been reported through that since earlier. */
    public void testNewWithBracketsKeepsReporting() {
        assertEquals(List.of(UNDEFINED), problemsIn("{var $x = new NoSuchClass('a')}\n"));
    }

    public void testInstanceofNamesAClass() {
        assertEquals(List.of(UNDEFINED), problemsIn("{if $x instanceof NoSuchClass}x{/if}\n"));
        assertEquals(List.of(UNDEFINED_QUALIFIED), problemsIn("{if $x instanceof App\\Model\\NoSuchClass}x{/if}\n"));
    }

    /** A name inside a call is a name. The walk reaches it since {@code ac333de}. */
    public void testAClassNamedInsideACallIsReportedToo() {
        assertEquals(List.of(UNDEFINED), problemsIn("{do $a = strtoupper(NoSuchClass::make())}\n"));
    }

    /** One report about one thing: the type is unresolved, so nothing is said about the method. */
    public void testAnUnknownClassIsReportedOnceAndNotAlsoAsAMissingMethod() {
        assertEquals(List.of(UNDEFINED), problemsIn("{do $a = NoSuchClass::noSuchMethod()}\n"));
    }

    public void testAKnownClassIsQuietInEveryOneOfThosePlaces() {
        assertEquals(List.of(), problemsIn("{do $a = Known::make()}\n"));
        assertEquals(List.of(), problemsIn("{do $a = Known::SIZE}\n"));
        assertEquals(List.of(), problemsIn("{do $a = Known::$count}\n"));
        assertEquals(List.of(), problemsIn("{var $x = new Known}\n"));
        assertEquals(List.of(), problemsIn("{if $x instanceof Known}x{/if}\n"));
    }

    /**
     * The other half of resolving the name: once the type in front of {@code ::} is known, what is
     * asked of it is checked. The qualified spelling has reported this all along.
     */
    public void testAMissingMemberOfAKnownClassIsReported() {
        assertEquals(
            List.of("WARNING:Method 'noSuch' not found for type '\\Known'"),
            problemsIn("{do $a = Known::noSuch()}\n")
        );
        assertEquals(
            List.of("WARNING:Method 'noSuch' not found for type '\\App\\Model\\Article'"),
            problemsIn("{do $a = App\\Model\\Article::noSuch()}\n")
        );
    }

    /** {@code self}, {@code static} and {@code parent} are ordinary identifiers to the lexer. */
    public void testTheThreeKeywordsThatLookLikeClassNamesAreNotClasses() {
        assertEquals(List.of(), problemsIn("{do $a = self::make()}\n"));
        assertEquals(List.of(), problemsIn("{do $a = static::make()}\n"));
        assertEquals(List.of(), problemsIn("{do $a = parent::make()}\n"));
    }

    /**
     * The false report {@code isClassPosition} was written for. A bare name standing in an
     * expression is a constant fetch, and it has to stay silent while the name in front of a
     * {@code ::} starts being reported - which is why both are asserted here rather than one at a
     * time.
     */
    public void testABareNameStandingAloneIsStillNotAClass() {
        assertEquals(List.of(), problemsIn("{PHP_EOL}\n"));
        assertEquals(List.of(), problemsIn("{MY_OWN_CONSTANT}\n"));
        assertEquals(List.of(), problemsIn("{do $a = PHP_EOL}\n"));
        assertEquals(List.of(), problemsIn("{\\PHP_EOL}\n"));
    }

    /**
     * {@code and} is not in the lexer's keyword list, so it arrives as an identifier followed by a
     * variable - the shape a written-out type has. It is here because that shape was the obvious
     * signal for the type position and this is what disproves it.
     */
    public void testALowerCaseWordFollowedByAVariableIsNotAClass() {
        assertEquals(List.of(), problemsIn("{if $a and $b}x{/if}\n"));
        assertEquals(List.of(), problemsIn("{if $a xor $b}x{/if}\n"));
    }

    /** Both spellings say nothing here today, and the change must not move only one of them. */
    public void testClassConstantSyntaxIsUnchanged() {
        assertEquals(List.of(), problemsIn("{do $a = NoSuchClass::class}\n"));
        assertEquals(List.of(), problemsIn("{do $a = App\\Model\\NoSuchClass::class}\n"));
    }

    private List<String> problemsIn(String template) {
        myFixture.configureByText("unqualified-class.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getSeverity().getName() + ":" + info.getDescription());
            }
        }
        return problems;
    }
}
