package dev.noctud.latte.corpus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The record that a corpus run happened.
 *
 * <p>The corpus lives outside this repository and CI never has it, so a green build says nothing
 * about whether it was checked. A run over the corpus leaves one of these behind,
 * {@code tools/corpus-gate.sh} reads it before {@code main} moves, and a run without the corpus
 * names the newest one in the reason it was skipped.
 *
 * <p>It lives next to the repository's object store ({@code git rev-parse --git-common-dir}), never
 * in the working tree and never in git - it carries counts measured on code that is not ours. Every
 * worktree of a clone shares it; another clone has none, and the gate then stops instead of passing.
 * {@code LATTE_CORPUS_STAMP_DIR} points it elsewhere, which is what the tests use.
 *
 * <p>The file is plain {@code key=value} lines, because a shell script reads it as well.
 */
final class CorpusStamp {

    static final String CORPUS_DIR = "LATTE_CORPUS_DIR";

    static final String STAMP_DIR = "LATTE_CORPUS_STAMP_DIR";

    final String commit;

    /** Whether the plugin had uncommitted changes, in which case the commit is not what was measured. */
    final boolean dirty;

    final Instant date;

    final int files;

    final int reports;

    final int plugin;

    final int unclassified;

    final int unreadable;

    /** {@code LATTE_CORPUS_LIMIT}, or 0 for the whole corpus. */
    final int limit;

    /** {@code LATTE_CORPUS_VERSION}, or empty when the corpus was measured under its own Latte. */
    final String forcedVersion;

    CorpusStamp(
        @NotNull String commit, boolean dirty, @NotNull Instant date, int files, int reports, int plugin,
        int unclassified, int unreadable, int limit, @NotNull String forcedVersion
    ) {
        this.commit = commit;
        this.dirty = dirty;
        this.date = date;
        this.files = files;
        this.reports = reports;
        this.plugin = plugin;
        this.unclassified = unclassified;
        this.unreadable = unreadable;
        this.limit = limit;
        this.forcedVersion = forcedVersion;
    }

    /**
     * The corpus to run over, or a skip that says so.
     *
     * <p>A corpus test used to return here, and a test that returns is reported as passed - in CI on
     * every run, and locally whenever the variable did not reach the test JVM. A pass is also what a
     * clean corpus looks like, so a run that checked nothing could not be told from one that did.
     */
    static @NotNull Path corpusOrSkip() {
        String dir = System.getenv(CORPUS_DIR);
        if (dir == null || dir.trim().isEmpty()) {
            throw new AssumptionViolatedException(
                CORPUS_DIR + " is not set, so the corpus was not checked; " + describeNewest(directory()));
        }
        return Paths.get(dir.trim());
    }

    /** A stamp for a run that just ended, on the commit the working tree is at. */
    static @NotNull CorpusStamp now(
        int files, int reports, int plugin, int unclassified, int unreadable, int limit, @NotNull String forcedVersion
    ) {
        String head = git("rev-parse", "HEAD");
        String changes = git("status", "--porcelain", "--", "src/main", "docs/latte");
        return new CorpusStamp(
            head == null ? "unknown" : head,
            // Not knowing is not clean: a stamp git could not describe must not let main move.
            changes == null || !changes.isEmpty(),
            Instant.now(), files, reports, plugin, unclassified, unreadable, limit, forcedVersion);
    }

    /**
     * A name for the corpus that does not say where it is: the path of somebody else's repository
     * does not belong in a file that tools read and print. It is taken from the real path, symlinks
     * resolved, because that is what the shell side sees through {@code pwd -P}.
     */
    static @NotNull String idOf(@NotNull Path corpusRoot) {
        Path path;
        try {
            path = corpusRoot.toRealPath();
        } catch (IOException e) {
            path = corpusRoot.toAbsolutePath().normalize();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(path.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Where stamps are kept, or null outside a git checkout. */
    static @Nullable Path directory() {
        String forced = System.getenv(STAMP_DIR);
        if (forced != null && !forced.trim().isEmpty()) {
            return Paths.get(forced.trim());
        }
        String common = git("rev-parse", "--git-common-dir");
        return common == null ? null : Paths.get("").toAbsolutePath().resolve(common).normalize().resolve("corpus-stamps");
    }

    void writeTo(@NotNull Path dir, @NotNull String id) throws IOException {
        Files.createDirectories(dir);
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> field : fields().entrySet()) {
            out.append(field.getKey()).append('=').append(field.getValue()).append('\n');
        }
        Files.writeString(dir.resolve(id + ".properties"), out.toString(), StandardCharsets.UTF_8);
    }

    static @NotNull CorpusStamp readFrom(@NotNull Path file) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int equals = line.indexOf('=');
            if (equals > 0) {
                fields.put(line.substring(0, equals), line.substring(equals + 1));
            }
        }
        return new CorpusStamp(
            fields.getOrDefault("commit", ""),
            Boolean.parseBoolean(fields.get("dirty")),
            Instant.parse(fields.get("date")),
            number(fields, "files"),
            number(fields, "reports"),
            number(fields, "plugin"),
            number(fields, "unclassified"),
            number(fields, "unreadable"),
            number(fields, "limit"),
            fields.getOrDefault("forcedVersion", ""));
    }

    /** What a run without the corpus says about the last one that had it. */
    static @NotNull String describeNewest(@Nullable Path dir) {
        if (dir == null) {
            return "and there is no corpus stamp either, this not being a git checkout";
        }
        if (!Files.isDirectory(dir)) {
            return "no corpus stamp in " + dir;
        }
        CorpusStamp newest = null;
        try (Stream<Path> list = Files.list(dir)) {
            for (Path file : list.filter(path -> path.getFileName().toString().endsWith(".properties")).toList()) {
                try {
                    CorpusStamp stamp = readFrom(file);
                    if (newest == null || stamp.date.isAfter(newest.date)) {
                        newest = stamp;
                    }
                } catch (IOException | RuntimeException e) {
                    // A stamp that cannot be read is no stamp; the gate says so on its own.
                }
            }
        } catch (IOException e) {
            return "no corpus stamp readable in " + dir;
        }
        if (newest == null) {
            return "no corpus stamp in " + dir;
        }
        return "corpus last verified " + newest.date + " at " + newest.commit.substring(0, Math.min(7, newest.commit.length()));
    }

    private @NotNull Map<String, String> fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("commit", commit);
        fields.put("dirty", String.valueOf(dirty));
        fields.put("date", date.toString());
        // The same moment as a number, so the shell side can take its age without parsing a date.
        fields.put("epoch", String.valueOf(date.getEpochSecond()));
        fields.put("files", String.valueOf(files));
        fields.put("reports", String.valueOf(reports));
        fields.put("plugin", String.valueOf(plugin));
        fields.put("unclassified", String.valueOf(unclassified));
        fields.put("unreadable", String.valueOf(unreadable));
        fields.put("limit", String.valueOf(limit));
        fields.put("forcedVersion", forcedVersion);
        return fields;
    }

    private static int number(@NotNull Map<String, String> fields, @NotNull String key) {
        return Integer.parseInt(fields.getOrDefault(key, "0").trim());
    }

    private static @Nullable String git(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        try {
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String out;
            try (InputStream in = process.getInputStream()) {
                out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            return process.waitFor() == 0 ? out : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
