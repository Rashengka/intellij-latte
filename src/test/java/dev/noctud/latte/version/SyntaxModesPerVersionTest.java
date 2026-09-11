package dev.noctud.latte.version;

import org.junit.Assert;
import org.junit.Test;

/**
 * Which arguments {@code {syntax}} takes, read from the table that says so.
 *
 * <p>The accepted modes changed inside a line: {@code latte} is taken in 2.11 and in 3.0.0 and
 * 3.0.1, and refused from 3.0.2; {@code single} arrives in 3.0.24. The reference tables have said
 * so for as long as they have existed - the {@code {syntax}} argument table is headed
 * {@code | Argument | 2.11 | 3.0.0-3.0.1 | 3.0.2-3.0.23 | 3.0.24+ | 3.1 |} - but nothing could read
 * a header with a range in it, so the whole table was skipped and the plugin carried the union of
 * every mode as a constant in Java instead.
 *
 * <p>The union was the right answer while that was all there was: it reports nothing, and a report
 * about a correct template costs more than a missing one. It is the wrong answer now that the
 * table can be read.
 */
public class SyntaxModesPerVersionTest {

    @Test
    public void theModesEveryVersionTakes() {
        for (LatteVersion version : new LatteVersion[]{patch(2, 11, 7), patch(3, 0, 1), patch(3, 1, 6)}) {
            Assert.assertTrue(version + " takes off", exists("off", version));
            Assert.assertTrue(version + " takes double", exists("double", version));
        }
    }

    @Test
    public void latteIsTakenUntilItIsDropped() {
        Assert.assertTrue(exists("latte", patch(2, 11, 7)));
        Assert.assertTrue(exists("latte", patch(3, 0, 0)));
        Assert.assertTrue(exists("latte", patch(3, 0, 1)));
        Assert.assertFalse(exists("latte", patch(3, 0, 2)));
        Assert.assertFalse(exists("latte", patch(3, 1, 6)));
    }

    @Test
    public void singleArrivesInTheMiddleOfALine() {
        Assert.assertFalse(exists("single", patch(2, 11, 7)));
        Assert.assertFalse(exists("single", patch(3, 0, 23)));
        Assert.assertTrue(exists("single", patch(3, 0, 24)));
        Assert.assertTrue(exists("single", patch(3, 1, 6)));
    }

    /**
     * A mode the table never names is not one, whatever the version.
     *
     * <p>This is also the one test that proves the table is being read at all. When it cannot be -
     * a header written in a shape the reader does not take, a file that failed to load - the map is
     * empty and everything is allowed, which is the right way to fail and an invisible one. With
     * the map empty this test is the only one here that goes red, so it is what stands between a
     * check that is quiet and a check that has stopped.
     */
    @Test
    public void aModeTheTableDoesNotNameIsNotAMode() {
        Assert.assertFalse(exists("triple", patch(3, 1, 6)));
        Assert.assertFalse(exists("", patch(3, 1, 6)));
        Assert.assertFalse(exists("Off", patch(3, 1, 6)));
    }

    /**
     * The two silences. A project whose version could not be established is told nothing, and so
     * is one known only to a line the table splits - the table says {@code latte} went in the
     * middle of 3.0 and a project on "3.0" could be either side of that.
     */
    @Test
    public void whatCannotBePlacedIsNotReported() {
        Assert.assertTrue(exists("latte", LatteVersion.undetermined()));
        Assert.assertTrue(exists("single", LatteVersion.undetermined()));
        Assert.assertTrue(exists("latte", line(3, 0)));
        Assert.assertTrue(exists("single", line(3, 0)));
    }

    /** But a line the table does not split answers plainly. */
    @Test
    public void aLineTheTableDoesNotSplitAnswersPlainly() {
        Assert.assertTrue(exists("latte", line(2, 11)));
        Assert.assertFalse(exists("single", line(2, 11)));
        Assert.assertFalse(exists("latte", line(3, 1)));
        Assert.assertTrue(exists("single", line(3, 1)));
    }

    private static boolean exists(String mode, LatteVersion version) {
        return LatteLanguageReference.getInstance().syntaxModeExists(mode, version);
    }

    private static LatteVersion patch(int major, int minor, int patch) {
        return LatteVersion.of(major, minor, patch, LatteVersionSource.LOCK_FILE);
    }

    private static LatteVersion line(int major, int minor) {
        return LatteVersion.of(major, minor, null, LatteVersionSource.CONSTRAINT);
    }
}
