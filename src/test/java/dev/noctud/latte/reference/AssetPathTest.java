package dev.noctud.latte.reference;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.inspections.MissingFileInspection;
import dev.noctud.latte.reference.references.LatteAssetReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The argument of {@code {asset}} is a path to a file in the project, behind the name of the mapper
 * that will serve it: {@code {asset 'vite:assets/app.ts'}}. Nothing in the plugin read it, so the
 * file it names could not be opened from the template.
 *
 * <p>Which directory a mapper serves from is configured, and the plugin does not read that
 * configuration - so the only thing it can prove is that the path names a file of the project. When
 * it does, the path is a link; when it does not, there is neither a link nor a report. A missing
 * asset is what a project looks like while the asset is being written.
 */
public class AssetPathTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("assets/app.ts", "export const app = 1;\n");
        myFixture.enableInspections(new MissingFileInspection());
    }

    public void testThePathBehindTheMapperNameIsALink() {
        PsiFile file = configure("{asset 'vite:assets/app.ts'}\n");

        PsiReference reference = referenceTo(file, "assets/app.ts");
        assertNotNull("the path should be a reference", reference);
        assertEquals("assets/app.ts", pathOf(reference));

        PsiElement target = reference.resolve();
        assertNotNull("the asset should be reachable", target);
        assertEquals("app.ts", ((PsiFile) target).getName());
    }

    public void testAPathWithNoMapperNameIsALinkToo() {
        PsiFile file = configure("{asset 'assets/app.ts'}\n");

        PsiReference reference = referenceTo(file, "assets/app.ts");
        assertNotNull(reference);
        assertNotNull(reference.resolve());
    }

    /**
     * The mapper name is not part of the path, so it is not part of what the link covers either.
     */
    public void testTheMapperNameIsNotPartOfTheLink() {
        PsiFile file = configure("{asset 'vite:assets/app.ts'}\n");

        assertNull("the mapper name is nothing to click on", referenceTo(file, "vite:"));
    }

    public void testAnAssetThatIsNotThereIsNeitherLinkedNorReported() {
        PsiFile file = configure("{asset 'vite:assets/missing.ts'}\n");

        PsiReference reference = referenceTo(file, "assets/missing.ts");
        assertNotNull(reference);
        assertNull("there is nothing to open", reference.resolve());
        assertEquals(List.of(), problems());
    }

    /**
     * The variable is still a variable, with the reference it has everywhere else - there is only
     * no asset in it.
     */
    public void testAPathBuiltAtRuntimeIsLeftAlone() {
        PsiFile file = configure("{asset $file}\n");

        assertEquals(List.of(), problems());
        assertFalse(referenceTo(file, "$file") instanceof LatteAssetReference);
    }

    /**
     * The same string in any other tag is a string. Only {@code {asset}} says its argument is a
     * path.
     */
    public void testTheSameStringInAnotherTagIsNotAnAsset() {
        PsiFile file = configure("{= 'assets/app.ts'}\n");

        assertNull(referenceTo(file, "assets/app.ts"));
    }

    private PsiFile configure(@NotNull String text) {
        myFixture.configureByText("asset.latte", text);
        return myFixture.getFile();
    }

    private List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            problems.add(info.getSeverity().getName() + ":" + info.getDescription());
        }
        return problems;
    }

    private @NotNull String pathOf(@NotNull PsiReference reference) {
        return reference.getRangeInElement().substring(reference.getElement().getText());
    }

    private @Nullable PsiReference referenceTo(@NotNull PsiFile file, @NotNull String text) {
        int offset = file.getText().indexOf(text);
        assertTrue("'" + text + "' is not in the template", offset >= 0);
        return file.findReferenceAt(offset);
    }
}
