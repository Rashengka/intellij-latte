package dev.noctud.latte.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiFile;
import dev.noctud.latte.BasePsiParsingTestCase;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.inspections.utils.LatteInspectionInfo;
import dev.noctud.latte.settings.LatteSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

public class VariablesInspectionTest extends BasePsiParsingTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // initialize configuration with test configuration
        LatteConfiguration.getInstance(getProject());

        getProject().registerService(LatteSettings.class);
    }

    @Override
    protected String getTestDataPath() {
        URL url = getClass().getClassLoader().getResource("data/inspections/variables");
        assert url != null;
        return url.getFile();
    }

    @Test
    public void testUndefinedVariable() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("UndefinedVariable.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(1, problems.size());

        Assert.assertEquals("Undefined variable 'foo'", problems.get(0).getDescription());
        Assert.assertEquals(ProblemHighlightType.GENERIC_ERROR_OR_WARNING, problems.get(0).getType());
    }

    @Test
    public void testProbablyUndefinedVariable() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("ProbablyUndefinedVariable.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(1, problems.size());

        Assert.assertEquals("Variable 'bar' is probably undefined", problems.get(0).getDescription());
        Assert.assertEquals(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, problems.get(0).getType());
    }

    /**
     * {var} assigns, it does not declare: Latte compiles {var $x = 1} to the PHP assignment
     * $x = 1, with no check that the name is free - only {default} adds one. Writing to the same
     * name twice is therefore ordinary Latte, and the report on it was false.
     */
    @Test
    public void testAssignedTwiceWithVarIsNotAClash() throws IOException {
        assertNoProblems("MultipleDefinitions.latte");
    }

    @Test
    public void testCaptureOverAVarIsNotAClash() throws IOException {
        assertNoProblems("CaptureAfterVar.latte");
    }

    /**
     * The line runs between assigning and declaring, not between once and twice. {varType} is a
     * declaration - it says what type the name carries - so giving one name two types stays
     * reported.
     */
    @Test
    public void testTypeDeclaredTwiceIsReported() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("MultipleVarTypeDefinitions.latte");

        Assert.assertNotNull(problems);
        Assert.assertEquals(describe(problems), 2, problems.size());

        Assert.assertEquals("Multiple definitions for variable 'foo'", problems.get(0).getDescription());
        Assert.assertEquals(ProblemHighlightType.WARNING, problems.get(0).getType());

        Assert.assertEquals("Multiple definitions for variable 'foo'", problems.get(1).getDescription());
        Assert.assertEquals(ProblemHighlightType.WARNING, problems.get(1).getType());
    }

    /**
     * Declaring a type and then assigning to the name is the ordinary way a typed template is
     * written, so the two must not be read as a declaration made twice.
     */
    @Test
    public void testTypeDeclaredAndThenAssignedIsNotAClash() throws IOException {
        assertNoProblems("VarTypeThenAssignment.latte");
    }

    @Test
    public void testDefinitionsInAnotherContext() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("DefinitionsInAnotherContext.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(1, problems.size());

        Assert.assertEquals("Unused variable 'foo'", problems.get(0).getDescription());
        Assert.assertEquals(ProblemHighlightType.LIKE_UNUSED_SYMBOL, problems.get(0).getType());
    }

    @Test
    public void testVariableInNFor() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("VariableInNFor.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(0, problems.size());
    }

    /**
     * n:foreach wraps the whole element, so every other n: attribute on the same tag is already
     * inside the loop it opens. Reported from a real template: $article was underlined in
     * n:class on the very tag whose n:foreach defines it.
     */
    @Test
    public void testVariableUsedInNAttributeOnTheDefiningTag() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("VariableInNAttributeOnSameTag.latte");

        Assert.assertNotNull(problems);
        Assert.assertEquals(
            "Expected no problems, got: " + problems.stream()
                .map(LatteInspectionInfo::getDescription)
                .collect(Collectors.joining(", ")),
            0,
            problems.size()
        );
    }

    @Test
    public void testVariableInBlock() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("VariableInBlock.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(2, problems.size());
        Assert.assertNotEquals(problems.get(0).getElement(), problems.get(1).getElement());

        Assert.assertEquals("Unused variable 'foo'", problems.get(0).getDescription());
        Assert.assertEquals(ProblemHighlightType.LIKE_UNUSED_SYMBOL, problems.get(0).getType());

        Assert.assertEquals("Unused variable 'foo'", problems.get(1).getDescription());
        Assert.assertEquals(ProblemHighlightType.LIKE_UNUSED_SYMBOL, problems.get(1).getType());
    }

    @Test
    public void testVariableDefinedInParentScope() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("VariableDefinedInParentScope.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(0, problems.size());
    }

    @Test
    public void testVariableDefinedInParentScopeNestedIf() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("VariableDefinedInParentScope2.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(0, problems.size());
    }

    @Test
    public void testArrowFunctionParameter() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("ArrowFunctionParameter.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(0, problems.size());
    }

    @Test
    public void testVariableDefinedInIfElse() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("VariableDefinedInIfElse.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(0, problems.size());
    }

    @Test
    public void testVariableDefinedInIfOnly() throws IOException {
        // Variable defined only in {if} (no {else}) — should warn as probably undefined
        List<LatteInspectionInfo> problems = getProblems("VariableDefinedInIfOnly.latte");

        Assert.assertNotNull(problems);
        LatteInspectionInfo probablyUndefined = problems.stream()
            .filter(p -> p.getDescription().contains("probably undefined"))
            .findFirst().orElse(null);
        Assert.assertNotNull("Expected 'probably undefined' warning", probablyUndefined);
    }

    @Test
    public void testVariableDefinedInElseifNoElse() throws IOException {
        // Variable defined in {if} and {elseif} but no final {else} — should warn
        List<LatteInspectionInfo> problems = getProblems("VariableDefinedInElseifNoElse.latte");

        Assert.assertNotNull(problems);
        LatteInspectionInfo probablyUndefined = problems.stream()
            .filter(p -> p.getDescription().contains("probably undefined"))
            .findFirst().orElse(null);
        Assert.assertNotNull("Expected 'probably undefined' warning without final {else}", probablyUndefined);
    }

    @Test
    public void testVariableDefinedInAllElseifBranches() throws IOException {
        // Variable defined in {if}, {elseif}, and {else} — all branches covered, no warning
        List<LatteInspectionInfo> problems = getProblems("VariableDefinedInAllElseifBranches.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(0, problems.size());
    }

    @Test
    public void testVariableDefinedInIfCondition() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("VariableDefinedInIfCondition.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(0, problems.size());
    }

    @Test
    public void testVariableAssignedInConditionScope() throws IOException {
        // {if $a = getSomething()} defines $a, but using $a after {/if} should be "probably undefined"
        List<LatteInspectionInfo> problems = getProblems("VariableAssignedInConditionScope.latte");

        Assert.assertNotNull(problems);
        LatteInspectionInfo probablyUndefined = problems.stream()
            .filter(p -> p.getDescription().contains("probably undefined"))
            .findFirst().orElse(null);
        Assert.assertNotNull("Expected 'probably undefined' for variable used after conditional assignment", probablyUndefined);
    }

    @Test
    public void testComparisonNotDefinition() throws IOException {
        // {if $a == 'test'} is a comparison, not a definition — $a should be undefined
        List<LatteInspectionInfo> problems = getProblems("ComparisonNotDefinition.latte");

        Assert.assertNotNull(problems);
        LatteInspectionInfo undefined = problems.stream()
            .filter(p -> p.getDescription().contains("ndefined variable 'a'"))
            .findFirst().orElse(null);
        Assert.assertNotNull("Expected undefined variable warning for comparison-only usage", undefined);
    }

    @Test
    public void testClosureParameter() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("ClosureParameter.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(0, problems.size());
    }

    /**
     * A {php} or {do} body is PHP, so the parser sees plain tokens where the Latte grammar would
     * have built a foreach node. Every shape below binds a name in PHP and none of them may be
     * reported as undefined; the last two prove the recogniser still knows where a binding ends.
     */
    @Test
    public void testPhpForeachValue() throws IOException {
        assertNoProblems("PhpForeachValue.latte");
    }

    @Test
    public void testPhpForeachKeyValue() throws IOException {
        assertNoProblems("PhpForeachKeyValue.latte");
    }

    @Test
    public void testPhpForeachReference() throws IOException {
        assertNoProblems("PhpForeachReference.latte");
    }

    @Test
    public void testPhpForeachNested() throws IOException {
        assertNoProblems("PhpForeachNested.latte");
    }

    @Test
    public void testPhpListDestructuring() throws IOException {
        assertNoProblems("PhpListDestructuring.latte");
    }

    @Test
    public void testPhpArrayDestructuring() throws IOException {
        assertNoProblems("PhpArrayDestructuring.latte");
    }

    @Test
    public void testPhpCatchParameter() throws IOException {
        assertNoProblems("PhpCatchParameter.latte");
    }

    @Test
    public void testPhpClosureParameterAndUse() throws IOException {
        assertNoProblems("PhpClosureParameterAndUse.latte");
    }

    @Test
    public void testPhpClosureUseByReference() throws IOException {
        assertNoProblems("PhpClosureUseByReference.latte");
    }

    @Test
    public void testPhpStaticDeclaration() throws IOException {
        assertNoProblems("PhpStaticDeclaration.latte");
    }

    @Test
    public void testPhpGlobalDeclaration() throws IOException {
        assertNoProblems("PhpGlobalDeclaration.latte");
    }

    @Test
    public void testPhpForeachDefinesNothingBeforeTheLoop() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("PhpForeachUsedBeforeLoop.latte");

        Assert.assertNotNull(problems);
        Assert.assertEquals(describe(problems), 1, problems.size());
        Assert.assertEquals("Undefined variable 'item'", problems.get(0).getDescription());
    }

    @Test
    public void testPhpForeachIteratedExpressionIsNotADefinition() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("PhpForeachIteratedExpression.latte");

        Assert.assertNotNull(problems);
        Assert.assertEquals(describe(problems), 1, problems.size());
        Assert.assertEquals("Undefined variable 'source'", problems.get(0).getDescription());
    }

    /**
     * A {php} or {do} body is PHP, and assigning to a name in it more than once is ordinary PHP -
     * most often a variable set up empty and then filled element by element.
     */
    @Test
    public void testAssignedTwiceInOnePhpBody() throws IOException {
        assertNoProblems("PhpAssignmentTwiceInOneBody.latte");
    }

    @Test
    public void testAssignedTwiceInOneDoBody() throws IOException {
        assertNoProblems("DoAssignmentTwiceInOneBody.latte");
    }

    /**
     * Two bodies are two tags, but both hold assignments, and assigning twice is not declaring
     * twice wherever the second assignment stands.
     */
    @Test
    public void testAssignedInTwoPhpBodies() throws IOException {
        assertNoProblems("PhpAssignmentInTwoBodies.latte");
    }

    /**
     * Giving an existing variable one more element is a write into it, not a second definition of
     * it, so the {do} that adds the element must not be read as redefining what the {php} above
     * filled.
     */
    @Test
    public void testArrayElementWrittenInAnotherBody() throws IOException {
        assertNoProblems("PhpArrayElementWrittenInAnotherBody.latte");
    }

    private void assertNoProblems(@NotNull String templateName) throws IOException {
        List<LatteInspectionInfo> problems = getProblems(templateName);

        Assert.assertNotNull(problems);
        Assert.assertEquals(describe(problems), 0, problems.size());
    }

    @NotNull
    private String describe(@NotNull List<LatteInspectionInfo> problems) {
        return "Reported problems: " + problems.stream()
            .map(LatteInspectionInfo::getDescription)
            .collect(Collectors.joining(", "));
    }

    private List<LatteInspectionInfo> getProblems(@NotNull String templateName) throws IOException {
        PsiFile file = parseFile(templateName);

        return (new VariablesInspection()).checkFile(file);
    }

}
