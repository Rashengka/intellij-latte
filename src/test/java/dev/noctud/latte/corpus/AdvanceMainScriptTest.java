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
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code tools/advance-main.sh}, the only way {@code main} is meant to move.
 *
 * <p>Everything happens in repositories made up here: a bare one standing in for {@code origin}, a
 * clone to move it from, and a stand-in for {@code gh} that answers what the CI run concluded. The
 * first case is the counterweight - a script that refuses everything would pass all the others -
 * and every other case is one reason it has to leave {@code main} where it is.
 */
public class AdvanceMainScriptTest {

    private static final Path SCRIPT = Paths.get("tools/advance-main.sh").toAbsolutePath();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path origin;

    private Path work;

    private Path bin;

    private Path stamps;

    private Path corpus;

    private String base;

    @Before
    public void setUp() throws Exception {
        origin = folder.newFolder("origin.git").toPath();
        work = folder.newFolder("work").toPath();
        bin = folder.newFolder("bin").toPath();
        stamps = folder.newFolder("stamps").toPath();
        corpus = folder.newFolder("corpus").toPath();

        run(origin, "git", "init", "-q", "--bare");
        git("init", "-q", "-b", "work");
        write("src/main/Plugin.java", "class Plugin {}\n");
        base = commit("base");
        git("remote", "add", "origin", origin.toString());
        git("push", "-q", "origin", base + ":refs/heads/main");
        git("fetch", "-q", "origin");

        // Stands in for the GitHub CLI: answers the conclusion of the run for whatever it is asked.
        Path gh = bin.resolve("gh");
        Files.writeString(gh, "#!/bin/sh\nprintf '%s\\n' \"$FAKE_CI_CONCLUSION\"\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    @Test
    public void mainMovesAndIsPushedWhenEverythingHolds() throws Exception {
        String next = commitAChange();
        stampCleanly(next);

        Result result = advance(next, "success");

        Assert.assertEquals(result.output, 0, result.exit);
        Assert.assertEquals(next, git("rev-parse", "refs/heads/main"));
        Assert.assertEquals(next, run(origin, "git", "rev-parse", "refs/heads/main").output.trim());
    }

    @Test
    public void withoutAPassingCorpusGateMainStays() throws Exception {
        String next = commitAChange();

        assertRefused(advance(next, "success"), "corpus gate");
    }

    @Test
    public void aBuildThatDidNotSucceedKeepsMainWhereItIs() throws Exception {
        String next = commitAChange();
        stampCleanly(next);

        assertRefused(advance(next, "failure"), "CI");
    }

    @Test
    public void aBuildThatHasNotRunKeepsMainWhereItIsToo() throws Exception {
        String next = commitAChange();
        stampCleanly(next);

        assertRefused(advance(next, ""), "CI");
    }

    /** Somebody else moved main in the meantime; replaying on top of that is a decision, not a step. */
    @Test
    public void whatIsNotAFastForwardIsRefused() throws Exception {
        String next = commitAChange();
        stampCleanly(next);
        git("checkout", "-q", "-b", "elsewhere", base);
        write("README.md", "elsewhere\n");
        git("push", "-q", "origin", commit("elsewhere") + ":refs/heads/main");
        git("checkout", "-q", "work");

        assertRefused(advance(next, "success"), "fast-forward");
    }

    /** A rebase drops a merge and replays its commits as new ones, so a merge in the range stops it. */
    @Test
    public void aMergeCommitInTheRangeIsRefused() throws Exception {
        git("checkout", "-q", "-b", "side", base);
        write("src/main/Side.java", "class Side {}\n");
        commit("side");
        git("checkout", "-q", "work");
        write("src/main/Plugin.java", "class Plugin { int a; }\n");
        commit("work");
        git("merge", "-q", "--no-ff", "--no-edit", "side");
        String merged = git("rev-parse", "HEAD");
        stampCleanly(merged);

        assertRefused(advance(merged, "success"), "merge");
    }

    /** Moving a branch checked out somewhere would leave that working tree showing the change reversed. */
    @Test
    public void mainCheckedOutInAWorkingTreeIsNotMovedUnderIt() throws Exception {
        String next = commitAChange();
        stampCleanly(next);
        git("branch", "-q", "-f", "main", base);
        git("checkout", "-q", "main");

        assertRefused(advance(next, "success"), "checked out");
    }

    private void assertRefused(Result result, String reason) throws Exception {
        Assert.assertNotEquals("main should have stayed:\n" + result.output, 0, result.exit);
        Assert.assertTrue("refused, but not for \"" + reason + "\":\n" + result.output, result.output.contains(reason));
        String pushed = run(origin, "git", "rev-parse", "refs/heads/main").output.trim();
        Assert.assertNotEquals("origin/main moved even though the script refused", git("rev-parse", "work"), pushed);
    }

    private String commitAChange() throws Exception {
        write("src/main/Plugin.java", "class Plugin { int changed; }\n");
        return commit("change the plugin");
    }

    private void stampCleanly(String commit) throws IOException {
        new CorpusStamp(commit, false, Instant.now(), 100, 10, 0, 0, 0, 0, "").writeTo(stamps, CorpusStamp.idOf(corpus));
    }

    private Result advance(String commit, String ciConclusion) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("bash", SCRIPT.toString(), commit)
            .directory(work.toFile())
            .redirectErrorStream(true);
        builder.environment().put("PATH", bin + ":" + System.getenv("PATH"));
        builder.environment().put("FAKE_CI_CONCLUSION", ciConclusion);
        builder.environment().put("LATTE_CORPORA", corpus.toString());
        builder.environment().put(CorpusStamp.STAMP_DIR, stamps.toString());
        builder.environment().put("GIT_CONFIG_COUNT", "4");
        builder.environment().put("GIT_CONFIG_KEY_0", "user.name");
        builder.environment().put("GIT_CONFIG_VALUE_0", "test");
        builder.environment().put("GIT_CONFIG_KEY_1", "user.email");
        builder.environment().put("GIT_CONFIG_VALUE_1", "test@example.invalid");
        builder.environment().put("GIT_CONFIG_KEY_2", "core.hooksPath");
        builder.environment().put("GIT_CONFIG_VALUE_2", "/dev/null");
        builder.environment().put("GIT_CONFIG_KEY_3", "commit.gpgsign");
        builder.environment().put("GIT_CONFIG_VALUE_3", "false");
        return new Result(builder.start());
    }

    private void write(String path, String content) throws IOException {
        Path file = work.resolve(path);
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
        Result result = run(work, command.toArray(new String[0]));
        Assert.assertEquals(String.join(" ", command) + "\n" + result.output, 0, result.exit);
        return result.output.trim();
    }

    private static Result run(Path directory, String... command) throws Exception {
        return new Result(new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start());
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
