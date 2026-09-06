package dev.noctud.latte.corpus;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.inspections.*;
import dev.noctud.latte.settings.LatteSettings;
import dev.noctud.latte.version.LatteVersion;
import dev.noctud.latte.version.LatteVersionResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Counts what the plugin <em>reports</em> on an external corpus, which CorpusParseTest does not:
 * that one asks whether a template parses, and a template can parse perfectly while being
 * underlined all over.
 *
 * <p>It exists because the change it was written for - answering the tag and filter registry per
 * Latte version - can only go wrong in one way, by reporting something that is correct. Without a
 * count over real templates that change would have had nothing to be measured against, and "no new
 * false reports" would have been a claim rather than a result.
 *
 * <p>It is a measurement, not a gate, and it says so by never failing on a count: there is no
 * number here that is right. What makes it useful is running it before a change and after one, and
 * the report it writes is shaped for exactly that comparison.
 *
 * <p><b>The corpus is not part of this repository and none of it may enter one.</b> The report goes
 * to a path under {@code .ai/}, which is excluded from git, and every quoted name is replaced by an
 * ellipsis before it is written or printed - a report reads "Undefined class '…'", never the class.
 * The count is the finding; the name belongs to somebody else.
 */
public class CorpusInspectionTest extends BasePlatformTestCase {

    private static final String DEFAULT_REPORT = ".ai/corpus-inspection-report.txt";

    /** Anything quoted is a name out of the corpus and is not ours to write down. */
    private static final Pattern QUOTED = Pattern.compile("'[^']*'|\"[^\"]*\"");

    /**
     * And so is a file name, which arrives unquoted - "File _something.latte is missing".
     *
     * A dot with letters on both sides and no space around it is a file name or something shaped
     * like one; a version is not, because "3.1.6" has no letters after its last dot. Collapsing
     * these is not only what the class comment promises, it is also what makes the histogram
     * honest: ten spellings of "a file is missing" are one shape, not ten.
     */
    private static final Pattern FILE_NAME = Pattern.compile("\\S*[A-Za-z0-9_-]\\.[A-Za-z]{2,}\\S*");

    public void testCorpusInspectionReport() throws IOException {
        String corpusDir = System.getenv("LATTE_CORPUS_DIR");
        if (corpusDir == null || corpusDir.trim().isEmpty()) {
            return;
        }
        Path root = Paths.get(corpusDir.trim());
        assertTrue("LATTE_CORPUS_DIR is not a directory: " + root, Files.isDirectory(root));

        String forced = System.getenv("LATTE_CORPUS_VERSION");
        LatteVersion version = versionOf(root);
        String line = forced != null && !forced.trim().isEmpty()
            ? forced.trim()
            : (version.isUndetermined() ? "" : version.line());
        LatteSettings.getInstance(getProject()).latteVersionOverride = line;

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

        List<Path> files = templatesIn(root);
        int limit = limit();
        if (limit > 0 && files.size() > limit) {
            files = files.subList(0, limit);
        }

        String chased = System.getenv("LATTE_CORPUS_SHAPE");
        List<Path> chasedFiles = new ArrayList<>();
        Map<String, Integer> byShape = new TreeMap<>();
        int reports = 0;
        int filesWithReports = 0;
        int unreadable = 0;

        for (Path file : files) {
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                unreadable++;
                continue;
            }
            List<String> shapes = shapesReportedOn(text);
            if (!shapes.isEmpty()) {
                filesWithReports++;
                reports += shapes.size();
                for (String shape : shapes) {
                    byShape.merge(shape, 1, Integer::sum);
                    if (chased != null && !chased.trim().isEmpty() && shape.contains(chased.trim())) {
                        chasedFiles.add(file);
                    }
                }
            }
        }

