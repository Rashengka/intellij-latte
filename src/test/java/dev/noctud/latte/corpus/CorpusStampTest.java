package dev.noctud.latte.corpus;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * The stamp a corpus run leaves behind, and what a run without a corpus says about it.
 *
 * <p>The corpus is not in this repository and CI never has it, so whether it was checked cannot be
 * read off a green build. The stamp is the record that it was: which commit, how long ago, and what
 * the run counted. {@code tools/corpus-gate.sh} decides from it whether {@code main} may move.
 */
public class CorpusStampTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aStampReadsBackAsItWasWritten() throws Exception {
        Path dir = folder.getRoot().toPath();
        CorpusStamp written = new CorpusStamp(
            "0123456789abcdef0123456789abcdef01234567", false, Instant.parse("2026-09-11T08:00:00Z"),
            2866, 24655, 0, 0, 0, 0, "");

        written.writeTo(dir, "abc");
        CorpusStamp read = CorpusStamp.readFrom(dir.resolve("abc.properties"));

        Assert.assertEquals(written.commit, read.commit);
        Assert.assertEquals(written.dirty, read.dirty);
        Assert.assertEquals(written.date, read.date);
        Assert.assertEquals(written.files, read.files);
        Assert.assertEquals(written.reports, read.reports);
        Assert.assertEquals(written.plugin, read.plugin);
        Assert.assertEquals(written.unclassified, read.unclassified);
        Assert.assertEquals(written.unreadable, read.unreadable);
        Assert.assertEquals(written.limit, read.limit);
        Assert.assertEquals(written.forcedVersion, read.forcedVersion);
    }

    /**
     * The stamp names its corpus without naming where it is: the path of somebody else's
     * repository has no place in a file a tool reads and prints. Two corpora still get two stamps.
     */
    @Test
    public void theIdTellsCorporaApartWithoutNamingThem() {
        String first = CorpusStamp.idOf(Paths.get("/somewhere/customer-one"));
        String second = CorpusStamp.idOf(Paths.get("/somewhere/customer-two"));

        Assert.assertNotEquals(first, second);
        Assert.assertEquals(first, CorpusStamp.idOf(Paths.get("/somewhere/customer-one")));
        Assert.assertFalse(first.contains("somewhere"));
        Assert.assertFalse(first.contains("customer"));
        Assert.assertTrue("a file name, not a path: " + first, first.matches("[0-9a-f]{16}"));
    }

    /** What a skipped run says: the newest stamp, so its age is in front of whoever reads the output. */
    @Test
    public void aRunWithoutACorpusNamesTheNewestStamp() throws Exception {
        Path dir = folder.getRoot().toPath();
        new CorpusStamp("1111111111111111111111111111111111111111", false, Instant.parse("2026-09-01T08:00:00Z"),
            10, 0, 0, 0, 0, 0, "").writeTo(dir, "older");
        new CorpusStamp("2222222222222222222222222222222222222222", false, Instant.parse("2026-09-10T08:00:00Z"),
            10, 0, 0, 0, 0, 0, "").writeTo(dir, "newer");

        String said = CorpusStamp.describeNewest(dir);

        Assert.assertTrue(said, said.contains("2026-09-10"));
        Assert.assertTrue(said, said.contains("2222222"));
        Assert.assertFalse(said, said.contains("1111111"));
    }

    /** And the absence of one is said out loud rather than left to be noticed. */
    @Test
    public void noStampAtAllIsSaidOutLoud() {
        String said = CorpusStamp.describeNewest(folder.getRoot().toPath().resolve("missing"));

        Assert.assertTrue(said, said.contains("no corpus stamp"));
    }
}
