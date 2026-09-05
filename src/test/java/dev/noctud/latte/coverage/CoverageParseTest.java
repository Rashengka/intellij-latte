package dev.noctud.latte.coverage;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import dev.noctud.latte.BasePsiParsingTestCase;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.settings.LatteSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;

/**
 * Parses every fixture in {@code data/coverage} and asserts that none of them
 * produces a {@link PsiErrorElement}.
 *
 * The fixtures are a combinatorial base covering the whole Latte 2.11 surface
 * the plugin claims to support - every tag, {@code n:} attribute, filter,
 * function and syntax form, plus the combinations that stress the lexer. They
 * live in this repository, so unlike {@code CorpusParseTest} this test needs no
 * environment variable and runs on every {@code ./gradlew test}.
 *
 * See {@code data/coverage/README.md} for how the fixtures are organised.
 */
public class CoverageParseTest extends BasePsiParsingTestCase {

    /**
     * Fixtures that are valid Latte 2.11 but that the plugin currently fails to
     * parse. Each entry is a known parser bug, kept here - rather than by
     * deleting or weakening the fixture - so the gap stays visible. Remove an
     * entry as soon as the underlying bug is fixed; the test fails if an entry
     * no longer reproduces, so the list cannot go stale.
     *
     * <ul>
     *   <li>{@code edge-cases/BraceInLiteralInsideNestedBraces.latte} - the tag now ends at the
     *       right brace, but a curly brace written inside a quoted literal is still handed to the
     *       parser as {@code T_PHP_RIGHT_CURLY_BRACE} instead of string content. That happens one
     *       layer further in, in the lexer that splits a tag's PHP into tokens, and it is written
     *       up with its exit condition in {@code .ai/plans}.
     * </ul>
     */
    private static final List<String> KNOWN_FAILURES = List.of(
        "edge-cases/BraceInLiteralInsideNestedBraces.latte"
    );

    /**
     * Subdirectory reserved for deliberately invalid input. Nothing in it is
     * expected to parse cleanly, so it is not walked.
     */
    private static final String INVALID_DIR = "invalid";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LatteConfiguration.getInstance(getProject());
        getProject().registerService(LatteSettings.class);
    }

    @Override
    protected String getTestDataPath() {
        URL url = getClass().getClassLoader().getResource("data/coverage");
        assert url != null;
        return url.getFile();
    }

    @Test
    public void testCoverageFixturesParseWithoutErrors() throws IOException {
        Path root = Paths.get(getTestDataPath());
        assertTrue("coverage fixture directory is missing: " + root, Files.isDirectory(root));

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".latte"))
                .filter(path -> !root.relativize(path).startsWith(INVALID_DIR))
                .sorted()
                .forEach(files::add);
        }

        assertFalse("no coverage fixtures were found in " + root, files.isEmpty());

        Map<String, List<String>> failures = new LinkedHashMap<>();

        for (Path file : files) {
            String name = relativeName(root, file);
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

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
                failures.put(name, errors);
            }
        }

        Set<String> unexpected = new HashSet<>(failures.keySet());
        unexpected.removeAll(KNOWN_FAILURES);

        Set<String> repaired = new HashSet<>(KNOWN_FAILURES);
        repaired.removeAll(failures.keySet());

        StringBuilder message = new StringBuilder();
        if (!unexpected.isEmpty()) {
            message.append("Coverage fixtures failed to parse:\n");
            for (String name : sorted(unexpected)) {
                message.append("  ").append(name).append('\n');
                for (String error : failures.get(name)) {
                    message.append("      ").append(error).append('\n');
                }
            }
        }
        if (!repaired.isEmpty()) {
            message.append("These fixtures now parse cleanly; remove them from KNOWN_FAILURES:\n");
            for (String name : sorted(repaired)) {
                message.append("  ").append(name).append('\n');
            }
        }

        assertTrue(message.toString(), message.length() == 0);
    }

    private List<String> sorted(Set<String> names) {
        List<String> list = new ArrayList<>(names);
        list.sort(String::compareTo);
        return list;
    }

    private String relativeName(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
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
}
