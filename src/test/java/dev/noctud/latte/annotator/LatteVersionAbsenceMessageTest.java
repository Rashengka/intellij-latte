package dev.noctud.latte.annotator;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.inspections.ModifierDefinitionInspection;
import dev.noctud.latte.settings.LatteSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * What the reader is told when a tag or filter is missing because of the version.
 *
 * "Unknown tag" sends them looking for a typo in a name that is spelled correctly. The template is
 * not wrong about the language, it is wrong about which Latte is installed - and that is a
 * different thing to go and check.
 */
public class LatteVersionAbsenceMessageTest extends BasePlatformTestCase {

	@Override
	protected void tearDown() throws Exception {
		try {
			LatteSettings.getInstance(getProject()).latteVersionOverride = "";
		} finally {
			super.tearDown();
		}
	}

	/** {includeblock} is Latte 2 only. Under Latte 3 it is not a typo, it is gone. */
	public void testATagRemovedInLatte3SaysSo() {
		assertReported("3.1", "{includeblock 'other.latte'}", "Tag {includeblock} was removed in Latte 3.0");
	}

	/** {exitIf} arrived in 3.0.5, so under 2.11 it is not gone - it is not there yet. */
	public void testATagAddedLaterSaysWhenItArrives() {
		assertReported("2.11", "{exitIf $done}", "Tag {exitIf} does not exist before Latte 3.0.5");
	}

	/**
	 * n:elseif arrived in 3.1.0 and {elseif} has always existed, and the registry holds one entry
	 * that serves both - so the tables cannot prove the attribute absent without taking the tag
	 * away with it. Silence is the right answer, and this pins it: the alternative is to report a
	 * tag that every version has.
	 */
	public void testAFormThatOnlyTheAttributeLacksIsNotReported() {
		assertNotReported("3.0", "<div n:if=\"$a\"></div><div n:elseif=\"$b\"></div>", "elseif");
	}

	/** column() arrived in 3.1.3. */
	public void testAFilterAddedLaterSaysWhenItArrives() {
		myFixture.enableInspections(new ModifierDefinitionInspection());

		assertReported("2.11", "{$items|column:'id'}", "Filter 'column' does not exist before Latte 3.1.3");
	}

	/**
	 * A name the reference tables never mention is a name the plugin has no version story for, so
	 * the old wording is the honest one.
	 */
	public void testANameTheTablesDoNotKnowIsStillJustUnknown() {
		assertReported("3.1", "{notATagAnywhere}", "Unknown tag {notATagAnywhere}");
	}

	/**
	 * Nothing is established about the project, so nothing is withheld and there is nothing to
	 * explain. This is the state most projects are in.
	 */
	public void testAnUndeterminedVersionReportsNeitherTag() {
		assertNotReported("", "{includeblock 'other.latte'}", "includeblock");
		assertNotReported("", "{exitIf $done}", "exitIf");
	}

	private void assertReported(@NotNull String line, @NotNull String template, @NotNull String expected) {
		List<String> reported = messagesOn(line, template);
		for (String message : reported) {
			if (message.contains(expected)) {
				return;
			}
		}
		fail("Expected a report containing \"" + expected + "\", got " + reported);
	}

	private void assertNotReported(@NotNull String line, @NotNull String template, @NotNull String name) {
		for (String message : messagesOn(line, template)) {
			assertFalse("Nothing should be reported about " + name + ", got: " + message,
				message.contains(name));
		}
	}

	private @NotNull List<String> messagesOn(@NotNull String line, @NotNull String template) {
		LatteSettings.getInstance(getProject()).latteVersionOverride = line;
		myFixture.configureByText("template.latte", template);
		List<String> messages = new ArrayList<>();
		for (HighlightInfo info : myFixture.doHighlighting()) {
			if (info.getDescription() != null) {
				messages.add(info.getDescription());
			}
		}
		return messages;
	}
}
