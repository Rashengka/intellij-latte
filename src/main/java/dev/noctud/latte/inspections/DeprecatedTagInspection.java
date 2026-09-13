package dev.noctud.latte.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.inspections.utils.LatteInspectionInfo;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.psi.LatteMacroTag;
import com.intellij.psi.util.PsiUtilCore;
import dev.noctud.latte.settings.LatteTagSettings;
import dev.noctud.latte.version.LatteLanguageReference;
import dev.noctud.latte.version.LatteVersion;
import dev.noctud.latte.version.LatteVersionService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DeprecatedTagInspection extends BaseLocalInspectionTool {

    @NotNull
    @Override
    public String getShortName() {
        return "DeprecatedTag";
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
        if (!(file instanceof LatteFile)) {
            return null;
        }
        final List<ProblemDescriptor> problems = new ArrayList<>();
        addInspections(manager, problems, checkFile(file), isOnTheFly);
        return problems.toArray(new ProblemDescriptor[0]);
    }

    @NotNull
    List<LatteInspectionInfo> checkFile(@NotNull final PsiFile file) {
        final List<LatteInspectionInfo> problems = new ArrayList<>();
        file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof LatteMacroTag) {
                    String macroName = ((LatteMacroTag) element).getMacroName();
                    LatteTagSettings macro = LatteConfiguration.getInstance(element.getProject()).getTag(macroName, element);
                    String description = macro != null ? deprecation(macro, macroName, element) : null;
                    if (description != null) {
                        problems.add(LatteInspectionInfo.deprecated(element, description));
                    }
                } else {
                    super.visitElement(element);
                }
            }
        });

        return problems;
    }

    /**
     * What the tag's own definition says, else what the reference tables say about the project's
     * Latte. The tables speak only for Latte's own tags: a tag the project defines under the same
     * name is not the engine's, and the engine's deprecation does not reach it.
     */
    private static @Nullable String deprecation(@NotNull LatteTagSettings macro, @NotNull String name, @NotNull PsiElement element) {
        if (macro.isDeprecated()) {
            return macro.getDeprecatedMessage() != null && macro.getDeprecatedMessage().length() > 0
                ? macro.getDeprecatedMessage()
                : "Tag {" + name + "} is deprecated";
        }
        if (macro.getVendor() != LatteConfiguration.Vendor.LATTE) {
            return null;
        }
        LatteVersion version = LatteVersionService.getInstance(element.getProject()).getVersion(PsiUtilCore.getVirtualFile(element));
        String since = LatteLanguageReference.getInstance().deprecationOfTag(name, version);
        return since == null ? null : "Tag {" + name + "} is deprecated since Latte " + since;
    }
}
