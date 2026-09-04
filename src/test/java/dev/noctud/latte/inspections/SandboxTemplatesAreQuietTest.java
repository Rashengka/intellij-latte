package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import dev.noctud.latte.settings.LatteSettings;
import org.jdom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every playground template except one claims, in its own header, that nothing in it may be
 * underlined. Until this test existed nothing checked that claim: the templates were there to be
 * looked at by hand, so a false report in one of them was found only by whoever happened to open
 * the file - which is how an {@code Unused variable} report survived in the playground.
 *
 * <p>The list of templates is read from the directory, not written here, so a template added
 * without a test is not possible: a new file is checked the moment it is committed.
 *
 * <p>{@link ExpectedErrorsTest} is the other half of the same claim. It runs the one template that
 * makes the opposite promise and asserts that all six of its mistakes are still reported, so
 * "quiet" cannot be reached by a plugin that reports nothing at all.
 */
public class SandboxTemplatesAreQuietTest extends BasePlatformTestCase {

    private static final Path PLAYGROUND = Paths.get("sandbox/playground");
    private static final Path TEMPLATES = PLAYGROUND.resolve("templates");
    private static final Path APP = PLAYGROUND.resolve("app");

    /**
     * The settings the build writes into the playground before the sandbox IDE starts. The
     * templates are opened with them in place, so they are what "quiet in the playground" means -
     * without them {@code custom-definitions.latte} would be red for the right reason.
     */
    private static final String SETTINGS = "data/settings/PlaygroundLatteSettings.xml";

    /**
     * Templates exempt from the silence claim, each with the reason it makes a different one. Named
     * here rather than recognised by a naming convention, so that exempting a template is a visible
     * edit to this file and not a side effect of what it was called.
     *
     * <ul>
     *   <li>{@code errors-expected.latte} - deliberate mistakes; every line in it must be
     *       reported. {@link ExpectedErrorsTest} asserts exactly that, name by name.
     * </ul>
     */
    private static final Set<String> NOT_QUIET_BY_DESIGN = Set.of("errors-expected.latte");

    /**
     * What the plugin reports today on templates that are correct Latte, keyed by template and
     * written as {@code SEVERITY: description at 'text'}. Every entry is a false positive, kept
     * here - rather than by taking the construct out of the template - so the gap stays visible and
     * so a new one cannot hide among them. The templates are written the way they should be; this
     * list is the part that is wrong.
     *
     * <p>The list fails in both directions. A report that is not here fails the test as a
     * regression, and an entry that stops reproducing fails it too, so a fix cannot leave a stale
     * entry behind. Each is recorded with its cause and its exit condition in
     * {@code .ai/plans/13-falesne-hlasky-ze-sandboxu.md}.
     */
    private static final Map<String, List<String>> KNOWN_FALSE_POSITIVES = Map.of(
        "links-and-components.latte", List.of(
            // nette/application 3.2.7 added {linkBase}; the registry never got it.
            "ERROR: Unknown tag {linkBase} at '{linkBase 'https://example.com'}'"
        ),
        // Three separate ones in the same file:
        //   - {PHP_EOL} prints a constant and is valid in every version in the supported range
        //     (docs/latte/latte-3.1.md: "both {FOO} and {\FOO} are valid"). It is reported as an
        //     unknown tag, and as an error rather than a warning;
        //   - {\PHP_EOL}, the qualified spelling of the same thing, is read as a class name;
        //   - a constructor call is looked up among functions, so `new DateTimeImmutable(...)`
        //     is reported as a function that does not exist even with the class in the index.
        "expressions-and-arrays.latte", List.of(
            "ERROR: Unknown tag {PHP_EOL} at '{PHP_EOL}'",
            "WARNING: Undefined class '\\PHP_EOL' at '\\PHP_EOL'",
            "WARNING: Function 'DateTimeImmutable' not found at 'DateTimeImmutable'"
        ),
        // |batch takes one argument in a template - the array is what it is applied to - but it is
        // registered with the array in its parameter list, so the second one is demanded as well.
        "filters.latte", List.of(
            "WARNING: Missing required filter parameters (2 required) at 'batch:2'"
        ),
        // Tags and functions the packages really register, missing from the plugin's registry.
        // Every one of them is an error on a template that is correct for the package version
        // that introduced it, which is the worst shape a false report can take.
        "forms.latte", List.of(
            "ERROR: Unknown tag {formContext} at '{formContext articleForm}'",
            "ERROR: Unknown tag {formClassPrint} at '{formClassPrint articleForm}'"
        ),
        "latte-3-only.latte", List.of(
            "ERROR: Unknown attribute tag n:asset? at 'n:asset?'",
            "ERROR: Unknown tag {preload} at '{preload 'images/logo.png'}'",
            "WARNING: Function 'asset' not found at 'asset'",
            "WARNING: Function 'tryAsset' not found at 'tryAsset'"
        )
    );

