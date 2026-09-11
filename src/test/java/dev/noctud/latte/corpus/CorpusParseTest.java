package dev.noctud.latte.corpus;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import dev.noctud.latte.BasePsiParsingTestCase;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.settings.LatteSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Parses every {@code .latte} file in an external corpus and reports parse errors.
 *
 * The corpus is not part of this repository. Unless {@code LATTE_CORPUS_DIR} points at a
 * directory the test is reported as skipped - never as passed, which is what it used to do and
 * what made a run without the corpus look like a clean one. The runner is what turns the
 * assumption into a skip: in a JUnit 3 test a failed assumption is otherwise a failure.
 *
 * The report is written to {@code LATTE_CORPUS_REPORT} (default
 * {@code .ai/corpus-parse-report.txt}), never to a tracked path.
 */
@RunWith(JUnit38AssumeSupportRunner.class)
public class CorpusParseTest extends BasePsiParsingTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LatteConfiguration.getInstance(getProject());
        getProject().registerService(LatteSettings.class);
    }

    @Override
    protected String getTestDataPath() {
        return ".";
    }

    @Test
    public void testCorpusParsesWithoutErrors() throws IOException {
        Path root = CorpusStamp.corpusOrSkip();
        assertTrue("LATTE_CORPUS_DIR is not a directory: " + root, Files.isDirectory(root));

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".latte"))
                .sorted()
                .forEach(files::add);
        }

        Map<Path, List<String>> failures = new LinkedHashMap<>();
        int unreadable = 0;

        for (Path file : files) {
            String text;
            try {
                text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            } catch (IOException e) {
                unreadable++;
                continue;
            }

            List<String> errors;
            try {
                PsiFile psiFile = createPsiFile(file.getFileName().toString(), text);
                ensureParsed(psiFile);
                errors = collectParseErrors(psiFile, text);
            } catch (Throwable t) {
                errors = new ArrayList<>();
                errors.add("THROWN " + t.getClass().getName() + ": " + t.getMessage());
            }

            if (!errors.isEmpty()) {
                failures.put(file, errors);
            }
        }

        writeReport(root, files.size(), unreadable, failures);

        System.out.println("[corpus] files=" + files.size()
            + " unreadable=" + unreadable
            + " filesWithParseErrors=" + failures.size());
    }

    private List<String> collectParseErrors(PsiFile psiFile, String text) {
        List<String> errors = new ArrayList<>();
        psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiErrorElement) {
                    errors.add(position(text, element.getTextRange().getStartOffset())
                        + " " + ((PsiErrorElement) element).getErrorDescription());
                }
                super.visitElement(element);
            }
        });
        return errors;
    }

    /**
     * Line:column of an offset. Deliberately reports the position only - the
     * corpus is third-party source and its text must not reach the report.
     */
    private String position(String text, int offset) {
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

    private void writeReport(Path root, int total, int unreadable, Map<Path, List<String>> failures) throws IOException {
        String target = System.getenv("LATTE_CORPUS_REPORT");
        Path report = Paths.get(target == null || target.trim().isEmpty()
            ? ".ai/corpus-parse-report.txt"
            : target.trim());

        if (report.getParent() != null) {
            Files.createDirectories(report.getParent());
        }

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(report, StandardCharsets.UTF_8))) {
            out.println("files=" + total);
            out.println("unreadable=" + unreadable);
            out.println("filesWithParseErrors=" + failures.size());
            out.println();
            for (Map.Entry<Path, List<String>> entry : failures.entrySet()) {
                out.println(root.relativize(entry.getKey()));
                for (String error : entry.getValue()) {
                    out.println("    " + error);
                }
            }
        }
    }
}