        writeReport(version, line, files.size(), unreadable, filesWithReports, reports, byShape, chasedFiles);
    }

    /**
     * Which Latte the corpus is written for, read from its own Composer files.
     *
     * Without this the measurement could not see the thing it was built to measure. Templates are
     * inspected as text in a fixture that has no composer.lock anywhere above it, so the registry
     * would answer "undetermined" for every one of them - and "undetermined" is the state that
     * withholds nothing. The report would have come out identical whether the version branch
     * worked, was broken, or was never written, which is the worst shape a measurement can have.
     *
     * The line is applied through the override because that is the only setting a fixture file can
     * be reached by; the registry cannot tell the difference, and the report says which line it
     * was measured under so the choice is not silent.
     *
     * <p>{@code LATTE_CORPUS_VERSION} forces a different line, which is how the other half of the
     * branch gets measured at all: both corpora available here lock Latte 2.11, so nothing in them
     * would ever exercise what the plugin does under Latte 3. Reported under a forced line, a
     * shape that appears and no other is the list of tags the version branch withholds - each one
     * then either a removal the engine really made, or a mistake to fix.
     */
    private static LatteVersion versionOf(Path root) {
        return LatteVersionResolver.resolve(new LatteVersionResolver.ComposerFileReader() {
            @Override
            public String read(String directory, String fileName) {
                try {
                    Path file = Paths.get(directory, fileName);
                    return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
                } catch (IOException | RuntimeException e) {
                    return null;
                }
            }

            @Override
            public List<String> ancestorsOf(String directory) {
                List<String> ancestors = new ArrayList<>();
                for (Path at = Paths.get(directory); at != null; at = at.getParent()) {
                    ancestors.add(at.toString());
                }
                return ancestors;
            }
        }, root.toAbsolutePath().toString(), null);
    }

    /**
     * The report goes under .ai/ and never into the repository, but the promise the class comment
     * makes is broader than "quoted names": it says the count is the finding and the name belongs
     * to somebody else. A file name carries just as much of somebody else's project as a class
     * name does, and it arrives unquoted - "File _something.latte is missing".
     */
    public void testAFileNameIsDroppedEvenThoughNothingQuotesIt() {
        assertEquals("File … is missing", anonymise("File _mainPills.latte is missing"));
        assertEquals("File … is missing", anonymise("File cheat-sheet.css.latte is missing"));
        assertEquals("File … is missing", anonymise("File @menu.latte is missing"));
    }

    /**
     * And the reason it is worth a test rather than a wider pattern: collapsing them is also what
     * makes the histogram honest. Ten spellings of one report are one shape, not ten.
     */
    public void testTheShapeItselfSurvives() {
        assertEquals("Undefined latte filter '…'", anonymise("Undefined latte filter 'money'"));
        assertEquals("Missing required filter parameters (1 required)",
            anonymise("Missing required filter parameters (1 required)"));
        assertEquals("Tag {includeblock} was removed in Latte 3.0",
            anonymise("Tag {includeblock} was removed in Latte 3.0"));
        assertEquals("Filter '…' does not exist before Latte 3.1.3",
            anonymise("Filter 'column' does not exist before Latte 3.1.3"));
    }

    private List<String> shapesReportedOn(String text) {
        myFixture.configureByText("corpus.latte", text);
        List<String> shapes = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() == null
                || info.getSeverity().compareTo(HighlightSeverity.WEAK_WARNING) < 0) {
                continue;
            }
            shapes.add(anonymise(info.getDescription()));
        }
        return shapes;
    }

    /** Keeps the shape of a report and drops the name in it. See the class comment. */
    static String anonymise(String description) {
        return replaceAll(replaceAll(description, QUOTED, "'…'"), FILE_NAME, "…");
    }

    private static String replaceAll(String text, Pattern pattern, String with) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder shape = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(shape, Matcher.quoteReplacement(with));
        }
        matcher.appendTail(shape);
        return shape.toString();
    }

    private static int limit() {
        String value = System.getenv("LATTE_CORPUS_LIMIT");
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<Path> templatesIn(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".latte"))
                .sorted()
                .forEach(files::add);
        }
        return files;
    }

    /**
     * @param chasedFiles the templates behind one shape, when {@code LATTE_CORPUS_SHAPE} named one.
     *                    A histogram says how often something is reported and never which template
     *                    to open, so a shape worth chasing could not be chased. The list is opt-in
     *                    because it is the one part of the report that is corpus content rather
     *                    than a count - it belongs under {@code .ai/}, which git does not carry,
     *                    and nowhere else.
     */
    private static void writeReport(
        LatteVersion version, String measuredAs, int files, int unreadable, int filesWithReports,
        int reports, Map<String, Integer> byShape, List<Path> chasedFiles
    ) throws IOException {
        String target = System.getenv("LATTE_CORPUS_INSPECTION_REPORT");
        Path path = Paths.get(target == null || target.trim().isEmpty() ? DEFAULT_REPORT : target.trim());
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        StringBuilder out = new StringBuilder();
        out.append("latteVersion=").append(version).append('\n');
        out.append("measuredAs=").append(measuredAs.isEmpty() ? "undetermined" : measuredAs).append('\n');
        out.append("files=").append(files).append('\n');
        out.append("unreadable=").append(unreadable).append('\n');
        out.append("filesWithReports=").append(filesWithReports).append('\n');
        out.append("reports=").append(reports).append('\n');
        out.append("distinctShapes=").append(byShape.size()).append('\n');
        out.append('\n');
        byShape.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .forEach(entry -> out.append(String.format("%6d  %s%n", entry.getValue(), entry.getKey())));
        if (!chasedFiles.isEmpty()) {
            out.append('\n').append("files behind the shape asked for:").append('\n');
            for (Path file : chasedFiles) {
                out.append("  ").append(file.toString()).append('\n');
            }
        }
        Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        System.out.println("[corpus-inspection] latteVersion=" + version
            + " measuredAs=" + (measuredAs.isEmpty() ? "undetermined" : measuredAs)
            + " files=" + files
            + " filesWithReports=" + filesWithReports
            + " reports=" + reports
            + " distinctShapes=" + byShape.size()
            + " -> " + path);
    }
}
