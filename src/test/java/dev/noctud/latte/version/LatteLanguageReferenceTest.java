package dev.noctud.latte.version;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The reference tables, read as the plugin reads them.
 *
 * Every fact asserted here is one a reader can check against docs/latte/reference-*.md by eye,
 * which is the point: the tables are the source, and a case that agreed with the parser but not
 * with the table would be worthless.
 */
public class LatteLanguageReferenceTest {

    private static LatteVersion version(int major, int minor, Integer patch) {
        return LatteVersion.of(major, minor, patch, LatteVersionSource.LOCK_FILE);
    }

    private static boolean tagCovers(String tag, LatteVersion version) {
        LatteLanguageReference reference = LatteLanguageReference.getInstance();
        return reference.availabilityOfTag(tag).covers(version, reference.getDocumentedLines());
    }

    private static boolean filterCovers(String filter, LatteVersion version) {
        LatteLanguageReference reference = LatteLanguageReference.getInstance();
        return reference.availabilityOfFilter(filter).covers(version, reference.getDocumentedLines());
    }

    private static boolean functionCovers(String function, LatteVersion version) {
        LatteLanguageReference reference = LatteLanguageReference.getInstance();
        return reference.availabilityOfFunction(function).covers(version, reference.getDocumentedLines());
    }

    @Test
    public void testTheLinesComeFromTheTableHeaderAndNotFromConstants() {
        assertEquals(List.of("2.11", "3.0", "3.1"), LatteLanguageReference.getInstance().getDocumentedLines());
    }

    /** {includeblock} is "yes | no | no": Latte 2 only, with no Latte 3 equivalent. */
    @Test
    public void testATagRemovedInLatte3IsCoveredOnlyByTheOlderLine() {
        assertTrue(tagCovers("includeblock", version(2, 11, 7)));
        assertFalse(tagCovers("includeblock", version(3, 0, 0)));
        assertFalse(tagCovers("includeblock", version(3, 1, 6)));
    }

    /** {foreach} is "yes" everywhere. */
    @Test
    public void testATagInEveryLineIsCoveredEverywhere() {
        assertTrue(tagCovers("foreach", version(2, 11, 7)));
        assertTrue(tagCovers("foreach", version(3, 1, 6)));
    }

    /** {?} is "no | no | no" - in the tables for history, in no version of the language. */
    @Test
    public void testARowThatIsNoEverywhereIsCoveredNowhere() {
        assertFalse(tagCovers("?", version(2, 11, 7)));
        assertFalse(tagCovers("?", version(3, 1, 6)));
    }

    /** breaklines, the lowercase alias, arrived in 2.11.1 - so 2.11.0 does not have it. */
    @Test
    public void testAPatchBoundaryIsRespectedWhenThePatchIsKnown() {
        assertFalse(filterCovers("breaklines", version(2, 11, 0)));
        assertTrue(filterCovers("breaklines", version(2, 11, 1)));
        assertTrue(filterCovers("breaklines", version(2, 11, 7)));
    }

    /**
     * A constraint in composer.json names a line and no patch. Withholding on a patch boundary
     * there would be a guess, and the guess would report something correct as unknown.
     */
    @Test
    public void testAPatchBoundaryIsNotAppliedWhenOnlyTheLineIsKnown() {
        assertTrue(filterCovers("breaklines", version(2, 11, null)));
    }

    /** Nothing is known about the project, so nothing that exists is withheld. */
    @Test
    public void testAnUndeterminedVersionIsCoveredByAnythingThatExists() {
        assertTrue(tagCovers("includeblock", LatteVersion.undetermined()));
        assertTrue(tagCovers("foreach", LatteVersion.undetermined()));
        assertFalse("what exists in no version cannot be in a project either",
            tagCovers("?", LatteVersion.undetermined()));
    }

    /**
     * A Latte released after these tables were written will have added things and removed almost
     * nothing, so it is answered for by the newest line documented. Refusing to recognise its tags
     * would report what is correct.
     */
    @Test
    public void testAVersionNewerThanTheTablesIsAnsweredByTheNewestLineTheyDescribe() {
        assertTrue(tagCovers("foreach", version(3, 2, 0)));
        assertFalse(tagCovers("includeblock", version(3, 2, 0)));
    }

    /** Below the floor the plugin does not claim to know the language, so it withholds nothing. */
    @Test
    public void testAVersionOlderThanTheTablesIsNotJudged() {
        assertTrue(tagCovers("includeblock", version(2, 10, 0)));
        assertTrue(tagCovers("foreach", version(2, 10, 0)));
    }

