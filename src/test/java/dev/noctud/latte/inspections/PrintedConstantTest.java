package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code {PHP_EOL}} prints a constant. Latte reads the content of a tag it does not know as an
 * expression, and a bare identifier in an expression is a constant fetch, so both {@code {FOO}} and
 * {@code {\FOO}} are valid in every version of the supported range (docs/latte/latte-3.1.md).
 *
 * <p>The plugin reported each spelling for a different reason. The unqualified one reached the
 * lexer as a tag name and came out as an unknown tag, which is an error - the worst shape a false
 * report can take. The qualified one is parsed as a class reference, so it was reported as a class
 * that does not exist.
 *
 * <p>Both are silent now, and the cases that make the silence narrow are here with them: a
 * lower-case name is still an unknown tag, and a class name is still reported everywhere PHP
 * requires one - after {@code new}, after {@code instanceof}, in front of {@code ::}, and in a
 * declared type.
 */
public class PrintedConstantTest extends BasePlatformTestCase {

    private static final String ARTICLE_PHP =
        "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace App\\Model;\n"
            + "\n"
            + "final class Article\n"
            + "{\n"
            + "    public const STATUS = 'published';\n"
            + "}\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("app/Model/Article.php", ARTICLE_PHP);
        myFixture.enableInspections(new ClassUsagesInspection());
    }

    public void testAnUnqualifiedConstantIsNotAnUnknownTag() {
        assertEquals(List.of(), problemsIn("{PHP_EOL}\n"));
        assertEquals(List.of(), problemsIn("{MY_OWN_CONSTANT}\n"));
    }

    public void testAQualifiedConstantIsNotAClassName() {
        assertEquals(List.of(), problemsIn("{\\PHP_EOL}\n"));
    }

    public void testANamespacedNameStandingAloneIsNotAClassNameEither() {
        assertEquals(List.of(), problemsIn("{App\\Model\\NO_SUCH_CONSTANT}\n"));
    }

    public void testALowerCaseNameIsStillAnUnknownTag() {
        assertEquals(List.of("ERROR:Unknown tag {noSuchTag}"), problemsIn("{noSuchTag}\n"));
    }

    public void testAClassIsStillReportedInFrontOfADoubleColon() {
        assertEquals(
            List.of("WARNING:Undefined class '\\App\\Model\\NoSuchClass'"),
            problemsIn("{App\\Model\\NoSuchClass::STATUS}\n")
        );
        assertEquals(List.of(), problemsIn("{App\\Model\\Article::STATUS}\n"));
    }

    public void testAClassIsStillReportedAfterNew() {
        assertEquals(
            List.of("WARNING:Undefined class '\\App\\Model\\NoSuchClass'"),
            problemsIn("{var $x = new App\\Model\\NoSuchClass}\n")
        );
    }

    public void testAClassIsStillReportedAfterInstanceof() {
        assertEquals(
            List.of("WARNING:Undefined class '\\App\\Model\\NoSuchClass'"),
            problemsIn("{if $x instanceof App\\Model\\NoSuchClass}x{/if}\n")
        );
    }

    public void testAClassIsStillReportedInADeclaredType() {
        assertEquals(
            List.of("WARNING:Undefined class '\\App\\Model\\NoSuchClass'"),
            problemsIn("{varType App\\Model\\NoSuchClass $x}\n")
        );
        assertEquals(
            List.of("WARNING:Undefined class '\\App\\Model\\NoSuchClass'"),
            problemsIn("{templateType App\\Model\\NoSuchClass}\n")
        );
    }

    private List<String> problemsIn(String template) {
        myFixture.configureByText("printed-constant.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getSeverity().getName() + ":" + info.getDescription());
            }
        }
        return problems;
    }
}
