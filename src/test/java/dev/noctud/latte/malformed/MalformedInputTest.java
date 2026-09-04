package dev.noctud.latte.malformed;

import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import dev.noctud.latte.BasePsiParsingTestCase;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.lexer.LatteLexer;
import dev.noctud.latte.lexer.LatteLookAheadLexer;
import dev.noctud.latte.settings.LatteSettings;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Feeds deliberately broken Latte to the lexer and the parser and asserts only
 * that the plugin survives it.
 *
 * A person typing in the editor produces broken input constantly - every
 * half-written tag is broken input - so "the plugin reports an error" is the
 * expected outcome and is a pass here. The three things that are never
 * acceptable are:
 *
 * <ol>
 *   <li><b>a thrown {@link Throwable}</b> from lexing or parsing;</li>
 *   <li><b>non-termination</b> - a file that does not finish inside
 *       {@link #PER_INPUT_TIMEOUT_MS};</li>
 *   <li><b>a lexer that stops making progress</b> - a token stream whose
 *       offsets do not move forward is an infinite loop that has not happened
 *       yet.</li>
 * </ol>
 *
 * Nothing is asserted about {@code PsiErrorElement}s, error messages or
 * recovery quality; a file full of errors passes.
 *
 * The inputs come from two places. The checked-in fixtures under
 * {@code data/malformed} cover the failure shapes that are worth naming, one
 * per file. The truncation and pathological-size cases are generated here,
 * deterministically, because a few seed constructs plus a loop cover far more
 * than a directory of near-identical files would.
 *
 * See {@code data/malformed/README.md} for how the fixtures are organised.
 */
public class MalformedInputTest extends BasePsiParsingTestCase {

    /**
     * Per-input budget. Once the JVM is warm the slowest input that finishes
     * at all takes well under a second, so three seconds is three orders of
     * magnitude above the median and a comfortable margin above the slowest;
     * anything that exceeds it is a runaway, not a slow machine.
     */
    private static final long PER_INPUT_TIMEOUT_MS = 3_000;

    /**
     * Parsed before the clock starts. Without it the first input in the list
     * pays for class loading, PSI setup and JIT - which was measured at nearly
     * nine seconds - and would fail the budget for reasons that have nothing
     * to do with the input.
     */
    private static final String WARM_UP = "<div n:foreach=\"$items as $item\">{$item|upper}</div>\n{if $a}x{/if}\n";
    private static final int WARM_UP_ROUNDS = 20;

    /**
     * Inputs that currently throw, hang or stall the lexer. Each entry is a
     * live robustness bug, kept here - rather than by softening the input -
     * so the gap stays visible. The test fails if an entry stops reproducing,
     * so the list cannot go stale.
     */
    private static final List<String> KNOWN_FAILURES = Arrays.asList(
        // The PHP-expression parser backtracks exponentially over nested "["
        // and "(" inside a tag. Lexing is instant in every case below - the
        // whole cost is in LatteParser - and the growth is measured:
        //
        //     {= [[[...       9 brackets   0.5 s
        //                    13 brackets   2.6 s
        //                    14 brackets   > 4 s     (15 characters of input)
        //     {if (((...     32 parens     0.08 s
        //                    64 parens     1.1 s
        //                    96 parens     > 4 s     (100 characters of input)
        //     {= ([([...      8 pairs      0.6 s
        //                    10 pairs      3.1 s
        //                    11 pairs      > 4 s     (25 characters of input)
        //
        // Being unterminated is not what triggers it: closing the tag does not
        // help, and balanced nesting blows up at the same rate - 19 levels of
        // {= [[[...]]]} and 16 levels of a nested array literal both exceed
        // four seconds, and both are valid Latte. This is the finding here
        // that can freeze a real editor; the depths involved are reachable by
        // holding a bracket key down.
        "pathological/DeepUnclosedBracketsInTag",
        "pathological/DeepBalancedNestedArray",

        // Super-linear rather than exponential, and far milder: a long run of
        // unmatched tag openers costs about n^1.3 in the parser. 2 000 of them
        // (4 KB) take 0.19 s, 20 000 (40 KB) do not finish inside the budget.
        // Lexing stays flat, so the cost is again in LatteParser.
        "pathological/ManyOpenBracesAndText"
    );

    /**
     * Valid constructs that get cut at every character position. This is what
     * typing looks like, and it is the highest-yield generator in the file.
     */
    private static final String[] TRUNCATION_SEEDS = {
        "{if $a}text{/if}",
        "{foreach $items as $item}{$item|upper}{/foreach}",
        "{block name}text{/block}",
        "{include 'partials/item.latte', item: $item}",
        "{* a comment *}",
        "{var $a = [1, 2, 'three' => $b]}",
        "{$article->getTitle()|truncate:30}",
        "{switch $a}{case 1}one{default}other{/switch}",
        "{= \"text {$a} more\"}",
        "{capture $out}{$a}{/capture}",
        "{define blockName, string $a}{$a}{/define}",
        "{= $a ?? $b ?: $c}",
        "{contentType xml}",
        "{syntax double}{{$a}}{/syntax}",
        "{php $a = 1;}",
        "<div n:if=\"$a\" class=\"x\">text</div>",
        "<ul n:inner-foreach=\"$items as $item\"><li>{$item}</li></ul>",
        "<script>var a = {$b};</script>",
        "<style>.a { color: {$c}; }</style>",
        "<div n:syntax=\"off\">{not a tag}</div>",
    };

    /**
     * Longest prefix count taken from one seed. Every seed here is shorter than
     * this, so in practice every prefix is tested; the stride only exists so
     * that adding a long seed later cannot quietly multiply the runtime.
     */
    private static final int MAX_PREFIXES_PER_SEED = 64;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LatteConfiguration.getInstance(getProject());
        getProject().registerService(LatteSettings.class);
    }

    @Override
    protected String getTestDataPath() {
        URL url = getClass().getClassLoader().getResource("data/malformed");
        assert url != null;
        return url.getFile();
    }

    @Test
    public void testMalformedInputNeitherThrowsNorHangs() throws IOException {
        List<Case> cases = new ArrayList<>();
        cases.addAll(fixtureCases());
        cases.addAll(truncationCases());
        cases.addAll(pathologicalCases());
        cases.addAll(unpairedSurrogateCases());

        Map<String, String> failures = new LinkedHashMap<>();
        Map<String, Long> elapsedPerCase = new LinkedHashMap<>();
        int leakedThreads = 0;

        for (int i = 0; i < WARM_UP_ROUNDS; i++) {
            assertNull(run(new Case("warmUp" + i, WARM_UP)));
        }

        long started = System.currentTimeMillis();
        ExecutorService executor = newExecutor();
        try {
            for (Case testCase : cases) {
                long caseStarted = System.currentTimeMillis();
                Future<String> future = executor.submit(() -> run(testCase));
                String failure;
                try {
                    failure = future.get(PER_INPUT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    failure = "DID NOT TERMINATE within " + PER_INPUT_TIMEOUT_MS + " ms";
                    // The worker cannot be killed - a runaway lexer has no
                    // interruption point - so it is abandoned and a fresh one
                    // takes over. It is a daemon thread, so it cannot keep the
                    // JVM alive after the suite finishes.
                    future.cancel(true);
                    executor.shutdownNow();
                    executor = newExecutor();
                    leakedThreads++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while parsing " + testCase.name, e);
                } catch (ExecutionException e) {
                    failure = "THREW " + describe(e.getCause());
                }
                elapsedPerCase.put(testCase.name, System.currentTimeMillis() - caseStarted);
                if (failure != null) {
                    failures.put(testCase.name, failure);
                }
            }
        } finally {
            executor.shutdownNow();
        }

        long elapsed = System.currentTimeMillis() - started;
        System.out.println("[malformed] inputs=" + cases.size()
            + " failing=" + failures.size()
            + " leakedThreads=" + leakedThreads
            + " elapsedMs=" + elapsed);
        // Printed so that the budget stays auditable: if the slowest input
        // that still passes ever creeps towards PER_INPUT_TIMEOUT_MS, it is
        // visible here before it turns into a flaky failure.
        elapsedPerCase.entrySet().stream()
            .filter(entry -> !failures.containsKey(entry.getKey()))
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(10)
            .forEach(entry -> System.out.println("[malformed] slowest " + entry.getValue()
                + " ms  " + entry.getKey()));

        Set<String> unexpected = new LinkedHashSet<>(failures.keySet());
        unexpected.removeAll(KNOWN_FAILURES);

        Set<String> repaired = new LinkedHashSet<>(KNOWN_FAILURES);
        repaired.removeAll(failures.keySet());

        StringBuilder message = new StringBuilder();
        if (!unexpected.isEmpty()) {
            message.append("Malformed input was not survived (")
                .append(unexpected.size()).append(" of ").append(cases.size()).append("):\n");
            for (String name : unexpected) {
                message.append("  ").append(name).append('\n')
                    .append("      ").append(failures.get(name)).append('\n');
            }
        }
        if (!repaired.isEmpty()) {
            message.append("These inputs are survived now; remove them from KNOWN_FAILURES:\n");
            for (String name : repaired) {
                message.append("  ").append(name).append('\n');
            }
        }

        assertTrue(message.toString(), message.length() == 0);
    }

    private ExecutorService newExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "malformed-input-parse");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Lexes and parses one input. Returns {@code null} when the input was
     * survived, or a one-line description of what went wrong.
     */
    private String run(Case testCase) {
        try {
            String progress = checkLexerProgress(testCase.text);
            if (progress != null) {
                return progress;
            }
            PsiFile psiFile = createPsiFile(fileNameFor(testCase.name), testCase.text);
            ensureParsed(psiFile);
            // Walking the tree is part of the contract: a parser can produce an
            // AST whose PSI wrappers throw on first touch.
            psiFile.getText();
            psiFile.getNode().getFirstChildNode();
            return null;
        } catch (Throwable t) {
            return "THREW " + describe(t);
        }
    }

    /**
     * Drives the lexer the parser actually uses and checks that it advances.
     *
     * Two cheap invariants catch a spinning lexer without asserting anything
     * about the token stream itself: token offsets never move backwards, and
     * the number of tokens cannot exceed the number of characters by more than
     * a small slack. A lexer that emits a zero-width token forever violates the
     * second one and fails here instead of hanging the build.
     */
    private String checkLexerProgress(String text) {
        Lexer lexer = new LatteLookAheadLexer(new LatteLexer());
        lexer.start(text);

        int limit = text.length() + 1024;
        int steps = 0;
        int previousStart = 0;

        while (lexer.getTokenType() != null) {
            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();
            if (start < previousStart) {
                return "LEXER WENT BACKWARDS at token " + steps
                    + " (start " + start + " after " + previousStart + ")";
            }
            if (end < start || end > text.length()) {
                return "LEXER RETURNED AN OUT-OF-RANGE TOKEN at " + steps
                    + " [" + start + ", " + end + ") for a buffer of " + text.length();
            }
            previousStart = start;
            if (++steps > limit) {
                return "LEXER MADE NO PROGRESS: more than " + limit
                    + " tokens for " + text.length() + " characters";
            }
            lexer.advance();
        }
        return null;
    }

    private List<Case> fixtureCases() throws IOException {
        Path root = Paths.get(getTestDataPath());
        assertTrue("malformed fixture directory is missing: " + root, Files.isDirectory(root));

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".latte"))
                .sorted()
                .forEach(files::add);
        }
        assertFalse("no malformed fixtures were found in " + root, files.isEmpty());

        List<Case> cases = new ArrayList<>();
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            cases.add(new Case(name, new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
        }
        return cases;
    }

    /**
     * Every prefix of every seed. Deterministic: no randomness, and the stride
     * depends only on the seed's length, so a name in a failure report always
     * refers to the same input.
     */
    private List<Case> truncationCases() {
        List<Case> cases = new ArrayList<>();
        for (String seed : TRUNCATION_SEEDS) {
            int stride = Math.max(1, seed.length() / MAX_PREFIXES_PER_SEED);
            for (int length = 0; length <= seed.length(); length += stride) {
                cases.add(new Case(
                    "truncation/" + seedName(seed) + "@" + length,
                    seed.substring(0, length)));
            }
        }
        return cases;
    }

    /**
     * Size and depth. A lexer that backtracks, or a parser that recurses per
     * nesting level, shows up here as a timeout or a StackOverflowError rather
     * than as a wrong token.
     */
    private List<Case> pathologicalCases() {
        List<Case> cases = new ArrayList<>();

        cases.add(new Case("pathological/DeepUnclosedIfTags", repeat("{if $a}", 500)));
        cases.add(new Case("pathological/DeepClosedIfTags", repeat("{if $a}", 500) + repeat("{/if}", 500)));
        cases.add(new Case("pathological/DeepUnclosedElements", repeat("<div>", 500)));
        // Kept at 200 rather than 500 for headroom: this is the slowest input
        // that still passes, and it is mildly super-linear (about n^1.3 - 100
        // elements 0.21 s, 200 0.64 s, 500 1.65 s), so a larger size would sit
        // close enough to the budget to go flaky on a slow machine.
        cases.add(new Case("pathological/DeepUnclosedNAttributeElements", repeat("<div n:if=\"$a\">", 200)));
        cases.add(new Case("pathological/DeepUnclosedComments", repeat("{*", 2000)));
        cases.add(new Case("pathological/ManyOpenBraces", repeat("{", 5000)));
        cases.add(new Case("pathological/ManyOpenBracesAndText", repeat("{a", 20000)));
        cases.add(new Case("pathological/AlternatingBraces", repeat("{}", 50000)));
        cases.add(new Case("pathological/ManySingleQuotes", repeat("'", 50000)));
        cases.add(new Case("pathological/ManyDoubleQuotes", repeat("\"", 50000)));
        // Deliberately shallow: these are the smallest depths that reproduce
        // with margin, so the failure report names an input a person could
        // type. {= ([([... is the same bug amplified and is left out only
        // because each hanging case costs a full timeout.
        cases.add(new Case("pathological/DeepUnclosedBracketsInTag", "{= " + repeat("[", 24)));
        // Parentheses backtrack too - measured 32 -> 0.08 s, 64 -> 1.13 s, 96 -> over 4 s -
        // but the curve is not monotonic: at 160 the cost drops back inside the budget, so
        // something is cutting the search off at depth. That makes any single depth an
        // unreliable "must still hang" entry, so this is measured rather than asserted, and
        // the shallow guard below is what protects the boundary. Pinning down where the
        // cutoff is belongs to the fix - see .ai/plans/06.
        cases.add(new Case("pathological/DeepUnclosedParenthesesInTag", "{if " + repeat("(", 160)));
        cases.add(new Case("pathological/DeepBalancedNestedArray",
            "{var $a = " + repeat("[1, ", 24) + repeat("]", 24) + "}"));
        // Shallow enough to stay well inside the budget. These guard the
        // boundary: if the blowup ever starts earlier, they turn into new
        // failures instead of into a slightly slower run.
        cases.add(new Case("pathological/ShallowNestedBracketsInTag", "{= " + repeat("[", 8)));
        cases.add(new Case("pathological/ShallowNestedParenthesesInTag", "{if " + repeat("(", 32)));
        cases.add(new Case("pathological/DeepNestedBracesInTag", "{if " + repeat("{", 2000)));
        cases.add(new Case("pathological/LongSingleLineOfText", repeat("a", 200000)));
        cases.add(new Case("pathological/LongUnterminatedComment", "{*" + repeat("a", 200000)));
        cases.add(new Case("pathological/LongUnterminatedString", "{= '" + repeat("a", 200000)));
        cases.add(new Case("pathological/LongUnterminatedTag", "{if " + repeat("a", 200000)));
        cases.add(new Case("pathological/LongUnterminatedScript", "<script>" + repeat("a", 200000)));
        cases.add(new Case("pathological/LongUnterminatedAttributeValue", "<div class=\"" + repeat("a", 200000)));
        cases.add(new Case("pathological/ManyPrintTags", repeat("{$a}", 10000)));
        cases.add(new Case("pathological/ManyNAttributeElements", repeat("<div n:if=\"$a\"></div>", 5000)));
        cases.add(new Case("pathological/ManyUnclosedSyntaxDouble", repeat("{syntax double}", 500)));
        cases.add(new Case("pathological/ManyUnclosedSyntaxOff", repeat("{syntax off}", 500)));
        cases.add(new Case("pathological/ManyStrayCloseTags", repeat("</div>", 5000)));

        return cases;
    }

    /**
     * A lone surrogate cannot be written to a UTF-8 fixture file, so these
     * three inputs only exist here.
     */
    private List<Case> unpairedSurrogateCases() {
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("surrogates/UnpairedHighSurrogateInText", "{if $a}\uD800{/if}"));
        cases.add(new Case("surrogates/UnpairedLowSurrogateInText", "{if $a}\uDC00{/if}"));
        cases.add(new Case("surrogates/UnpairedHighSurrogateInTag", "{if \uD800$a}text{/if}"));
        cases.add(new Case("surrogates/ReversedSurrogatePair", "{= '\uDC00\uD800'}"));
        return cases;
    }

    private static String repeat(String unit, int times) {
        StringBuilder builder = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(unit);
        }
        return builder.toString();
    }

    /** A stable, readable identifier for a seed, used in case names. */
    private static String seedName(String seed) {
        StringBuilder builder = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < seed.length() && builder.length() < 24; i++) {
            char c = seed.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            } else {
                upper = true;
            }
        }
        return builder.length() == 0 ? "Seed" : builder.toString();
    }

    /** Case names contain slashes and '@'; a PSI file name may not. */
    private static String fileNameFor(String caseName) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < caseName.length(); i++) {
            char c = caseName.charAt(i);
            builder.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return builder.toString();
    }

    private static String describe(Throwable t) {
        if (t == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(t.getClass().getName());
        if (t.getMessage() != null) {
            builder.append(": ").append(t.getMessage().replace('\n', ' '));
        }
        StackTraceElement[] trace = t.getStackTrace();
        if (trace.length > 0) {
            builder.append(" at ").append(trace[0]);
        }
        return builder.toString();
    }

    private static final class Case {
        final String name;
        final String text;

        Case(String name, String text) {
            this.name = name;
            this.text = text;
        }
    }
}
