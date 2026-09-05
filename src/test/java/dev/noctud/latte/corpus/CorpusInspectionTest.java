package dev.noctud.latte.corpus;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.inspections.*;

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

    public void testCorpusInspectionReport() throws IOException {
        String corpusDir = System.getenv("LATTE_CORPUS_DIR");
        if (corpusDir == null || corpusDir.trim().isEmpty()) {
            return;
        }
        Path root = Paths.get(corpusDir.trim());
        assertTrue("LATTE_CORPUS_DIR is not a directory: " + root, Files.isDirectory(root));

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
                }
            }
        }

        writeReport(files.size(), unreadable, filesWithReports, reports, byShape);
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
        Matcher matcher = QUOTED.matcher(description);
        StringBuilder shape = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(shape, "'…'");
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

    private static void writeReport(
        int files, int unreadable, int filesWithReports, int reports, Map<String, Integer> byShape
    ) throws IOException {
        String target = System.getenv("LATTE_CORPUS_INSPECTION_REPORT");
        Path path = Paths.get(target == null || target.trim().isEmpty() ? DEFAULT_REPORT : target.trim());
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        StringBuilder out = new StringBuilder();
        out.append("files=").append(files).append('\n');
        out.append("unreadable=").append(unreadable).append('\n');
        out.append("filesWithReports=").append(filesWithReports).append('\n');
        out.append("reports=").append(reports).append('\n');
        out.append("distinctShapes=").append(byShape.size()).append('\n');
        out.append('\n');
        byShape.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .forEach(entry -> out.append(String.format("%6d  %s%n", entry.getValue(), entry.getKey())));
        Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        System.out.println("[corpus-inspection] files=" + files
            + " filesWithReports=" + filesWithReports
            + " reports=" + reports
            + " distinctShapes=" + byShape.size()
            + " -> " + path);
    }
}
