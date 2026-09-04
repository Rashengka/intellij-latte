package dev.noctud.latte.reference;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.psi.LatteMacroTag;
import dev.noctud.latte.reference.references.LatteParentBlockReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code {include parent}} does not name a file and does not name a block: Latte reads it as the
 * block the tag stands in, taken from the parent template. The plugin used to hand it to the file
 * path resolver, which offered a link to a file called "parent" - a link that leads nowhere and
 * looks like it should.
 *
 * <p>What replaces it is deliberately narrow. A link is offered only where the parent template can
 * be pointed at, and the two reports are the two mistakes Latte itself refuses: using the keyword
 * outside a block (a compile error) and naming a block no parent template defines (a runtime one).
 * Everywhere else - a dynamic {@code {extends $file}}, a template whose layout the presenter
 * attaches at runtime - the plugin says nothing, because there it cannot tell the two apart.
 */
public class IncludeParentTest extends BasePlatformTestCase {

    /**
     * The reference is always built - looking anything up while references are being built is what
     * crashed the plugin once already - so what has to be empty is where it leads.
     */
    public void testTheKeywordIsNotOfferedAsAFilePath() {
        PsiFile file = configure("child.latte", "{block content}{include parent}{/block}\n");

        PsiReference reference = referenceTo(file, "parent");
        assertTrue(
            "the keyword is not a file path, so it must not be resolved as one",
            reference instanceof LatteParentBlockReference
        );
        assertNull("no parent template is known here, so there is nothing to link to", reference.resolve());
    }

    public void testTheKeywordPointsAtTheBlockOfTheParentTemplate() {
        myFixture.addFileToProject("layout.latte", "{block content}base{/block}\n");
        PsiFile file = configure(
            "child.latte",
            "{extends 'layout.latte'}\n{block content}{include parent}{/block}\n"
        );

        PsiElement target = resolve(file, "{include parent}", "parent");
        assertNotNull("the parent block should be reachable", target);
        assertEquals("layout.latte", target.getContainingFile().getName());
        assertEquals("block", ((LatteMacroTag) target).getMacroName());
    }

    /**
     * The same template, with the parent moved one step further up the chain: Latte looks for the
     * block in every ancestor, not only in the template named by {@code {extends}}.
     */
    public void testTheBlockIsLookedUpThroughTheWholeChain() {
        myFixture.addFileToProject("base.latte", "{block content}base{/block}\n");
        myFixture.addFileToProject("layout.latte", "{extends 'base.latte'}\n");
        PsiFile file = configure(
            "child.latte",
            "{extends 'layout.latte'}\n{block content}{include parent}{/block}\n"
        );

        PsiElement target = resolve(file, "{include parent}", "parent");
        assertNotNull(target);
        assertEquals("base.latte", target.getContainingFile().getName());
    }

    /**
     * A file path is still a file path - the keyword is the only thing taken away from the
     * resolver.
     */
    public void testAFilePathIsStillAReference() {
        PsiFile file = configure("child.latte", "{include 'part.latte'}\n");

        assertNotNull(referenceTo(file, "part.latte"));
    }

    public void testTheKeywordOutsideOfABlockIsReported() {
        assertEquals(
            List.of("ERROR:Cannot include parent block outside of any block."),
            problemsIn("child.latte", "{include parent}\n")
        );
    }

    public void testTheSameKeywordInsideABlockIsNotReported() {
        assertEquals(
            List.of(),
            problemsIn("child.latte", "{block content}{include parent}{/block}\n")
        );
    }

    /**
     * {@code {include this}} is the same keyword with the same rule, and Latte reports it with the
     * same message.
     */
    public void testTheThisKeywordOutsideOfABlockIsReported() {
        assertEquals(
            List.of("ERROR:Cannot include this block outside of any block."),
            problemsIn("child.latte", "{include this}\n")
        );
    }

    public void testABlockNoParentTemplateDefinesIsReported() {
        myFixture.addFileToProject("layout.latte", "{block title}base{/block}\n");

        assertEquals(
            List.of("WARNING:Cannot include undefined parent block 'content'."),
            problemsIn("child.latte", "{extends 'layout.latte'}\n{block content}{include parent}{/block}\n")
        );
    }

    /**
     * Without {@code {extends}} the template still has a parent whenever a presenter attaches a
     * layout to it, which happens at runtime and cannot be seen from here. Reporting the block as
     * undefined would be a guess, so nothing is reported - and nothing is linked either.
     */
    public void testNothingIsReportedWhenTheParentTemplateIsNotKnown() {
        assertEquals(
            List.of(),
            problemsIn("child.latte", "{block content}{include parent}{/block}\n")
        );
    }

    /**
     * {@code {import}} adds the blocks of another template to this one, so a block missing from the
     * chain may still be there. The plugin cannot follow that, and says nothing rather than
     * guessing.
     */
    public void testNothingIsReportedWhenTheParentTemplateImportsBlocks() {
        myFixture.addFileToProject("blocks.latte", "{block content}base{/block}\n");
        myFixture.addFileToProject("layout.latte", "{import 'blocks.latte'}\n");

        assertEquals(
            List.of(),
            problemsIn("child.latte", "{extends 'layout.latte'}\n{block content}{include parent}{/block}\n")
        );
    }

    public void testNothingIsReportedWhenTheParentTemplateIsDynamic() {
        assertEquals(
            List.of(),
            problemsIn("child.latte", "{extends $layout}\n{block content}{include parent}{/block}\n")
        );
    }

    private PsiFile configure(@NotNull String path, @NotNull String text) {
        PsiFile file = myFixture.addFileToProject(path, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    private List<String> problemsIn(@NotNull String path, @NotNull String text) {
        configure(path, text);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            problems.add(info.getSeverity().getName() + ":" + info.getDescription());
        }
        return problems;
    }

    private @Nullable PsiReference referenceTo(@NotNull PsiFile file, @NotNull String text) {
        int offset = file.getText().indexOf(text);
        assertTrue("'" + text + "' is not in the template", offset >= 0);
        return file.findReferenceAt(offset);
    }

    private @Nullable PsiElement resolve(@NotNull PsiFile file, @NotNull String tag, @NotNull String text) {
        int offset = file.getText().indexOf(tag) + tag.indexOf(text);
        PsiReference reference = file.findReferenceAt(offset);
        assertNotNull("no reference on '" + text + "'", reference);
        return reference.resolve();
    }
}
