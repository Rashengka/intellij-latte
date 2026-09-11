package dev.noctud.latte.corpus;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.LatteLanguage;
import dev.noctud.latte.settings.LatteSettings;
import dev.noctud.latte.version.LatteVersion;
import dev.noctud.latte.version.LatteVersionResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.RunWith;

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
 *
 * <p>Without {@code LATTE_CORPUS_DIR} the measurement is reported as skipped, not as passed, and a
 * run that measured leaves a {@link CorpusStamp} behind. The test still never fails on a count -
 * {@code tools/corpus-gate.sh} reads the stamp and is where a count decides anything.
 */
@RunWith(JUnit38AssumeSupportRunner.class)
public class CorpusInspectionTest extends BasePlatformTestCase {

    private static final String DEFAULT_REPORT = ".ai/corpus-inspection-report.txt";

    /** Counts the templates measured, so that each gets a file name of its own. See below. */
    private int measured = 0;

    /**
     * How many inspections the run actually switched on. It is printed because a run that switched
     * on none reports nothing, and "nothing" is also what a clean corpus looks like.
     */
    private static int enabledInspections;

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
        Path root = CorpusStamp.corpusOrSkip();
        assertTrue("LATTE_CORPUS_DIR is not a directory: " + root, Files.isDirectory(root));

        String forced = System.getenv("LATTE_CORPUS_VERSION");
        LatteVersion version = versionOf(root);
        String line = forced != null && !forced.trim().isEmpty()
            ? forced.trim()
            : (version.isUndetermined() ? "" : version.line());
        LatteSettings.getInstance(getProject()).latteVersionOverride = line;

        myFixture.enableInspections(registeredLatteInspections());

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
        stamp(root, files.size(), reports, unreadable, limit, forced, byShape);
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

    /**
     * A quoted punctuation mark is part of the message, not a name, and keeping it is what keeps
     * two languages out of one row. The CSS parser's {@code ':' expected} and a Latte parser's
     * {@code '}' expected} collapse into the same shape otherwise, and a report of ours would then
     * be filed under somebody else's origin and never looked at again.
     */
    public void testAQuotedPunctuationMarkIsPartOfTheMessage() {
        assertEquals("':' expected", anonymise("':' expected"));
        assertEquals("';' expected", anonymise("';' expected"));
        assertEquals("'}' expected", anonymise("'}' expected"));
        assertEquals("Unexpected '#' here", anonymise("Unexpected '#' here"));
    }

    /** And a quoted name is still a name, however short. */
    public void testAQuotedNameIsStillHidden() {
        assertEquals("Method '…' not found", anonymise("Method 'x' not found"));
        assertEquals("Undefined variable '…'", anonymise("Undefined variable '1'"));
        assertEquals("Undefined class '…'", anonymise("Undefined class 'Ab'"));
    }

