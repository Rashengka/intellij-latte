package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.settings.LatteSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A tag the project's Latte deprecates is reported as deprecated, from the version the reference
 * table names in its Deprecated column - the version from which the engine emits
 * {@code E_USER_DEPRECATED} for it.
 *
 * <p>{@code {includeblock}} is the case: Latte 2.11 compiles it with a deprecation and Latte 3 no
 * longer has it, which the annotator says on its own. A deprecation that holds only in some
 * places - {@code {first}} outside a {@code {foreach}} from 3.1.6 - cannot be told from the tag
 * alone and is not reported.
 */
public class DeprecatedTagPerVersionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new DeprecatedTagInspection());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            LatteSettings.getInstance(getProject()).latteVersionOverride = "";
        } finally {
            super.tearDown();
        }
    }

    public void testATagDeprecatedInTheLineIsReported() {
        assertReported("2.11", "{includeblock 'other.latte'}", "Tag {includeblock} is deprecated since Latte 2.11");
    }

    public void testATagDeprecatedInTheLineIsReportedWhenThePatchIsKnown() {
        assertReported("2.11.7", "{includeblock 'other.latte'}", "Tag {includeblock} is deprecated since Latte 2.11");
    }

    /** Gone rather than deprecated - the annotator says "was removed", and saying both is noise. */
    public void testATagTheLineNoLongerHasIsNotReportedAsDeprecated() {
        assertNoDeprecation("3.1", "{includeblock 'other.latte'}");
    }

    public void testAnUndeterminedVersionReportsNoDeprecation() {
        assertNoDeprecation("", "{includeblock 'other.latte'}");
    }

    /** "3.1.6 outside {foreach}" - the tag alone does not say whether it is outside one. */
    public void testADeprecationThatHoldsOnlyInSomePlacesIsNotReported() {
        assertNoDeprecation("3.1.6", "{foreach $items as $item}{first}x{/first}{/foreach}");
    }

    public void testATagNobodyDeprecatesIsNotReported() {
        assertNoDeprecation("2.11", "{foreach $items as $item}{$item}{/foreach}");
    }

    private void assertReported(@NotNull String version, @NotNull String template, @NotNull String expected) {
        List<String> reported = messagesOn(version, template);
        assertTrue("Expected \"" + expected + "\", got " + reported, reported.contains(expected));
    }

    private void assertNoDeprecation(@NotNull String version, @NotNull String template) {
        for (String message : messagesOn(version, template)) {
            assertFalse("Nothing should be reported as deprecated, got: " + message, message.contains("deprecated"));
        }
    }

    private @NotNull List<String> messagesOn(@NotNull String version, @NotNull String template) {
        LatteSettings.getInstance(getProject()).latteVersionOverride = version;
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
