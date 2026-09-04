package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The inspection used to build a {@code file://} URL out of the containing directory's path and
 * hand it to VirtualFileManager, which meant it could only ever find a file in the local
 * filesystem. Everywhere else - the temp filesystem a test fixture runs in, and equally a remote
 * or in-memory one in the IDE - every {@code {include}} in every template was reported as naming a
 * missing file, which is why this test could not exist before.
 *
 * <p>The path is resolved against the containing directory's VirtualFile instead, so which
 * filesystem the project lives in stops being part of the answer.
 */
public class MissingFileInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new MissingFileInspection());
        myFixture.addFileToProject("templates/partial.latte", "<p>partial</p>\n");
        myFixture.addFileToProject("templates/blocks/card.latte", "<p>card</p>\n");
        myFixture.addFileToProject("layout.latte", "{include content}\n");
    }

    public void testAnIncludedFileThatExistsIsNotReported() {
        assertEquals(List.of(), problemsIn("{include 'partial.latte'}\n"));
    }

    public void testAnIncludedFileInASubdirectoryIsNotReported() {
        assertEquals(List.of(), problemsIn("{include 'blocks/card.latte'}\n"));
    }

    public void testAFileAboveTheTemplateIsNotReported() {
        assertEquals(List.of(), problemsIn("{layout '../layout.latte'}\n"));
    }

    public void testAnIncludedFileThatIsNotThereIsReported() {
        assertEquals(
            List.of("WARNING:File no-such-partial.latte is missing"),
            problemsIn("{include 'no-such-partial.latte'}\n")
        );
    }

    public void testAFileMissingFromASubdirectoryIsReported() {
        assertEquals(
            List.of("WARNING:File missing.latte is missing"),
            problemsIn("{include 'blocks/missing.latte'}\n")
        );
    }

    /**
     * A path built from a variable is not a path the inspection can check, and the arguments after
     * the file name are none of its business either.
     */
    public void testADynamicPathIsNotReported() {
        assertEquals(List.of(), problemsIn("{include $file, title: 'x'}\n"));
    }

    /**
     * The fix the report offers used to write to the local disk through java.io, on a path built
     * the same way the lookup was, so it could not create the file it had just claimed was
     * missing anywhere else. It goes through the virtual filesystem now, which is also what makes
     * it checkable here.
     */
    public void testTheOfferedFixCreatesTheFileInASubdirectory() {
        problemsIn("{include 'blocks/created.latte'}\n");

        myFixture.launchAction(myFixture.findSingleIntention("Create file created.latte"));

        assertNotNull(myFixture.findFileInTempDir("templates/blocks/created.latte"));
    }

    public void testTheOfferedFixCarriesTheTemplateTypeOver() throws IOException {
        String template = "{templateType App\\FooTemplate}\n{include 'created-with-type.latte'}\n";
        problemsIn(template);
        myFixture.getEditor().getCaretModel().moveToOffset(template.indexOf("{include"));

        myFixture.launchAction(myFixture.findSingleIntention("Create file created-with-type.latte"));

        VirtualFile created = myFixture.findFileInTempDir("templates/created-with-type.latte");
        assertNotNull(created);
        assertEquals("{templateType App\\FooTemplate}\n", VfsUtilCore.loadText(created));
    }

    private List<String> problemsIn(String template) {
        myFixture.addFileToProject("templates/page.latte", template);
        VirtualFile file = myFixture.findFileInTempDir("templates/page.latte");
        assertNotNull(file);
        myFixture.configureFromExistingVirtualFile(file);

        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getSeverity().getName() + ":" + info.getDescription());
            }
        }
        return problems;
    }
}
