package dev.noctud.latte.annotator;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LatteSyntaxModeTest {

	/**
	 * The argument accepted by {syntax} depends on the Latte version, and the accepted set is
	 * the union rather than any single version's set. The plugin does not know which version a
	 * project uses, and reporting a mode that is correct somewhere in the supported range would
	 * be a false error on correct code.
	 */
	@Test
	public void testModesValidInEveryVersion() {
		assertTrue(LatteAnnotator.isValidSyntaxMode("off"));
		assertTrue(LatteAnnotator.isValidSyntaxMode("double"));
	}

	@Test
	public void testLatteIsTheDefaultModeNameInLatte2() {
		// Valid in 2.11 - the version this fork targets - and in 3.0.0 to 3.0.1.
		assertTrue(LatteAnnotator.isValidSyntaxMode("latte"));
	}

	@Test
	public void testSingleIsTheDefaultModeNameFromLatte3024() {
		assertTrue(LatteAnnotator.isValidSyntaxMode("single"));
	}

	@Test
	public void testUnknownModeIsRejected() {
		assertFalse(LatteAnnotator.isValidSyntaxMode("triple"));
		assertFalse(LatteAnnotator.isValidSyntaxMode(""));
		assertFalse(LatteAnnotator.isValidSyntaxMode("Off"));
	}
}
