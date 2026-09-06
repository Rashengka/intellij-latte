package dev.noctud.latte.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.inspections.utils.LatteInspectionInfo;
import dev.noctud.latte.psi.LatteBlockName;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.psi.LatteMacroContent;
import dev.noctud.latte.psi.LatteMacroTag;
import dev.noctud.latte.psi.LatteTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A line comment inside a tag, which Latte does not have.
 *
 * <p>Its tag lexer knows exactly one comment and it is the block one. Everywhere else {@code //} is
 * two divisions and {@code #} is nothing at all, so Latte answers {@code Unexpected '/'} or
 * {@code Unexpected '#', expecting end of tag} and refuses to compile the template.
 *
 * <p>{@code {php}} is the exception, and only halfway. Latte copies its body into the compiled
 * template as PHP, so PHP's own comment applies there - but it writes
 * {@code ; return get_defined_vars();} directly after that body, and a comment with nothing under
 * it hides that as well. The template then compiles and fails when it runs, with variables the tag
 * defined missing, which is why a comment ending a {@code {php}} tag is reported rather than left
 * alone.
 *
 * <p>Three spellings look like a comment and are not: {@code //} beginning a destination means an
 * absolute path, {@code #} in a destination is an anchor, and {@code #name} where an argument
 * starts is a block. None of them reach this - a destination is lexed as one, and a block name is
 * only reported where an argument does not begin.
 */
public class CommentInTagInspection extends BaseLocalInspectionTool {

    private static final String SLASH =
        "A line comment is not part of a Latte tag; Latte reports Unexpected '/' here";

    private static final String HASH =
        "A line comment is not part of a Latte tag; Latte reports Unexpected '#' here";

    private static final String ENDS_PHP =
        "Nothing follows this comment, so it hides what Latte writes after the tag "
            + "and the template stops returning its variables";

    @NotNull
    @Override
    public String getShortName() {
        return "CommentInTag";
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
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
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof LatteMacroContent) {
                    inspect((LatteMacroContent) element, problems);
                }
                super.visitElement(element);
            }
        });
        return problems;
    }

    private void inspect(@NotNull LatteMacroContent content, @NotNull List<LatteInspectionInfo> problems) {
        boolean copiedIntoPhp = isPhpBody(content);
        List<PsiElement> leaves = leavesOf(content);

        for (int i = 0; i < leaves.size(); i++) {
            PsiElement leaf = leaves.get(i);
            if (isTwoSlashes(leaf)) {
                report(content, leaf, copiedIntoPhp, SLASH, problems);
            } else if (isSlash(leaf) && i + 1 < leaves.size() && followsDirectly(leaf, leaves.get(i + 1))) {
                report(content, leaf, copiedIntoPhp, SLASH, problems);
                i++;
            }
        }

        for (LatteBlockName name : PsiTreeUtil.findChildrenOfType(content, LatteBlockName.class)) {
            if (PsiTreeUtil.getParentOfType(name, LatteMacroContent.class) != content) {
                continue;
            }
            if (copiedIntoPhp) {
                report(content, name, true, HASH, problems);
            } else if (!startsAnArgument(name)) {
                problems.add(LatteInspectionInfo.error(name, HASH));
            }
        }
    }

    /**
     * Inside {@code {php}} the comment is PHP's own and only the last one in the tag is wrong;
     * anywhere else every one of them is.
     */
    private void report(
        @NotNull LatteMacroContent content,
        @NotNull PsiElement comment,
        boolean copiedIntoPhp,
        @NotNull String description,
        @NotNull List<LatteInspectionInfo> problems
    ) {
        if (!copiedIntoPhp) {
            problems.add(LatteInspectionInfo.error(comment, description));
        } else if (nothingFollowsOnALaterLine(content, comment)) {
            problems.add(LatteInspectionInfo.error(comment, ENDS_PHP));
        }
    }

    /** A line comment runs to the end of its line, so what saves it is a later line with code on it. */
    private static boolean nothingFollowsOnALaterLine(@NotNull LatteMacroContent content, @NotNull PsiElement comment) {
        String text = content.getText();
        int from = comment.getTextRange().getStartOffset() - content.getTextRange().getStartOffset();
        int newline = text.indexOf('\n', Math.max(from, 0));

        return newline < 0 || text.substring(newline + 1).isBlank();
    }

    private static boolean startsAnArgument(@NotNull LatteBlockName name) {
        for (PsiElement before = name.getPrevSibling(); before != null; before = before.getPrevSibling()) {
            if (before.getText().isBlank()) {
                continue;
            }
            return ",".equals(before.getText());
        }
        return true;
    }

    private static boolean isPhpBody(@NotNull LatteMacroContent content) {
        PsiElement tag = content.getParent();

        return tag instanceof LatteMacroTag && ((LatteMacroTag) tag).matchMacroName("php");
    }

    private static boolean isTwoSlashes(@NotNull PsiElement leaf) {
        return leaf.getNode().getElementType() == LatteTypes.T_MACRO_ARGS && "//".equals(leaf.getText());
    }

    private static boolean isSlash(@NotNull PsiElement leaf) {
        return leaf.getNode().getElementType() == LatteTypes.T_PHP_MULTIPLICATIVE_OPERATORS
            && "/".equals(leaf.getText());
    }

    /**
     * A comment is two slashes written together. Whitespace is an element of its own, so being the
     * next element is nearly enough on its own; the offsets say the rest, and say it in the terms
     * the rule is actually about.
     */
    private static boolean followsDirectly(@NotNull PsiElement first, @NotNull PsiElement second) {
        return isSlash(second)
            && first.getTextRange().getEndOffset() == second.getTextRange().getStartOffset();
    }

    /** Leaves of this content only - a tag nested inside it gets visited as the content it is. */
    @NotNull
    private static List<PsiElement> leavesOf(@NotNull LatteMacroContent content) {
        List<PsiElement> leaves = new ArrayList<>();
        collectLeaves(content, content, leaves);
        return leaves;
    }

    private static void collectLeaves(
        @NotNull PsiElement element,
        @NotNull LatteMacroContent owner,
        @NotNull List<PsiElement> leaves
    ) {
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof LatteMacroContent && child != owner) {
                continue;
            }
            if (child.getFirstChild() == null) {
                leaves.add(child);
            } else {
                collectLeaves(child, owner, leaves);
            }
        }
    }
}
