package dev.noctud.latte.performance;

import com.intellij.psi.PsiFile;
import dev.noctud.latte.BasePsiParsingTestCase;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.settings.LatteSettings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Asserts the <b>shape of the cost curve</b> of parsing nested brackets inside
 * a tag, rather than any absolute time.
 *
 * <h2>Why a ratio and not a budget</h2>
 *
 * An absolute budget can only tell "it finished" from "it did not". It cannot
 * tell a linear parser from an exponential one, because both finish on a small
 * enough input - and the input that freezes an editor is small: three levels of
 * nesting beyond the last one that passed is the difference between instant and
 * a hang. A budget therefore has to be set at the exact depth the defect
 * happens to reach today, and it goes stale the moment a machine gets faster.
 *
 * So this test parses the <b>same shape at two depths</b>, one double the
 * other, in one run on one machine, and asserts the ratio between them. What
 * that ratio is worth:
 *
 * <pre>
 *   parser is linear in depth      doubling costs about 2x
 *   parser is quadratic            doubling costs about 4x
 *   parser is cubic                doubling costs about 8x
 *   parser doubles per level       doubling depth 7 -&gt; 14 costs 2^7 = 128x
 * </pre>
 *
 * {@link #MAX_RATIO} is therefore set at 8: everything up to a cubic parser
 * passes, and the exponential blowup this test was written for misses it by
 * more than an order of magnitude. Nothing about the number depends on how fast
 * the machine is, because both halves of every ratio are measured on the same
 * machine in the same run, interleaved.
 *
 * <h2>The three shapes</h2>
 *
 * <ol>
 *   <li><b>unclosed brackets</b>, {@code &#123;= [[[...} - what holding the
 *       bracket key down produces, and the cheapest way to reach a large depth;
 *   <li><b>a balanced array literal</b>, {@code &#123;= [1, [1, ...]]} - valid
 *       Latte, to show the curve is a property of the grammar and not of broken
 *       input;
 *   <li><b>the same literal with keys</b>, {@code &#123;= [0 =&gt; [0 =&gt; ...]]} -
 *       the control. It was linear even while the other two were exponential,
 *       because back then {@code phpArrayItem} tried a key first and only threw
 *       the parsed subtree away when no arrow followed - which is exactly what
 *       does not happen when there is an arrow. Keeping it in the table is what
 *       makes a failure diagnostic: if (1) and (2) fail while (3) passes, the
 *       array item rule is parsing its subtree twice again; if all three fail,
 *       something more general regressed.
 * </ol>
 *
 * <h2>How much room there is today</h2>
 *
 * <pre>
 *   unclosed brackets       depth  7 -&gt; 14     1.0 ms -&gt;  3.9 ms    4.0x
 *   balanced array          depth  8 -&gt; 16     0.3 ms -&gt;  0.5 ms    1.9x
 *   keyed array (control)   depth  8 -&gt; 16     0.5 ms -&gt;  0.9 ms    1.8x
 * </pre>
 *
 * The two array shapes are flat and stay flat well past these depths: 128
 * levels of {@code [1, [1, ...]]} parse in 3 ms. The unclosed shape is not
 * flat - it is still a power law of roughly n^2.6, measured out to 128
 * brackets - which is why it sits at half the limit rather than a quarter of
 * it. That residual is a separate, much milder defect than the doubling this
 * test was written for; it is recorded in .ai/plans/06. Every ratio is printed
 * on every run, so the margin cannot erode unseen.
 */
public class ParserBacktrackingTest extends BasePsiParsingTestCase {

    /**
     * Rounds parsed and thrown away before measuring. Only the shallow input of
     * each shape is warmed: it runs the very same parser methods as the deep
     * one, so it takes the interpreter and the first JIT tiers out of both
     * halves of every ratio, without paying the deep input's cost three more
     * times while the defect is still present.
     */
    private static final int WARMUP_ROUNDS = 3;

    /**
     * Measured rounds per input, interleaved - every input once per round. The
     * median is kept, so a GC pause or a busy patch of the machine has to hit
     * three of the five samples before it can move a result.
     */
    private static final int MEASURED_RUNS = 5;

    /**
     * The most a doubling of the nesting depth may cost. See the class comment:
     * a cubic parser sits exactly on this line, and doubling per level is 128x
     * at these depths.
     */
    private static final double MAX_RATIO = 8.0;

    /**
     * Below this, the difference between two medians is scheduler noise rather
     * than parser work, so it is used as the floor of every denominator. A
     * shallow input that measures 10 us must not be able to turn 40 us of
     * jitter on the deep one into a failure.
     */
    private static final double DENOMINATOR_FLOOR_MS = 0.05;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LatteConfiguration.getInstance(getProject());
        getProject().registerService(LatteSettings.class);
    }

    @Override
    protected String getTestDataPath() {
        return "";
    }

    @Test
    public void testDoublingTheNestingDepthDoesNotExplode() {
        List<Shape> shapes = Arrays.asList(
            new Shape("unclosed brackets", "{= [[[...", 7, 14, ParserBacktrackingTest::unclosedBrackets),
            new Shape("balanced array", "{= [1, [1, ...]]}", 8, 16, ParserBacktrackingTest::balancedArray),
            new Shape("keyed array (control)", "{= [0 => [0 => ...]]}", 8, 16, ParserBacktrackingTest::keyedArray)
        );

        List<Input> inputs = new ArrayList<>();
        for (Shape shape : shapes) {
            inputs.add(new Input(shape, shape.shallowDepth));
            inputs.add(new Input(shape, shape.deepDepth));
        }

        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (Input input : inputs) {
                if (input.depth == input.shape.shallowDepth) {
                    parse(input);
                }
            }
        }

        double[][] samples = new double[inputs.size()][MEASURED_RUNS];
        for (int run = 0; run < MEASURED_RUNS; run++) {
            for (int i = 0; i < inputs.size(); i++) {
                long t0 = System.nanoTime();
                parse(inputs.get(i));
                samples[i][run] = (System.nanoTime() - t0) / 1_000_000.0;
            }
        }

        StringBuilder failures = new StringBuilder();
        System.out.println("[backtracking] runs=" + MEASURED_RUNS
            + " (+" + WARMUP_ROUNDS + " warmup rounds on the shallow input of each shape)"
            + ", limit " + (long) MAX_RATIO + "x per doubling of depth");
        for (int i = 0; i < inputs.size(); i += 2) {
            Shape shape = inputs.get(i).shape;
            double shallowMs = median(samples[i]);
            double deepMs = median(samples[i + 1]);
            double ratio = deepMs / Math.max(shallowMs, DENOMINATOR_FLOOR_MS);

            String line = String.format(Locale.ROOT,
                "%-22s %s   depth %2d %9.3f ms -> depth %2d %9.3f ms   %8.1fx",
                shape.name, shape.sketch,
                shape.shallowDepth, shallowMs, shape.deepDepth, deepMs, ratio);
            System.out.println("[backtracking] " + line
                + (ratio > MAX_RATIO ? "   OVER THE " + (long) MAX_RATIO + "x LIMIT" : ""));
            if (ratio > MAX_RATIO) {
                failures.append("  ").append(line).append('\n');
            }
        }

        assertTrue("Parsing cost more than " + (long) MAX_RATIO + "x when the nesting depth doubled,"
            + " which is super-cubic growth in the depth of a bracket - the shape of a parser that\n"
            + "re-parses the same text on every level. Ratios measured in this run:\n" + failures,
            failures.length() == 0);
    }

    /** {@code &#123;= } followed by {@code depth} opening brackets and nothing else. */
    private static String unclosedBrackets(int depth) {
        StringBuilder text = new StringBuilder("{= ");
        for (int i = 0; i < depth; i++) {
            text.append('[');
        }
        return text.toString();
    }

    /** {@code &#123;= [1, [1, [1, 1]]]}} nested {@code depth} levels deep. */
    private static String balancedArray(int depth) {
        return "{= " + nest(depth, "[1, ", "]") + "}";
    }

    /** {@code &#123;= [0 =&gt; [0 =&gt; [0 =&gt; 1]]]}} nested {@code depth} levels deep. */
    private static String keyedArray(int depth) {
        return "{= " + nest(depth, "[0 => ", "]") + "}";
    }

    private static String nest(int depth, String open, String close) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            text.append(open);
        }
        text.append('1');
        for (int i = 0; i < depth; i++) {
            text.append(close);
        }
        return text.toString();
    }

    /**
     * Nothing is asserted about the resulting tree here - the parser tests own
     * correctness - but a throw has to name the input that caused it, which a
     * bare stack trace out of the parser would not.
     */
    private void parse(Input input) {
        try {
            PsiFile psiFile = createPsiFile(input.psiName, input.text);
            ensureParsed(psiFile);
        } catch (Throwable t) {
            throw new AssertionError("parsing " + input.psiName + " threw " + t, t);
        }
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1
            ? sorted[middle]
            : (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    private interface Generator {
        String at(int depth);
    }

    private static final class Shape {
        final String name;
        final String sketch;
        final int shallowDepth;
        final int deepDepth;
        final Generator generator;

        Shape(String name, String sketch, int shallowDepth, int deepDepth, Generator generator) {
            this.name = name;
            this.sketch = sketch;
            this.shallowDepth = shallowDepth;
            this.deepDepth = deepDepth;
            this.generator = generator;
        }
    }

    private static final class Input {
        final Shape shape;
        final int depth;
        final String text;
        final String psiName;

        Input(Shape shape, int depth) {
            this.shape = shape;
            this.depth = depth;
            this.text = shape.generator.at(depth);
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < shape.name.length(); i++) {
                char c = shape.name.charAt(i);
                name.append(Character.isLetterOrDigit(c) ? c : '_');
            }
            this.psiName = name + "_" + depth;
        }
    }
}
