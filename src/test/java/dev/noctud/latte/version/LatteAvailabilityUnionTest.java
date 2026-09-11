package dev.noctud.latte.version;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * Two rows about one name, merged where they disagree about the patch.
 *
 * <p>{@link LatteLanguageReferenceTest} reads the real tables, and no name in them has two rows
 * naming different patches of one line, so the rule for that case is exercised by nothing there.
 * The rule is that the earlier patch wins - withholding is what produces a report - and "earlier"
 * has to be counted rather than spelled: 3.0.9 comes before 3.0.16, which comparing the text gets
 * backwards.
 */
public class LatteAvailabilityUnionTest {

    private static final List<String> LINES = List.of("2.11", "3.0", "3.1");

    @Test
    public void theEarlierPatchWinsWhicheverRowComesFirst() {
        LatteAvailability later = LatteAvailability.inLines(Map.of("3.0", "3.0.16"));
        LatteAvailability earlier = LatteAvailability.inLines(Map.of("3.0", "3.0.9"));

        for (LatteAvailability union : List.of(later.or(earlier), earlier.or(later))) {
            Assert.assertTrue(union.covers(patch(3, 0, 9), LINES));
            Assert.assertFalse(union.covers(patch(3, 0, 8), LINES));
        }
    }

    @Test
    public void aWholeLineBeatsAnyPatch() {
        LatteAvailability patchOnly = LatteAvailability.inLines(Map.of("3.0", "3.0.16"));
        LatteAvailability wholeLine = LatteAvailability.inLines(Map.of("3.0", LatteAvailability.WHOLE_LINE));

        Assert.assertTrue(patchOnly.or(wholeLine).covers(patch(3, 0, 0), LINES));
        Assert.assertTrue(wholeLine.or(patchOnly).covers(patch(3, 0, 0), LINES));
    }

    private static LatteVersion patch(int major, int minor, int patch) {
        return LatteVersion.of(major, minor, patch, LatteVersionSource.LOCK_FILE);
    }
}
