package dev.noctud.latte.version;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LatteVersionParserTest {

	@Test
	public void testLockFileGivesAnExactVersion() {
		LatteVersion version = LatteVersionParser.fromLockFile(lock("\"latte/latte\"", "\"v2.11.7\""));
		assertEquals("2.11.7", version.toString());
		assertEquals(LatteVersionSource.LOCK_FILE, version.getSource());
		assertTrue(version.hasPatchPrecision());
	}

	@Test
	public void testLeadingVIsOptional() {
		assertEquals("3.1.6", LatteVersionParser.fromLockFile(lock("\"latte/latte\"", "\"3.1.6\"")).toString());
	}

	/**
	 * Most Nette projects do not require latte/latte directly - it arrives through
	 * nette/application. composer.lock flattens that, so the entry is present either way and the
	 * lookup must not be restricted to what composer.json names.
	 */
	@Test
	public void testTransitiveDependencyIsFound() {
		String content = "{\"packages\":["
			+ "{\"name\":\"nette/application\",\"version\":\"v3.2.6\"},"
			+ "{\"name\":\"latte/latte\",\"version\":\"v2.11.7\"}"
			+ "],\"packages-dev\":[]}";
		assertEquals("2.11.7", LatteVersionParser.fromLockFile(content).toString());
	}

	@Test
	public void testPackagesDevIsUsedWhenPackagesHasNoLatte() {
		String content = "{\"packages\":[{\"name\":\"nette/utils\",\"version\":\"v4.0.7\"}],"
			+ "\"packages-dev\":[{\"name\":\"latte/latte\",\"version\":\"v3.1.6\"}]}";
		LatteVersion version = LatteVersionParser.fromLockFile(content);
		assertEquals("3.1.6", version.toString());
		assertTrue(version.isFromDevDependencies());
	}

	@Test
	public void testPackagesWinsOverPackagesDev() {
		String content = "{\"packages\":[{\"name\":\"latte/latte\",\"version\":\"v2.11.7\"}],"
			+ "\"packages-dev\":[{\"name\":\"latte/latte\",\"version\":\"v3.1.6\"}]}";
		LatteVersion version = LatteVersionParser.fromLockFile(content);
		assertEquals("2.11.7", version.toString());
		assertTrue(!version.isFromDevDependencies());
	}

	/**
	 * A guessed version is worse than an admitted "unknown", because the next cycle turns the
	 * answer into diagnostics.
	 */
	@Test
	public void testBranchVersionsAreNotGuessed() {
		assertTrue(LatteVersionParser.fromLockFile(lock("\"latte/latte\"", "\"dev-master\"")).isUndetermined());
		assertTrue(LatteVersionParser.fromLockFile(lock("\"latte/latte\"", "\"3.x-dev\"")).isUndetermined());
		// This is the shape that actually needs the -dev check: the three numbers parse, so
		// without it the branch would be reported as the release it was branched from.
		assertTrue(LatteVersionParser.fromLockFile(lock("\"latte/latte\"", "\"3.0.26-dev\"")).isUndetermined());
	}

	/**
	 * A release candidate of 3.1.0 behaves as 3.1.0 for everything the plugin decides, so it is
	 * read as that rather than discarded. Pinned here because it is a choice, not an accident.
	 */
	@Test
	public void testPreReleasesAreReadAsTheirRelease() {
		assertEquals("3.1.0", LatteVersionParser.fromLockFile(lock("\"latte/latte\"", "\"v3.1.0-RC1\"")).toString());
		assertEquals("2.11.0", LatteVersionParser.fromLockFile(lock("\"latte/latte\"", "\"v2.11.0-beta1\"")).toString());
	}

	@Test
	public void testMissingOrBrokenInputIsUndetermined() {
		assertTrue(LatteVersionParser.fromLockFile(lock("\"nette/utils\"", "\"v4.0.7\"")).isUndetermined());
		assertTrue(LatteVersionParser.fromLockFile("not json at all").isUndetermined());
		assertTrue(LatteVersionParser.fromLockFile("").isUndetermined());
		assertTrue(LatteVersionParser.fromLockFile(null).isUndetermined());
	}

	/** A name that merely contains the package name must not be mistaken for it. */
	@Test
	public void testSimilarPackageNamesAreNotMatched() {
		assertTrue(LatteVersionParser.fromLockFile(lock("\"acme/latte-latte\"", "\"v1.0.0\"")).isUndetermined());
		assertTrue(LatteVersionParser.fromLockFile(lock("\"latte/latte-extra\"", "\"v1.0.0\"")).isUndetermined());
	}

	@Test
	public void testConstraintGivesALineNotAPatch() {
		LatteVersion version = LatteVersionParser.fromConstraint("^3.0");
		assertEquals("3.0", version.toString());
		assertEquals(LatteVersionSource.CONSTRAINT, version.getSource());
		assertTrue(!version.hasPatchPrecision());
	}

	@Test
	public void testConstraintLowerBoundIsWhatCounts() {
		assertEquals("2.11", LatteVersionParser.fromConstraint("^2.11.7").toString());
		assertEquals("2.11", LatteVersionParser.fromConstraint(">=2.11 <3.0").toString());
		assertEquals("3.1", LatteVersionParser.fromConstraint("3.1.*").toString());
		assertEquals("3.1", LatteVersionParser.fromConstraint("~3.1.0").toString());
		assertEquals("3.0", LatteVersionParser.fromConstraint("3.0.26").toString());
	}

	@Test
	public void testConstraintBelowTheSupportedFloorIsUndetermined() {
		// 2.10 and older are out of the supported range; claiming 2.11 would be a guess.
		assertTrue(LatteVersionParser.fromConstraint("^2.10").isUndetermined());
		assertTrue(LatteVersionParser.fromConstraint("*").isUndetermined());
		assertTrue(LatteVersionParser.fromConstraint("").isUndetermined());
		assertTrue(LatteVersionParser.fromConstraint(null).isUndetermined());
	}

	/** The php requirement may only rule a line out, never pick one. */
	@Test
	public void testPhpRequirementRulesOutLatte3() {
		assertTrue(LatteVersionParser.canRunLatte3("^8.2"));
		assertTrue(LatteVersionParser.canRunLatte3(">=8.0"));
		assertTrue(!LatteVersionParser.canRunLatte3("^7.4"));
		assertTrue(!LatteVersionParser.canRunLatte3(">=7.1 <8.0"));
		// Unknown or absent tells us nothing, so it rules nothing out.
		assertTrue(LatteVersionParser.canRunLatte3(null));
		assertTrue(LatteVersionParser.canRunLatte3("nonsense"));
	}

	@Test
	public void testConstraintFromComposerJsonPrefersRequireOverRequireDev() {
		String content = "{\"require\":{\"latte/latte\":\"^2.11\"},"
			+ "\"require-dev\":{\"latte/latte\":\"^3.1\"}}";
		assertEquals("2.11", LatteVersionParser.fromComposerJson(content).toString());
	}

	@Test
	public void testConstraintFallsBackToRequireDev() {
		String content = "{\"require\":{\"php\":\"^8.2\"},\"require-dev\":{\"latte/latte\":\"^3.1\"}}";
		assertEquals("3.1", LatteVersionParser.fromComposerJson(content).toString());
	}

	@Test
	public void testComposerJsonWithoutLatteIsUndetermined() {
		assertTrue(LatteVersionParser.fromComposerJson("{\"require\":{\"php\":\"^8.2\"}}").isUndetermined());
	}

	/**
	 * A project pinned to PHP 7 cannot be on Latte 3, so a ^3.0 constraint there is a
	 * contradiction. Reporting a version from contradictory input would be a guess.
	 */
	@Test
	public void testConstraintContradictedByPhpRequirementIsUndetermined() {
		String content = "{\"require\":{\"php\":\"^7.4\",\"latte/latte\":\"^3.0\"}}";
		assertTrue(LatteVersionParser.fromComposerJson(content).isUndetermined());
	}

	@Test
	public void testUndeterminedHasNoVersionToShow() {
		LatteVersion undetermined = LatteVersion.undetermined();
		assertTrue(undetermined.isUndetermined());
		assertEquals(LatteVersionSource.UNDETERMINED, undetermined.getSource());
		assertNull(undetermined.line());
	}

	private String lock(String name, String version) {
		return "{\"packages\":[{\"name\":" + name + ",\"version\":" + version + "}],\"packages-dev\":[]}";
	}
}
