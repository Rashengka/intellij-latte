package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A plugin that has gone quiet looks healthy, which is why the playground carries a template of
 * deliberate mistakes: every line in it has to be reported. Nothing ran it, though - it was there to
 * be looked at by hand - so silence would have been noticed only by whoever happened to open the
 * file. This runs it.
 *
 * <p>The template is read from the playground rather than copied here, so the two cannot drift: a
 * mistake added there without an expectation added below fails this test, which is the point.
 *
 * <p>{@link #testTheSameLinesWrittenCorrectlyAreNotReported()} is what makes the list above mean
 * something. Without it the whole class would still pass with the plugin reporting everything.
 */
public class ExpectedErrorsTest extends BasePlatformTestCase {

    private static final String TEMPLATE = "sandbox/playground/templates/errors-expected.latte";

    /**
     * The class the template declares a type against. Written here rather than read from the
     * playground because the test needs one class and not a project layout - but it has to carry a
     * method, so that "method not found" means the name and not the type is unknown.
     */
    private static final String ARTICLE_PHP =
        "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace App\\Model;\n"
            + "\n"
            + "final class Article\n"
            + "{\n"
            + "    public function getTitle(): string\n"
            + "    {\n"
            + "        return 'title';\n"
            + "    }\n"
            + "}\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("app/Model/Article.php", ARTICLE_PHP);
        myFixture.enableInspections(
            new ModifierNotAllowedInspection(),
            new ModifierDefinitionInspection(),
            new DeprecatedTagInspection(),
            new VariablesInspection(),
            new ClassUsagesInspection(),
            new MethodUsagesInspection(),
            new StaticPropertyUsagesInspection(),
            new ConstantUsagesInspection(),
            new PropertyUsagesInspection(),
            new MacroTemplateTypeInspection(),
            new MacroVarTypeInspection(),
            new MacroVarInspection(),
            new LatteIterableTypeInspection(),
            new MissingFileInspection()
        );
    }

    public void testEveryDeliberateMistakeInThePlaygroundIsReported() throws Exception {
        File template = new File(TEMPLATE);
        assertTrue("Missing " + template.getAbsolutePath(), template.isFile());

        assertEquals(
            List.of(
                "WARNING:Method 'noSuchMethod' not found for type '\\App\\Model\\Article'",
                "WARNING:Property 'noSuchProperty' not found for type '\\App\\Model\\Article'",
                "WARNING:Undefined variable 'undefinedVariable'",
                "WARNING:Undefined latte filter 'noSuchFilter'",
                "ERROR:Unknown tag {noSuchTag}",
                "ERROR:Invalid syntax mode 'nonsense'. Expected: off, double, single, or latte"
            ),
            problemsIn(Files.readString(template.toPath(), StandardCharsets.UTF_8))
        );
    }

    /**
     * The same six lines with the names spelled the way the class and Latte define them. A plugin
     * that painted the template above red for some other reason would light this one up too.
     */
    public void testTheSameLinesWrittenCorrectlyAreNotReported() {
        assertEquals(List.of(), problemsIn(
            "{varType App\\Model\\Article $article}\n"
                + "{var $definedVariable = 1}\n"
                + "\n"
                + "{$article->getTitle()}\n"
                + "{$definedVariable}\n"
                + "{$article->getTitle()|upper}\n"
                + "{if true}yes{/if}\n"
                + "{syntax off}\n"
        ));
    }

    private List<String> problemsIn(String template) {
        myFixture.configureByText("errors-expected.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            problems.add(info.getSeverity().getName() + ":" + info.getDescription());
        }
        return problems;
    }
}
