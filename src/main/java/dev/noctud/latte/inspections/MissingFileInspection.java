package dev.noctud.latte.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.psi.LatteFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import dev.noctud.latte.intentions.CreateMissingFile;
import dev.noctud.latte.psi.*;

import java.util.ArrayList;
import java.util.List;

public class MissingFileInspection extends BaseLocalInspectionTool {
    private final List<String> tags = List.of("include", "import", "extends", "layout", "embed", "sandbox");

    @NotNull
    @Override
    public String getShortName() {
        return "LatteMissingFile";
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
        if (!(file instanceof LatteFile)) {
            return null;
        }

        final PsiDirectory containingDirectory = file.getContainingDirectory();
        final VirtualFile containingFileVirtual = file.getVirtualFile();
        if (containingDirectory == null || containingFileVirtual == null) {
            return null;
        }
        // The directory itself, not its path. A path only leads back to a file through the
        // filesystem it was taken from, and building a file:// URL out of it named the local one
        // whatever the project actually lives in - so in any other filesystem every {include}
        // resolved to nothing and was reported as missing.
        final VirtualFile baseDir = containingDirectory.getVirtualFile();

        final List<ProblemDescriptor> problems = new ArrayList<>();
        file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof LatteMacroTag && tags.contains(((LatteMacroTag) element).getMacroName())) {
                    LatteMacroContent macroContent = PsiTreeUtil.findChildOfType(element, LatteMacroContent.class);
                    if (macroContent != null) {
                        String text = macroContent.getText().split("\\s")[0].split(",")[0];
                        if (!text.contains("$") && text.contains(".")) {
                            String relativePath = text.replaceAll("[\"']", "").trim();

                            if (!relativePath.matches(".*\\(.*\\).*")) {
                                if (relativePath.startsWith("/")) {
                                    relativePath = relativePath.substring(1);
                                }

                                VirtualFile virtual = baseDir.findFileByRelativePath(relativePath);
                                if (virtual == null) {
                                    String name = relativePath.substring(relativePath.lastIndexOf('/') + 1);
                                    LocalQuickFix[] fixes = {new CreateMissingFile(containingFileVirtual, baseDir, relativePath)};
                                    problems.add(manager.createProblemDescriptor(element, "File " + name + " is missing", fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, false));
                                }
                            }
                        }
                    }
                } else {
                    super.visitElement(element);
                }
            }
        });

        return problems.toArray(new ProblemDescriptor[0]);
    }
}
