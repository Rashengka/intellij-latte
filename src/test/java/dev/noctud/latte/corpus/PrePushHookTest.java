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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code tools/git-hooks/pre-push}: the corpus gate for a push to {@code main} that did not go
 * through {@code tools/advance-main.sh}.
 *
 * <p>Each case is a real {@code git push} into a bare repository made up here, with the hooks
 * directory of this repository switched on for that one command - so what is tested is the hook as
 * git runs it, with the input git gives it, and not a script called by hand.
 */
public class PrePushHookTest {

    private static final Path HOOKS = Paths.get("tools/git-hooks").toAbsolutePath();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path origin;

    private Path work;

    private Path stamps;

    private Path corpus;

    private String next;

    @Before
    public void setUp() throws Exception {
        origin = folder.newFolder("origin.git").toPath();
        work = folder.newFolder("work").toPath();
        stamps = folder.newFolder("stamps").toPath();
        corpus = folder.newFolder("corpus").toPath();

        run(origin, List.of("git", "init", "-q", "--bare"));
        git("init", "-q", "-b", "work");
        write("src/main/Plugin.java", "class Plugin {}\n");
        String base = commit("base");
        git("remote", "add", "origin", origin.toString());
        git("push", "-q", "origin", base + ":refs/heads/main");
        write("src/main/Plugin.java", "class Plugin { int changed; }\n");
        next = commit("change the plugin");
    }

    /** The counterweight: a hook that refuses every push would pass both cases below. */
    @Test
    public void aPushOfAnyOtherBranchIsLeftAlone() throws Exception {
        Result result = pushWithHook("work:refs/heads/work");

        Assert.assertEquals(result.output, 0, result.exit);
    }

    @Test
    public void aPushToMainTheCorpusHasNotRunOverIsStopped() throws Exception {
        Result result = pushWithHook(next + ":refs/heads/main");

        Assert.assertNotEquals("the push should have been stopped:\n" + result.output, 0, result.exit);
        Assert.assertTrue(result.output, result.output.contains("corpus gate"));
        Assert.assertNotEquals(next, run(origin, List.of("git", "rev-parse", "refs/heads/main")).output.trim());
    }

    @Test
    public void aPushToMainTheCorpusHasRunOverGoesThrough() throws Exception {
        new CorpusStamp(next, false, Instant.now(), 100, 10, 0, 0, 0, 0, "").writeTo(stamps, CorpusStamp.idOf(corpus));

        Result result = pushWithHook(next + ":refs/heads/main");

        Assert.assertEquals(result.output, 0, result.exit);
        Assert.assertEquals(next, run(origin, List.of("git", "rev-parse", "refs/heads/main")).output.trim());
    }

    private Result pushWithHook(String refspec) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
            "git", "-c", "core.hooksPath=" + HOOKS, "push", "origin", refspec)
            .directory(work.toFile())
            .redirectErrorStream(true);
        builder.environment().put("LATTE_CORPORA", corpus.toString());
        builder.environment().put(CorpusStamp.STAMP_DIR, stamps.toString());
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
        Result result = run(work, command);
        Assert.assertEquals(String.join(" ", command) + "\n" + result.output, 0, result.exit);
        return result.output.trim();
    }

    private static Result run(Path directory, List<String> command) throws Exception {
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
