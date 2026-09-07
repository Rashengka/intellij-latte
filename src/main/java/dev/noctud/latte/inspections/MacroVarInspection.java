package dev.noctud.latte.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.inspections.utils.LatteInspectionInfo;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.psi.*;
import dev.noctud.latte.utils.LatteTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MacroVarInspection extends BaseLocalInspectionTool {

    @NotNull
    @Override
    public String getShortName() {
        return "LatteTagVar";
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
                if (element instanceof LatteMacroTag && ((LatteMacroTag) element).matchMacroName("var")) {
                    LatteMacroContent macroContent = PsiTreeUtil.findChildOfType(element, LatteMacroContent.class);
                    if (macroContent != null) {
                        List<LattePhpContent> phpContent = new ArrayList<>(macroContent.getPhpContentList());

                        if (phpContent.size() == 0) {
                            problems.add(LatteInspectionInfo.strictError(element, "Tag {var} must have php content."));

                        } else {
                            LattePhpContent content = phpContent.get(0);
                            List<PsiElement> children = new ArrayList<>();
                            content.acceptChildren(new PsiElementVisitor() {
                                @Override
                                public void visitElement(PsiElement element) {
                                    if (!LatteTypesUtil.whitespaceTokens.contains(element.getNode().getElementType())) {
                                        children.add(element);
                                    }
                                }
                            });

                            if (children.size() == 0) {
                                problems.add(LatteInspectionInfo.strictError(element, "Tag {var} must contain valid variable definition."));

                            } else {
                                if (
                                    !(children.get(0) instanceof LattePhpTypedArguments)
                                        && (!(children.get(0) instanceof LattePhpStatement) || !((LattePhpStatement) children.get(0)).isPhpVariableOnly())
                                ) {
                                    problems.add(LatteInspectionInfo.strictError(element, "Tag {var} must contain valid variable definition."));

                                } else if (children.size() < 2) {
                                    // A declaration with no value: {var $a}, {var $a, $b},
                                    // {var App\Model\Thing $x}. The name is declared as null, and
                                    // it is what a template writes to say a variable exists before
                                    // anything fills it. This used to be reported as a missing
                                    // assignment - 36 of the 44 such reports over the corpus were
                                    // this shape. Checked by running both ends of the supported
                                    // range, 2.11.7 and 3.1.6: each compiles it, the generated PHP
                                    // passes php -l, and the template renders.

                                } else if (children.get(1).getNode().getElementType() != LatteTypes.T_PHP_DEFINITION_OPERATOR) {
                                    // Something does follow the declaration and it is not an
                                    // assignment - {var $a++} is the shape the corpus has. Latte 3
                                    // refuses it outright; Latte 2 compiles it and emits PHP that
                                    // does not parse, so it is broken in both.
                                    problems.add(LatteInspectionInfo.strictError(element, "Tag {var} must contain definition operator (=)."));

                                } else if (children.size() < 3) {
                                    problems.add(LatteInspectionInfo.strictError(element, "Tag {var} must contain variable content after =."));
                                }
                            }
                        }
                    }

                } else {
                    super.visitElement(element);
                }
            }
        });
        return problems;
    }
}
