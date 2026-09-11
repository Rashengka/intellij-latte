package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

import static dev.noctud.latte.inspections.ExpectedErrorsTest.registeredLatteInspections;

/**
 * The two mistakes the parser itself reports - a string left open and a tag left open until the end
 * of the file - used to come out as a list of about fifty internal token names ending in
 * {@code expected}. Latte says {@code Unterminated string} and {@code Unexpected end} about them.
 *
 * <p>Every inspection the plugin registers runs here, so a readable message that only replaced the
 * list in one place and left it in another would still fail.
 */
public class ReadableParserErrorTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(registeredLatteInspections());
    }

    public void testAStringLeftOpenIsAnUnterminatedString() {
        assertEquals(List.of("ERROR:Unterminated string"), problemsIn("{= 'unterminated}\n"));
        assertEquals(List.of("ERROR:Unterminated string"), problemsIn("{= \"unterminated}\n"));
    }

    public void testATagLeftOpenSaysNothingAboutTokens() {
        List<String> problems = problemsIn("<p>{= 1\n");
        assertFalse("a tag left open has to be reported", problems.isEmpty());
        for (String problem : problems) {
            assertFalse("internal token names reached the user: " + problem, problem.contains("LatteTokenType."));
        }
    }

    public void testStringsThatAreClosedAreQuiet() {
        assertEquals(List.of(), problemsIn("{= 'closed'}\n{= \"closed\"}\n{= \"a{1}b\"}\n"));
    }

    private List<String> problemsIn(String template) {
        myFixture.configureByText("readable.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getSeverity().getName() + ":" + info.getDescription());
            }
        }
        return problems;
    }
}
