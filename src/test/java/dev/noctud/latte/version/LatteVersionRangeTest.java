package dev.noctud.latte.version;

import org.junit.Assert;
import org.junit.Test;

/**
 * A column of a reference table, read as the versions it stands for.
 *
 * <p>The tag, filter and function tables head their columns with a minor line - {@code 2.11},
 * {@code 3.1} - and that is all the plugin has ever been able to read. The {@code {syntax}}
 * argument table needs more, because what it describes changed inside a line: {@code latte} is
 * accepted in 3.0.0 and 3.0.1 and refused from 3.0.2 on. Its header says so already
 * ({@code | Argument | 2.11 | 3.0.0-3.0.1 | 3.0.2-3.0.23 | 3.0.24+ | 3.1 |}) and the reader
 * skipped the whole table for want of understanding it.
 *
 * <p>A range is asked only about a version that is known to a patch, or known to a line. Asked
 * about a line where the range covers part of it, the answer is yes: the plugin says nothing about
 * what it cannot place, and the line is all it was told.
 */
public class LatteVersionRangeTest {

    @Test
    public void aWholeLineIsEveryVersionInIt() {
        LatteVersionRange line = LatteVersionRange.parse("2.11");
        Assert.assertNotNull(line);
        Assert.assertTrue(line.contains(version(2, 11, 0)));
        Assert.assertTrue(line.contains(version(2, 11, 7)));
        Assert.assertFalse(line.contains(version(3, 0, 0)));
        Assert.assertFalse(line.contains(version(2, 10, 9)));
    }

    @Test
    public void aClosedRangeCoversBothItsEnds() {
        LatteVersionRange range = LatteVersionRange.parse("3.0.0-3.0.1");
        Assert.assertNotNull(range);
        Assert.assertTrue(range.contains(version(3, 0, 0)));
        Assert.assertTrue(range.contains(version(3, 0, 1)));
        Assert.assertFalse(range.contains(version(3, 0, 2)));
        Assert.assertFalse(range.contains(version(2, 11, 7)));
    }

    @Test
    public void anOpenRangeRunsToTheEndOfItsLine() {
        LatteVersionRange range = LatteVersionRange.parse("3.0.24+");
        Assert.assertNotNull(range);
        Assert.assertTrue(range.contains(version(3, 0, 24)));
        Assert.assertTrue(range.contains(version(3, 0, 99)));
        Assert.assertFalse(range.contains(version(3, 0, 23)));
        Assert.assertFalse(range.contains(version(3, 1, 0)));
    }

    /** A version known only to its line is placed when the range holds that whole line. */
    @Test
    public void aLineWithoutAPatchIsPlacedOnlyWhenTheWholeLineFits() {
        Assert.assertTrue(LatteVersionRange.parse("2.11").contains(lineOnly(2, 11)));
        Assert.assertFalse(LatteVersionRange.parse("3.1").contains(lineOnly(2, 11)));
    }

    /**
     * And a range that holds part of a line says nothing about a version known only to that line -
     * neither yes nor no, so the caller can tell it apart from a plain no.
     */
    @Test
    public void aPartOfALineCannotPlaceAVersionKnownOnlyToItsLine() {
        Assert.assertFalse(LatteVersionRange.parse("3.0.24+").contains(lineOnly(3, 0)));
        Assert.assertTrue(LatteVersionRange.parse("3.0.24+").touches(lineOnly(3, 0)));
        Assert.assertFalse(LatteVersionRange.parse("3.0.24+").touches(lineOnly(3, 1)));
        Assert.assertTrue(LatteVersionRange.parse("2.11").touches(lineOnly(2, 11)));
    }

    @Test
    public void whatIsNotARangeIsNotRead() {
        Assert.assertNull(LatteVersionRange.parse("yes"));
        Assert.assertNull(LatteVersionRange.parse("Argument"));
        Assert.assertNull(LatteVersionRange.parse(""));
        Assert.assertNull(LatteVersionRange.parse("3.x"));
    }

    /**
     * A range has to lie inside one minor line, and one written across two is not read at all.
     *
     * <p>It is the shape a table gets when somebody writes the boundary from memory, and the cost
     * of taking it would be silent: a column that is not a range makes the whole header
     * unreadable, the table is skipped, and everything in it becomes allowed everywhere. Refusing
     * to read it is the same outcome, but it is the outcome the reader can see coming - and this
     * test is where the boundary is written down rather than left to be rediscovered.
     */
    @Test
    public void aRangeAcrossTwoLinesIsNotARange() {
        Assert.assertNull(LatteVersionRange.parse("3.0.5-3.1.2"));
        Assert.assertNull(LatteVersionRange.parse("2.11.7-3.0.0"));
        Assert.assertNotNull(LatteVersionRange.parse("3.0.5-3.0.9"));
    }

    /** And a backwards one is not read either, whatever it was meant to say. */
    @Test
    public void aRangeThatEndsBeforeItBeginsIsNotARange() {
        Assert.assertNull(LatteVersionRange.parse("3.0.9-3.0.5"));
    }

    /**
     * The edge of the rule above. A range whose two ends are the same patch is one version, not a
     * backwards range - refusing it as well would pass every test above and lose that column.
     */
    @Test
    public void aRangeOfOnePatchIsThatPatch() {
        LatteVersionRange range = LatteVersionRange.parse("3.0.5-3.0.5");
        Assert.assertNotNull(range);
        Assert.assertTrue(range.contains(version(3, 0, 5)));
        Assert.assertFalse(range.contains(version(3, 0, 4)));
        Assert.assertFalse(range.contains(version(3, 0, 6)));
    }

    private static LatteVersion version(int major, int minor, int patch) {
        return LatteVersion.of(major, minor, patch, LatteVersionSource.LOCK_FILE);
    }

    private static LatteVersion lineOnly(int major, int minor) {
        return LatteVersion.of(major, minor, null, LatteVersionSource.CONSTRAINT);
    }
}
