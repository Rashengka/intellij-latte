package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * A constant printed by a tag, and a bare name that only looks like one.
 *
 * <p>{@code {\PHP_EOL}} prints a constant in every version of the supported range. {@code {PHP_EOL}}
 * does not: a bare name standing as the whole tag is read as the tag's name, so Latte 2.11.7 refuses
 * it as "Unknown tag {PHP_EOL}" and 3.1.6 as "Unexpected tag {PHP_EOL}" - measured on both. The plugin
 * used to keep quiet about it on the strength of the opposite claim, which nobody had measured.
 *
 * <p>The qualified spelling is parsed as a class reference and was once reported as a class that
 * does not exist; it is silent, and the cases that keep that silence narrow are here with it: a class
 * name is still reported everywhere PHP requires one - after {@code new}, after {@code instanceof},
 * in front of {@code ::}, and in a declared type.
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

    public void testAnUnqualifiedConstantIsAnUnknownTag() {
        assertEquals(List.of("ERROR:Unknown tag {PHP_EOL}"), problemsIn("{PHP_EOL}\n"));
        assertEquals(List.of("ERROR:Unknown tag {MY_OWN_CONSTANT}"), problemsIn("{MY_OWN_CONSTANT}\n"));
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