    /** An item the tables never mention is one the plugin has no grounds to withhold. */
    @Test
    public void testSomethingTheTablesDoNotMentionIsCoveredEverywhere() {
        assertTrue(tagCovers("aTagThisProjectInvented", version(2, 11, 7)));
        assertTrue(filterCovers("aFilterThisProjectInvented", version(3, 1, 6)));
    }

    /** Latte 2.11 matches filter names case-insensitively, and the registry looks them up so. */
    @Test
    public void testAFilterIsFoundWhateverCaseTheLookupUsed() {
        assertTrue(filterCovers("escapeurl", version(2, 11, 7)));
        assertTrue(filterCovers("ESCAPEURL", version(2, 11, 7)));
    }

    /** clamp() is "yes | yes | yes" - the function set changed only by addition. */
    @Test
    public void testAFunctionInEveryLineIsCoveredEverywhere() {
        assertTrue(functionCovers("clamp", version(2, 11, 7)));
        assertTrue(functionCovers("clamp", version(3, 1, 6)));
    }

    /**
     * group() is "no | 3.0.16 | yes". It is the case the third table was added for: the function
     * set is small enough that a missing one is conspicuous, and the only movement in it happens
     * mid-line.
     */
    @Test
    public void testAFunctionThatArrivedMidLineWaitsForItsPatch() {
        assertFalse(functionCovers("group", version(2, 11, 7)));
        assertFalse(functionCovers("group", version(3, 0, 15)));
        assertTrue(functionCovers("group", version(3, 0, 16)));
        assertTrue(functionCovers("group", version(3, 1, 0)));
    }

    /**
     * isLinkCurrent() comes from nette/application, so its row carries a provider where the core
     * table carries versions. Availability that depends on another package is not availability
     * this can judge, and the answer is therefore yes everywhere.
     */
    @Test
    public void testAFunctionFromAnotherPackageIsNotJudgedByTheLatteVersion() {
        assertTrue(functionCovers("isLinkCurrent", version(2, 11, 7)));
        assertTrue(functionCovers("isLinkCurrent", version(3, 1, 6)));
    }

    /**
     * {attr} the tag was dropped in Latte 3; n:attr the attribute was not. The registry knows one
     * entry serving both, so reading only the first row would withhold n:attr from every Latte 3
     * template that uses it - a report on correct code, which is the failure this whole round is
     * meant to avoid.
     */
    @Test
    public void testATagAndItsAttributeFormAreOneAnswer() {
        assertTrue(tagCovers("attr", version(2, 11, 7)));
        assertTrue(tagCovers("attr", version(3, 1, 6)));
        assertTrue(tagCovers("class", version(3, 1, 6)));
        assertTrue(tagCovers("ifcontent", version(3, 1, 6)));
        assertTrue(tagCovers("tag", version(3, 1, 6)));
    }

    /**
     * {snippet} left the engine in Latte 3 and is registered by nette/application instead, and the
     * table says so in a row naming both it and {snippetArea} at once. Missing that row would
     * withhold {snippet} from every Nette 3 project there is.
     */
    @Test
    public void testATagThatMovedToAnotherPackageIsStillAvailable() {
        assertTrue(tagCovers("snippet", version(2, 11, 7)));
        assertTrue(tagCovers("snippet", version(3, 1, 6)));
        assertTrue(tagCovers("snippetArea", version(3, 1, 6)));
    }

    /**
     * The {syntax} argument table is headed with ranges - "3.0.2-3.0.23", "3.0.24+" - and only two
     * of its columns are bare lines. Lining its rows up against those two would stamp "single" as
     * existing nowhere, and "single" is also a name a tag could have. A header this cannot read
     * stamps nothing.
     */
    @Test
    public void testATableWithAHeaderThatCannotBeReadStampsNothing() {
        assertTrue(tagCovers("single", version(2, 11, 7)));
        assertTrue(tagCovers("single", version(3, 1, 6)));
        assertTrue(tagCovers("off", version(3, 1, 6)));
    }

    /** {includeblock} has one row and no second one to widen it, so it stays Latte 2 only. */
    @Test
    public void testOneRowWithNoSecondOpinionIsStillTakenAtItsWord() {
        assertTrue(tagCovers("includeblock", version(2, 11, 7)));
        assertFalse(tagCovers("includeblock", version(3, 1, 6)));
    }
}