    /**
     * Each template is measured under a file name of its own.
     *
     * <p>Under one shared name the run died on template 1 695 of 2 847, and not because of that
     * template: a view provider is built for the language a file turns out to hold, and
     * {@code {contentType application/xml}} makes a Latte file hold XML where the one measured
     * before it held HTML. Asked to parse the second text into the provider built for the first,
     * the platform refuses - "refused to parse text with Language: XML". Reproduced in two lines
     * and written up in {@code .ai/plans/21-zmena-contenttype-v-otevrenem-souboru.md}, because
     * changing that tag in an open editor is the same sequence.
     *
     * <p>A fresh name gives each template the provider its own content type asks for, which is
     * what a corpus of separate files has in an IDE. The counter is what makes the name fresh;
     * the corpus path is not in it, because none of the corpus belongs in this repository.
     */
    private List<String> shapesReportedOn(String text) {
        myFixture.configureByText("corpus" + (++measured) + ".latte", text);
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

    /**
     * Keeps the shape of a report and drops the name in it. See the class comment.
     *
     * <p>A quoted single character that is not a letter or a digit stays as it is. Nothing in a
     * corpus is named {@code :} or {@code ;}, so there is nothing to protect - and collapsing them
     * made {@code ':' expected} and {@code ';' expected} into one shape that a Latte parser's own
     * {@code '}' expected} would fall into as well. Two reports from different languages sharing a
     * row is how a report of ours would come to be filed as somebody else's and never looked at.
     */
    static String anonymise(String description) {
        return replaceAll(replaceAll(description, QUOTED, null), FILE_NAME, "…");
    }

    /** What a quoted run is replaced by: itself when it is one punctuation mark, an ellipsis else. */
    private static String hidden(String quoted) {
        String inside = quoted.substring(1, quoted.length() - 1);

        return inside.length() == 1 && !Character.isLetterOrDigit(inside.charAt(0))
            ? quoted
            : quoted.charAt(0) + "…" + quoted.charAt(quoted.length() - 1);
    }

    /** {@code with} of null means each match decides for itself, through {@link #hidden}. */
    private static String replaceAll(String text, Pattern pattern, @Nullable String with) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder shape = new StringBuilder();
        while (matcher.find()) {
            String replacement = with == null ? hidden(matcher.group()) : with;
            matcher.appendReplacement(shape, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(shape);
        return shape.toString();
    }

    /**
     * Where a report comes from, so that the total stops being one number nobody can act on.
     *
     * 1 971 reports over 400 templates sounds like 1 971 problems and is nothing of the sort:
     * almost all of them say that a variable or a class could not be resolved, and nothing in
     * this fixture could have resolved them - it holds one template and no PHP at all. Counting
     * them together with a report about the language hides the second inside the first.
     *
     * The point of the split is the last two rows. {@link Origin#PLUGIN} has a right answer and
     * it is zero; anything unclassified is a shape nobody has looked at yet, which is exactly
     * what a new report is on the day it appears.
     */
    private enum Origin {
        /** The fixture has no PHP behind it and no sibling templates. Nothing here is about Latte. */
        RESOLUTION,
        /** The corpus project's own tags and filters, which the plugin is not configured for. */
        PROJECT,
        /** Not this plugin's message at all - the IDE reporting on the HTML it sees. */
        PLATFORM,
        /**
         * Ours, and right: the template is genuinely wrong and Latte refuses it too.
         *
         * <p>A corpus of real templates has some broken ones in it - this one carries a file with
         * "invalid" in its name - and until this row existed they had nowhere to go. Counting them
         * as unclassified made the number that means "nobody has looked at this" grow by things
         * somebody had looked at and settled, which is the one thing that number may not do.
         *
         * <p>Nothing goes here on a guess. Every shape below that lands in this row was run
         * against Latte itself, at both ends of the supported range, and refused by it.
         */
        TEMPLATE,
        /** Ours, and wrong. This one has a right answer and it is zero. */
        PLUGIN,
    }

    /**
     * Shape prefix to where it comes from. A prefix rather than the whole shape, because some
     * reports carry a count that varies - "(1 required)", "(2 required)".
     */
    private static final Map<String, Origin> ORIGINS = Map.ofEntries(
        Map.entry("Undefined variable", Origin.RESOLUTION),
        Map.entry("Variable '…' is probably undefined", Origin.RESOLUTION),
        Map.entry("Unused variable", Origin.RESOLUTION),
        Map.entry("Multiple definitions for variable", Origin.RESOLUTION),
        Map.entry("Rewrite default variable", Origin.RESOLUTION),
        Map.entry("Undefined class", Origin.RESOLUTION),
        Map.entry("Function '…' not found", Origin.RESOLUTION),
        Map.entry("Method '…' not found", Origin.RESOLUTION),
        Map.entry("File … is missing", Origin.RESOLUTION),
        Map.entry("Undefined latte filter", Origin.PROJECT),
        Map.entry("Unknown tag", Origin.PROJECT),
        Map.entry("Unknown attribute tag", Origin.PROJECT),
        Map.entry("Closing tag matches nothing", Origin.PLATFORM),

        // The IDE's own CSS and JavaScript, reported inside a style attribute or a script element.
        // They arrive because the plugin offers an HTML view of the template, so the injected
        // languages get inspected as well - which is .ai/plans/19-hlasky-z-html-pohledu.md, not a
        // Latte report. Measured shape by shape: what they name is real, invalid CSS.
        Map.entry("Term expected", Origin.PLATFORM),
        Map.entry("Property name expected", Origin.PLATFORM),
        Map.entry("':' expected", Origin.PLATFORM),
        Map.entry("';' expected", Origin.PLATFORM),
        Map.entry("Newline or semicolon expected", Origin.PLATFORM),
        Map.entry("Duplicate attribute", Origin.PLATFORM),
        Map.entry("There should be a space between attribute", Origin.PLATFORM),
        Map.entry("Start tag has wrong closing tag", Origin.PLATFORM),

        // Ours, and right about a template that is genuinely wrong. Each shape was chased to the
        // lines behind it and each of those was run against Latte: 2.11.7 answers "Filters are not
        // allowed in {php}" in those words, and 3.1.6 answers "Unexpected '|', expecting end of
        // tag". The {var} tags and the {varType} one were settled the same way in plans 24 and 26,
        // and the three tag-nesting ones sit in two files the corpus itself calls invalid.
        Map.entry("Filters are not allowed here", Origin.TEMPLATE),
        Map.entry("Tag {var} must contain definition operator", Origin.TEMPLATE),
        Map.entry("First value in {varType} tag must be type definition", Origin.TEMPLATE),
        Map.entry("Incomplete variable", Origin.TEMPLATE),
        Map.entry("Unexpected {/", Origin.TEMPLATE),
        Map.entry("Unclosed tag", Origin.TEMPLATE),
        Map.entry("Invalid argument supplied to", Origin.TEMPLATE),
        Map.entry("Filter '…' does not exist before Latte", Origin.TEMPLATE),
        // LatteTagSyntax: syntax both ends of the supported range refuse to compile.
        Map.entry("Missing argument in tag", Origin.TEMPLATE),
        Map.entry("Unclosed '", Origin.TEMPLATE),
        Map.entry("Closing '", Origin.TEMPLATE),
        Map.entry("No operand after", Origin.TEMPLATE),
        Map.entry("Operator '", Origin.TEMPLATE),
        Map.entry("Expression starts with", Origin.TEMPLATE),
        Map.entry("Filter after '|' has no name", Origin.TEMPLATE)
    );

    private static Origin originOf(String shape) {
        for (Map.Entry<String, Origin> known : ORIGINS.entrySet()) {
            if (shape.startsWith(known.getKey())) {
                return known.getValue();
            }
        }
        return null;
    }

    /**
     * The record that this run happened, for {@code tools/corpus-gate.sh}. Every run writes one and
     * the gate judges it, so a sample or a run under a forced line leaves a stamp that does not let
     * {@code main} move - the counts stay what they were, and the decision stays in one place.
     */
    private static void stamp(
        Path root, int files, int reports, int unreadable, int limit, @Nullable String forced, Map<String, Integer> byShape
    ) throws IOException {
        Path dir = CorpusStamp.directory();
        if (dir == null) {
            System.out.println("[corpus-inspection] no stamp written: this is not a git checkout");
            return;
        }
        int plugin = byShape.entrySet().stream()
            .filter(e -> originOf(e.getKey()) == Origin.PLUGIN).mapToInt(Map.Entry::getValue).sum();
        int unclassified = byShape.entrySet().stream()
            .filter(e -> originOf(e.getKey()) == null).mapToInt(Map.Entry::getValue).sum();
        CorpusStamp.now(files, reports, plugin, unclassified, unreadable, limit, forced == null ? "" : forced.trim())
            .writeTo(dir, CorpusStamp.idOf(root));
        System.out.println("[corpus-inspection] stamp written to " + dir);
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

        Map<Origin, Integer> byOrigin = new java.util.EnumMap<>(Origin.class);
        Map<String, Integer> unclassified = new TreeMap<>();
        for (Map.Entry<String, Integer> shape : byShape.entrySet()) {
            Origin origin = originOf(shape.getKey());
            if (origin == null) {
                unclassified.put(shape.getKey(), shape.getValue());
            } else {
                byOrigin.merge(origin, shape.getValue(), Integer::sum);
            }
        }
        for (Origin origin : Origin.values()) {
            out.append(String.format("%-12s %6d%n", origin.name().toLowerCase(), byOrigin.getOrDefault(origin, 0)));
        }
        out.append(String.format("%-12s %6d%n", "unclassified",
            unclassified.values().stream().mapToInt(Integer::intValue).sum()));
        if (!unclassified.isEmpty()) {
            out.append('\n').append("shapes nobody has placed yet - each one is a question:").append('\n');
            unclassified.forEach((shape, count) -> out.append(String.format("%6d  %s%n", count, shape)));
        }
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
        System.out.println("[corpus-inspection] inspections=" + enabledInspections
            + " latteVersion=" + version
            + " measuredAs=" + (measuredAs.isEmpty() ? "undetermined" : measuredAs)
            + " files=" + files
            + " filesWithReports=" + filesWithReports
            + " reports=" + reports
            + " distinctShapes=" + byShape.size()
            + " plugin=" + byShape.entrySet().stream()
                .filter(e -> originOf(e.getKey()) == Origin.PLUGIN).mapToInt(Map.Entry::getValue).sum()
            + " unclassified=" + byShape.entrySet().stream()
                .filter(e -> originOf(e.getKey()) == null).mapToInt(Map.Entry::getValue).sum()
            + " -> " + path);
    }

    /**
     * Every inspection the plugin registers for Latte, taken from the registration itself.
     *
     * <p>It used to be a list written out here, and a list written out here is a list that can be
     * missing one. A measurement over the corpus answers "the plugin reported nothing" the same way
     * whether an inspection is quiet or was never switched on - so the answer would look right
     * either way, which is the one thing a measurement may not do. Reading the registration removes
     * the choice rather than asking anybody to make it correctly each time.
     */
    @NotNull
    private static LocalInspectionTool[] registeredLatteInspections() {
        List<LocalInspectionTool> tools = new ArrayList<>();
        for (LocalInspectionEP ep : LocalInspectionEP.LOCAL_INSPECTION.getExtensionList()) {
            if (LatteLanguage.INSTANCE.getID().equals(ep.language)) {
                tools.add((LocalInspectionTool) ep.instantiateTool());
            }
        }
        assertFalse("the plugin registers no Latte inspection at all", tools.isEmpty());
        enabledInspections = tools.size();
        return tools.toArray(new LocalInspectionTool[0]);
    }

}
