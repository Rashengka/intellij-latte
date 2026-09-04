package dev.noctud.latte.reference;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.psi.LatteFilePath;

/**
 * The path in {@code {include 'blocks/card.latte'}} is a link, one reference per segment, so that
 * both the directory and the file can be opened from the template.
 *
 * <p>Each of them used to be looked up by handing VirtualFileManager a {@code file://} URL built
 * out of the containing directory's path, which finds a file only when the project is on the local
 * disk - the same defect MissingFileInspection had. A path is resolved against the directory's own
 * VirtualFile now, so the link works wherever the project lives.
 */
public class IncludedFilePathTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("templates/partial.latte", "<p>partial</p>\n");
        myFixture.addFileToProject("templates/blocks/card.latte", "<p>card</p>\n");
    }

    public void testAPathNextToTheTemplateOpensTheFile() {
        PsiElement target = resolveLastSegment("{include 'partial.latte'}\n");

        assertTrue(target instanceof PsiFile);
        assertEquals("partial.latte", ((PsiFile) target).getName());
    }

    public void testAPathThroughADirectoryOpensTheFile() {
        PsiElement target = resolveLastSegment("{include 'blocks/card.latte'}\n");

        assertTrue(target instanceof PsiFile);
        assertEquals("card.latte", ((PsiFile) target).getName());
    }

    public void testTheDirectorySegmentOpensTheDirectory() {
        PsiReference[] references = referencesIn("{include 'blocks/card.latte'}\n");
        assertEquals(2, references.length);

        PsiElement target = references[0].resolve();
        assertTrue(target instanceof PsiDirectory);
        assertEquals("blocks", ((PsiDirectory) target).getName());
    }

    public void testAPathThatNamesNoFileResolvesToNothing() {
        assertNull(resolveLastSegment("{include 'no-such-partial.latte'}\n"));
    }

    private PsiElement resolveLastSegment(String template) {
        PsiReference[] references = referencesIn(template);
        assertTrue("the path should carry a reference", references.length > 0);
        return references[references.length - 1].resolve();
    }

    private PsiReference[] referencesIn(String template) {
        myFixture.addFileToProject("templates/page.latte", template);
        VirtualFile file = myFixture.findFileInTempDir("templates/page.latte");
        assertNotNull(file);
        myFixture.configureFromExistingVirtualFile(file);

        LatteFilePath path = PsiTreeUtil.findChildOfType(myFixture.getFile(), LatteFilePath.class);
        assertNotNull("the template should contain a file path", path);
        return path.getReferences();
    }
}