    public void testEveryPlaygroundTemplateThatPromisesSilenceIsSilent() throws Exception {
        applyPlaygroundSettings();
        addPlaygroundSources();
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
            new LatteIterableTypeInspection()
            // MissingFileInspection is deliberately absent. It resolves an {include} through
            // VirtualFileManager with a hardcoded file:// URL, and the light fixture's files live
            // in a temp:// filesystem, so every template that includes another one would be
            // reported as including a missing file. In the sandbox IDE, which opens a local
            // directory, the same templates resolve. Recorded as a finding with the rest.
        );

        List<Path> quiet = quietTemplates();
        assertFalse("No playground templates were found in " + TEMPLATES.toAbsolutePath(), quiet.isEmpty());

        Map<String, List<String>> reports = new LinkedHashMap<>();
        Map<String, List<String>> located = new LinkedHashMap<>();
        for (Path template : quiet) {
            String name = TEMPLATES.relativize(template).toString();
            List<String> problems = problemsIn(template);
            if (!problems.isEmpty()) {
                reports.put(name, stripPositions(problems));
                located.put(name, problems);
            }
        }

        assertEquals(describe(located), KNOWN_FALSE_POSITIVES, reports);
    }

    /**
     * The exemption list is only meaningful while the file it names is there. A renamed or deleted
     * template would otherwise leave a dead entry behind, and the next template to go noisy could
     * be exempted by inheriting the name.
     */
    public void testEveryExemptedTemplateExists() {
        for (String name : NOT_QUIET_BY_DESIGN) {
            Path template = TEMPLATES.resolve(name);
            assertTrue("Exempted template is missing: " + template.toAbsolutePath(), Files.isRegularFile(template));
        }
    }

    private List<String> problemsIn(Path template) {
        VirtualFile file = myFixture.findFileInTempDir("templates/" + template.getFileName());
        assertNotNull("Template was not copied into the fixture project: " + template, file);
        myFixture.configureFromExistingVirtualFile(file);

        PsiFile psiFile = myFixture.getFile();
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            // Below a weak warning nothing is a complaint: that band carries the editor's own
            // annotations, such as the "Open in browser" marker on a URL in an attribute.
            if (info.getDescription() == null || info.getSeverity().compareTo(HighlightSeverity.WEAK_WARNING) < 0) {
                continue;
            }
            problems.add(position(psiFile.getText(), info.getStartOffset())
                + " " + info.getSeverity().getName()
                + ": " + info.getDescription()
                + " at '" + info.getText() + "'");
        }
        return problems;
    }

    /**
     * Drops the {@code line:column} prefix the failure message carries. What is compared against
     * the known list has to survive an edit further up the template, or the list would have to be
     * renumbered every time a line is added above one of its entries.
     */
    private static List<String> stripPositions(List<String> problems) {
        List<String> stripped = new ArrayList<>();
        for (String problem : problems) {
            stripped.add(problem.substring(problem.indexOf(' ') + 1));
        }
        return stripped;
    }

    private List<Path> quietTemplates() throws IOException {
        assertTrue("Missing " + TEMPLATES.toAbsolutePath(), Files.isDirectory(TEMPLATES));
        List<Path> templates = new ArrayList<>();
        try (Stream<Path> walk = Files.list(TEMPLATES)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".latte"))
                .filter(path -> !NOT_QUIET_BY_DESIGN.contains(path.getFileName().toString()))
                .sorted()
                .forEach(templates::add);
        }
        return templates;
    }

    /**
     * The whole playground goes into the fixture project, not just the template under test: the
     * templates include and extend each other, and the PHP under {@code app/} is what the type
     * declarations in them point at. A template checked on its own would go red for want of the
     * rest of the project rather than for anything the plugin got wrong.
     */
    private void addPlaygroundSources() throws IOException {
        addTree(TEMPLATES, "templates", ".latte");
        addTree(APP, "app", ".php");
        myFixture.addFileToProject("stubs/php-standard-library.php", PHP_STANDARD_LIBRARY);
    }

    /**
     * The part of the PHP standard library the templates call. A real IDE indexes it always, this
     * fixture indexes nothing but the files given to it, and a template calling {@code count()}
     * would otherwise look like a plugin bug rather than like a missing environment.
     *
     * <p>The packages the playground's composer.json names are deliberately <em>not</em> stubbed.
     * The playground has no vendor directory either, so a template referring to a Nette or Latte
     * class is exactly the case the plugin has to be quiet about - what it cannot resolve, it
     * cannot have an opinion on.
     */
    private static final String PHP_STANDARD_LIBRARY =
        "<?php\n"
            + "\n"
            + "function count(mixed $value, int $mode = 0): int {}\n"
            + "function implode(string $separator, array $array): string {}\n"
            + "function strtoupper(string $string): string {}\n"
            + "function number_format(float $num, int $decimals = 0): string {}\n"
            + "\n"
            + "class DateTimeImmutable\n"
            + "{\n"
            + "    public function __construct(string $datetime = 'now') {}\n"
            + "}\n";

    private void addTree(Path root, String target, String suffix) throws IOException {
        assertTrue("Missing " + root.toAbsolutePath(), Files.isDirectory(root));
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .sorted()
                .forEach(files::add);
        }
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            myFixture.addFileToProject(target + "/" + relative, Files.readString(file, StandardCharsets.UTF_8));
        }
    }

    private void applyPlaygroundSettings() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(SETTINGS)) {
            assertNotNull("Missing " + SETTINGS, stream);
            Element component = JDOMUtil.load(stream).getChild("component");
            assertNotNull("No <component> in " + SETTINGS, component);
            LatteSettings playground = XmlSerializer.deserialize(component, LatteSettings.class);

            LatteSettings settings = LatteSettings.getInstance(getProject());
            settings.tagSettings = playground.tagSettings;
            settings.filterSettings = playground.filterSettings;
            settings.functionSettings = playground.functionSettings;
            settings.variableSettings = playground.variableSettings;
        }
    }

    private static String describe(Map<String, List<String>> reports) {
        StringBuilder message = new StringBuilder(
            "What the plugin reports on the playground templates that promise silence, in full.\n"
                + "A report that is not in KNOWN_FALSE_POSITIVES is a regression: fix the plugin, or, if\n"
                + "the template is wrong, fix the template - never by taking the construct out.\n"
                + "An entry in that list which is missing here has been fixed: delete the entry.\n");
        for (Map.Entry<String, List<String>> report : reports.entrySet()) {
            message.append("  ").append(report.getKey()).append('\n');
            for (String problem : report.getValue()) {
                message.append("      ").append(problem).append('\n');
            }
        }
        return message.toString();
    }

    private static String position(String text, int offset) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return line + ":" + column;
    }
}
