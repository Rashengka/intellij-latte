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

public class MacroVarInspectionTest extends BasePsiParsingTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LatteConfiguration.getInstance(getProject());
        getProject().registerService(LatteSettings.class);
    }

    @Override
    protected String getTestDataPath() {
        URL url = getClass().getClassLoader().getResource("data/inspections/macroVar");
        assert url != null;
        return url.getFile();
    }

    @Test
    public void testValidVarDefinition() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("ValidVarDefinition.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(0, problems.size());
    }

    /**
     * What follows a declaration has to be an {@code =} - but nothing at all may follow it.
     *
     * <p>The fixture used to be {@code {var $foo}} and this test used to expect an error for it.
     * That was a false report: {@code {var $foo}} declares the name as null and both ends of the
     * supported range compile it, pass {@code php -l} on the generated code and render it. What
     * really is broken is a declaration followed by something that is not an assignment, so that
     * is what the fixture holds now. Measured, not reasoned:
     * {@code .ai/plans/24-var-bez-definicniho-operatoru.md}.
     */
    @Test
    public void testSomethingOtherThanAnAssignmentAfterTheDeclaration() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("MissingDefinitionOperator.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(1, problems.size());

        Assert.assertEquals("Tag {var} must contain definition operator (=).", problems.get(0).getDescription());
        Assert.assertEquals(ProblemHighlightType.GENERIC_ERROR, problems.get(0).getType());
    }

    /**
     * A declaration with no value at all. Latte 2.11.7 and Latte 3.1.6 both render it, so the
     * plugin says nothing - it was 36 of the 44 reports this shape produced over the corpus.
     */
    @Test
    public void testADeclarationWithoutAValueIsValid() throws IOException {
        Assert.assertSame(0, getProblems("DeclarationWithoutValue.latte").size());
    }

    @Test
    public void testAListOfDeclarationsWithoutValuesIsValid() throws IOException {
        Assert.assertSame(0, getProblems("DeclarationListWithoutValue.latte").size());
    }

    @Test
    public void testATypedDeclarationWithoutAValueIsValid() throws IOException {
        Assert.assertSame(0, getProblems("TypedDeclarationWithoutValue.latte").size());
    }

    @Test
    public void testMissingContentAfterEquals() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("MissingContentAfterEquals.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(1, problems.size());

        Assert.assertEquals("Tag {var} must contain variable content after =.", problems.get(0).getDescription());
        Assert.assertEquals(ProblemHighlightType.GENERIC_ERROR, problems.get(0).getType());
    }

    @Test
    public void testTypedVarDefinition() throws IOException {
        List<LatteInspectionInfo> problems = getProblems("TypedVarDefinition.latte");

        Assert.assertNotNull(problems);
        Assert.assertSame(0, problems.size());
    }

    private List<LatteInspectionInfo> getProblems(@NotNull String templateName) throws IOException {
        PsiFile file = parseFile(templateName);
        return (new MacroVarInspection()).checkFile(file);
    }
}
