package dev.noctud.latte.inspections;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.psi.LattePhpClassReference;
import dev.noctud.latte.psi.LattePhpTypePart;
import dev.noctud.latte.psi.LatteTypes;
import dev.noctud.latte.php.LattePhpUtil;
import dev.noctud.latte.utils.LatteUtil;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ClassUsagesInspection extends BaseLocalInspectionTool {

    @NotNull
    @Override
    public String getShortName() {
        return "LatteClassUsages";
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
        if (!(file instanceof LatteFile)) {
            return null;
        }

        final List<ProblemDescriptor> problems = new ArrayList<>();
        file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof LattePhpClassReference) {
                    if (!isClassPosition(element)) {
                        return;
                    }

                    String className = ((LattePhpClassReference) element).getClassName();
                    Collection<PhpClass> classes = LattePhpUtil.getClassesByFQN(element.getProject(), className);
                    if (classes.size() == 0) {
                        addProblem(manager, problems, element, "Undefined class '" + className + "'", isOnTheFly);

                    } else {
                        for (PhpClass phpClass : classes) {
                            if (phpClass.isDeprecated()) {
                                addDeprecated(manager, problems, element, "Used class '" + className + "' is marked as deprecated", isOnTheFly);
                                break;

                            } else if (phpClass.isInternal()) {
                                addDeprecated(manager, problems, element, "Used class '" + className + "' is marked as internal", isOnTheFly);
                                break;
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

    /**
     * Whether the name is in one of the places PHP requires a class, rather than standing alone in
     * an expression.
     *
     * <p>A name on its own is a constant fetch, not a class - {@code {\PHP_EOL}} is the spelling
     * the Latte documentation gives for printing a constant, and it was reported as a class that
     * does not exist. The parser has one element for both, so the surroundings are what separates
     * them: a {@code ::} after the name, a {@code new} or {@code instanceof} in front of it, a
     * declared type, or the argument of {@code {templateType}}. Everywhere else the plugin cannot
     * tell a class from a constant and says nothing.
     */
    private static boolean isClassPosition(@NotNull PsiElement reference) {
        // {varType App\Model\Article $article}, {var App\Model\Article $a = $b} and every other
        // written-out type: the parser wraps those in a type of their own.
        PsiElement parent = reference.getParent();
        if (parent instanceof LattePhpTypePart) {
            return true;
        }

        // {templateType App\FooTemplate} - a bare name, but the tag says what it is.
        if (LatteUtil.matchParentMacroName(reference, "templateType")) {
            return true;
        }

        // App\Model\Article::STATUS, ::class, ::method() - the name is followed by the access.
        if (PsiTreeUtil.skipWhitespacesAndCommentsForward(parent) != null) {
            return true;
        }

        // new App\Model\Article, $x instanceof App\Model\Article - the keyword stands in front of
        // the statement the name was parsed into, not in front of the name.
        PsiElement statement = parent == null ? null : parent.getParent();
        PsiElement previous = statement == null ? null : PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement);
        if (previous == null) {
            return false;
        }

        IElementType type = previous.getNode().getElementType();
        return type == LatteTypes.T_PHP_NEW || (type == LatteTypes.T_PHP_KEYWORD && previous.textMatches("instanceof"));
    }
}
