package dev.noctud.latte.corpus;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code tools/corpus-gate.sh}, the check between a change to the plugin and {@code main}.
 *
 * <p>It runs against a repository and stamps made up here, never against a real corpus: what is
 * under test is the decision, and every case below is one reason it has to say no. The first case
 * is the counterweight - a gate that says no to everything would pass all the others.
 *
 * <p>The stamps are written by {@link CorpusStamp}, the same class the corpus test writes them
 * with, so this also holds the two readers of the file format to one another.
 */
public class CorpusGateScriptTest {

    private static final Path SCRIPT = Paths.get("tools/corpus-gate.sh").toAbsolutePath();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path repo;

    private Path stamps;

    private Path first;

    private Path second;

    private String base;

    @Before
    public void setUp() throws Exception {
        repo = folder.newFolder("repo").toPath();
        stamps = folder.newFolder("stamps").toPath();
        first = folder.newFolder("corpus-one").toPath();
        second = folder.newFolder("corpus-two").toPath();
        git("init", "-q");
        write("src/main/Plugin.java", "class Plugin {}\n");
        write("docs/latte/reference-tags.md", "| tag |\n");
        write("README.md", "readme\n");
        base = commit("base");
    }

    @Test
    public void freshStampsFromWholeCleanRunsLetMainMove() throws Exception {
        stamp(first, clean(base));
        stamp(second, clean(base));

        assertPasses(gate(base));
    }

    @Test
    public void aChangeToThePluginAfterTheStampStopsIt() throws Exception {
        stamp(first, clean(base));
        stamp(second, clean(base));
        write("src/main/Plugin.java", "class Plugin { int changed; }\n");

        assertStops(gate(commit("change the plugin")), "src/main/Plugin.java");
    }

    /** The reference tables ship inside the plugin and decide what it reports. */
    @Test
    public void aChangeToTheReferenceTablesStopsItToo() throws Exception {
        stamp(first, clean(base));
        stamp(second, clean(base));
        write("docs/latte/reference-tags.md", "| tag | 3.1 |\n");

        assertStops(gate(commit("change a table")), "docs/latte/reference-tags.md");
    }

    @Test
    public void aChangeOutsideThePluginDoesNot() throws Exception {
        stamp(first, clean(base));
        stamp(second, clean(base));
        write("README.md", "readme, changed\n");

        assertPasses(gate(commit("change the readme")));
    }

    @Test
    public void aCorpusWithoutAStampStopsIt() throws Exception {
        stamp(first, clean(base));

        assertStops(gate(base), "no stamp");
    }

    /** The corpus changes on its own, so a stamp goes stale even when the plugin does not change. */
    @Test
    public void aStampOlderThanAWeekStopsIt() throws Exception {
        stamp(first, clean(base));
        stamp(second, new CorpusStamp(base, false, Instant.now().minus(Duration.ofDays(8)), 100, 10, 0, 0, 0, 0, ""));

        assertStops(gate(base), "days old");
    }

    @Test
    public void aReportOfThePluginsOwnStopsIt() throws Exception {
        stamp(first, clean(base));
        stamp(second, new CorpusStamp(base, false, Instant.now(), 100, 10, 1, 0, 0, 0, ""));

        assertStops(gate(base), "plugin=1");
    }

    @Test
    public void aShapeNobodyHasPlacedStopsIt() throws Exception {
        stamp(first, clean(base));
        stamp(second, new CorpusStamp(base, false, Instant.now(), 100, 10, 0, 2, 0, 0, ""));

        assertStops(gate(base), "unclassified=2");
    }

    @Test
    public void aTemplateThatCouldNotBeReadStopsIt() throws Exception {
        stamp(first, clean(base));
        stamp(second, new CorpusStamp(base, false, Instant.now(), 100, 10, 0, 0, 1, 0, ""));

        assertStops(gate(base), "unreadable=1");
    }

    @Test
    public void aSampleIsNotTheCorpus() throws Exception {
        stamp(first, clean(base));
        stamp(second, new CorpusStamp(base, false, Instant.now(), 400, 10, 0, 0, 0, 400, ""));

        assertStops(gate(base), "limit=400");
    }

    @Test
    public void aRunUnderAForcedLatteLineIsNotTheCorpus() throws Exception {
        stamp(first, clean(base));
        stamp(second, new CorpusStamp(base, false, Instant.now(), 100, 10, 0, 0, 0, 0, "3.1"));

        assertStops(gate(base), "forced");
    }

    @Test
    public void aStampTakenOverUncommittedChangesStopsIt() throws Exception {
        stamp(first, clean(base));
        stamp(second, new CorpusStamp(base, true, Instant.now(), 100, 10, 0, 0, 0, 0, ""));

        assertStops(gate(base), "uncommitted");
    }

    @Test
    public void aStampOnACommitThisHistoryDoesNotHaveStopsIt() throws Exception {
        stamp(first, clean(base));
        stamp(second, clean("0123456789abcdef0123456789abcdef01234567"));

        assertStops(gate(base), "not an ancestor");
    }

    /** A gate over nothing must not answer yes: that is the one mistake a summary of checks makes. */
    @Test
    public void namingNoCorpusIsNotAPass() throws Exception {
        assertStops(run(List.of(), base), "LATTE_CORPORA");
    }

    private static CorpusStamp clean(String commit) {
        return new CorpusStamp(commit, false, Instant.now(), 100, 10, 0, 0, 0, 0, "");
    }

    private void stamp(Path corpus, CorpusStamp stamp) throws IOException {
        stamp.writeTo(stamps, CorpusStamp.idOf(corpus));
    }

    private Result gate(String target) throws Exception {
        return run(List.of(first, second), target);
    }

    private Result run(List<Path> corpora, String target) throws Exception {
        List<String> names = new ArrayList<>();
        for (Path corpus : corpora) {
            names.add(corpus.toString());
        }
        ProcessBuilder builder = new ProcessBuilder("bash", SCRIPT.toString(), target)
            .directory(repo.toFile())
            .redirectErrorStream(true);
        builder.environment().put("LATTE_CORPORA", String.join(":", names));
        builder.environment().put(CorpusStamp.STAMP_DIR, stamps.toString());
        return new Result(builder.start());
    }

    private static void assertPasses(Result result) {
        Assert.assertEquals("the gate should let this through:\n" + result.output, 0, result.exit);
    }

    private static void assertStops(Result result, String reason) {
        Assert.assertNotEquals("the gate should have stopped this:\n" + result.output, 0, result.exit);
        Assert.assertTrue("the gate stopped, but not for \"" + reason + "\":\n" + result.output,
            result.output.contains(reason));
    }

    private void write(String path, String content) throws IOException {
        Path file = repo.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private String commit(String message) throws Exception {
        git("add", "-A");
        git("commit", "-q", "-m", message);
        return git("rev-parse", "HEAD");
    }

    /** Git with identity and hooks pinned here, so nothing of the machine it runs on takes part. */
    private String git(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(
            "git", "-c", "user.name=test", "-c", "user.email=test@example.invalid",
            "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null"));
        command.addAll(Arrays.asList(args));
        Result result = new Result(new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start());
        Assert.assertEquals(String.join(" ", command) + "\n" + result.output, 0, result.exit);
        return result.output.trim();
    }

    private static final class Result {

        final int exit;

        final String output;

        Result(Process process) throws Exception {
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            exit = process.waitFor();
        }
    }
}
