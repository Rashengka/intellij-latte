package dev.noctud.latte.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.inspections.utils.LatteInspectionInfo;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.psi.LatteMacroTag;
import dev.noctud.latte.psi.LatteTypes;
import dev.noctud.latte.utils.LatteTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Syntax inside a tag that Latte refuses to compile.
 *
 * <p>The grammar reads the content of a tag leniently on purpose - whatever it does not recognise is
 * kept as a run of tokens rather than reported - so {@code {= [1, 2}}, {@code {= $a +}} and
 * {@code {if}} went through without a word. Tightening the grammar instead is how parsing a tag
 * once became exponential, so the checks are made here, over the tokens the lexer already gives.
 *
 * <p>Each rule stands for shapes measured against both ends of the supported range, 2.11.7 and
 * 3.1.6, and is written no wider than the measurement: what one of them takes is not reported.
 * That is why a trailing comma at the end of a tag, an empty filter argument and a {@code {switch}}
 * without a subject are all left alone. {@link TagSyntaxInspectionTest} holds all three lists.
 */
public class TagSyntaxInspection extends BaseLocalInspectionTool {

    /** The tags both ends refuse without an argument. {@code switch} is not one: 3.1.6 takes it. */
    private static final Set<String> NEED_AN_ARGUMENT = Set.of("=", "do", "if", "elseif", "while", "foreach", "for", "ifset");

    /** Operators that need an operand after them. */
    private static final TokenSet BINARY = TokenSet.create(
        LatteTypes.T_PHP_ADDITIVE_OPERATOR, LatteTypes.T_PHP_MULTIPLICATIVE_OPERATORS, LatteTypes.T_PHP_CONCATENATION,
        LatteTypes.T_PHP_LOGIC_OPERATOR, LatteTypes.T_PHP_NULL_MARK, LatteTypes.T_PHP_COLON,
        LatteTypes.T_PHP_OBJECT_OPERATOR, LatteTypes.T_PHP_DOUBLE_COLON, LatteTypes.T_PHP_DOUBLE_ARROW,
        LatteTypes.T_PHP_DEFINITION_OPERATOR, LatteTypes.T_PHP_AS
    );

    /** Operators that may not stand right after another one. A sign, + or -, may. */
    private static final TokenSet CANNOT_FOLLOW_AN_OPERATOR = TokenSet.create(
        LatteTypes.T_PHP_MULTIPLICATIVE_OPERATORS, LatteTypes.T_PHP_CONCATENATION, LatteTypes.T_PHP_LOGIC_OPERATOR
    );

    /** The operators after which that is checked. */
    private static final TokenSet ARITHMETIC = TokenSet.create(
        LatteTypes.T_PHP_ADDITIVE_OPERATOR, LatteTypes.T_PHP_MULTIPLICATIVE_OPERATORS, LatteTypes.T_PHP_CONCATENATION,
        LatteTypes.T_PHP_LOGIC_OPERATOR
    );

    private static final TokenSet OPENING = TokenSet.create(
        LatteTypes.T_PHP_LEFT_NORMAL_BRACE, LatteTypes.T_PHP_LEFT_BRACKET, LatteTypes.T_PHP_LEFT_CURLY_BRACE
    );

    private static final TokenSet CLOSING = TokenSet.create(
        LatteTypes.T_PHP_RIGHT_NORMAL_BRACE, LatteTypes.T_PHP_RIGHT_BRACKET, LatteTypes.T_PHP_RIGHT_CURLY_BRACE
    );

    private static final TokenSet QUOTES = TokenSet.create(
        LatteTypes.T_PHP_SINGLE_QUOTE_LEFT, LatteTypes.T_PHP_SINGLE_QUOTE_RIGHT,
        LatteTypes.T_PHP_DOUBLE_QUOTE_LEFT, LatteTypes.T_PHP_DOUBLE_QUOTE_RIGHT
    );

    @NotNull
    @Override
    public String getShortName() {
        return "LatteTagSyntax";
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
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof LatteMacroTag) {
                    checkTag(element, problems);
                } else {
                    super.visitElement(element);
                }
            }
        });
        return problems;
    }

    private static void checkTag(@NotNull PsiElement tag, @NotNull List<LatteInspectionInfo> problems) {
        List<PsiElement> leaves = new ArrayList<>();
        for (PsiElement leaf = PsiTreeUtil.firstChild(tag); leaf != null && PsiTreeUtil.isAncestor(tag, leaf, false); leaf = PsiTreeUtil.nextLeaf(leaf)) {
            // An element the parser left empty - the content of {if}, the value after => - has no
            // children, so the walk hands it over as a leaf. It is nothing that was written.
            if (leaf.getTextLength() > 0 && !(leaf instanceof PsiWhiteSpace)
                && !LatteTypesUtil.whitespaceTokens.contains(type(leaf)) && type(leaf) != LatteTypes.T_PHP_COMMENT) {
                leaves.add(leaf);
            }
        }
        if (leaves.isEmpty() || type(leaves.get(0)) == LatteTypes.T_MACRO_CLOSE_TAG_OPEN) {
            return;
        }

        // The name, when the tag has one, and what stands between it and the brace that ends the tag.
        int from = 1;
        String name = "";
        if (leaves.size() > 1 && (type(leaves.get(1)) == LatteTypes.T_MACRO_NAME || type(leaves.get(1)) == LatteTypes.T_MACRO_SHORTNAME)) {
            name = leaves.get(1).getText();
            from = 2;
        }
        List<PsiElement> content = new ArrayList<>();
        for (int i = from; i < leaves.size(); i++) {
            IElementType type = type(leaves.get(i));
            if (type == LatteTypes.T_MACRO_TAG_CLOSE || type == LatteTypes.T_MACRO_TAG_CLOSE_EMPTY) {
                break;
            }
            content.add(leaves.get(i));
        }

        if (content.isEmpty()) {
            if (NEED_AN_ARGUMENT.contains(name)) {
                problems.add(LatteInspectionInfo.strictError(tag, "Missing argument in tag {" + name + "}"));
            }
            return;
        }

        checkBrackets(content, problems);
        checkOperators(name, content, problems);
    }

    /** Every bracket opened in the tag is closed in it, and none is closed that was not opened. */
    private static void checkBrackets(@NotNull List<PsiElement> content, @NotNull List<LatteInspectionInfo> problems) {
        Deque<PsiElement> open = new ArrayDeque<>();
        boolean inString = false;
        for (PsiElement leaf : content) {
            IElementType type = type(leaf);
            if (QUOTES.contains(type)) {
                inString = type == LatteTypes.T_PHP_SINGLE_QUOTE_LEFT || type == LatteTypes.T_PHP_DOUBLE_QUOTE_LEFT;
                continue;
            }
            if (inString || type == LatteTypes.T_MACRO_ARGS_STRING) {
                continue;
            }
            if (OPENING.contains(type)) {
                open.push(leaf);
                continue;
            }
            // A closing bracket the lexer met before any opening one comes as plain arguments, not as
            // a bracket token - {= )} - so both spellings count.
            String closing = CLOSING.contains(type) || (type == LatteTypes.T_MACRO_ARGS && isClosingBracket(leaf.getText()))
                ? leaf.getText() : null;
            if (closing == null) {
                continue;
            }
            if (open.isEmpty() || !pairs(open.peek().getText(), closing)) {
                problems.add(LatteInspectionInfo.strictError(leaf, "Closing '" + closing + "' matches no opening one"));
                return;
            }
            open.pop();
        }
        if (!inString && !open.isEmpty()) {
            PsiElement unclosed = open.peekLast();
            problems.add(LatteInspectionInfo.strictError(unclosed, "Unclosed '" + unclosed.getText() + "'"));
        }
    }

    /** An operator has an operand on each side, as far as the tag itself can show. */
    private static void checkOperators(@NotNull String name, @NotNull List<PsiElement> content, @NotNull List<LatteInspectionInfo> problems) {
        // What follows the first filter is the filter's business: {$a|truncate:} is taken by 2.11.7.
        int end = content.size();
        for (int i = 0; i < content.size(); i++) {
            if (type(content.get(i)) == LatteTypes.T_PHP_MACRO_SEPARATOR) {
                end = i;
                break;
            }
        }
        boolean inString = false;
        for (int i = 0; i < end; i++) {
            PsiElement leaf = content.get(i);
            IElementType type = type(leaf);
            if (QUOTES.contains(type)) {
                inString = type == LatteTypes.T_PHP_SINGLE_QUOTE_LEFT || type == LatteTypes.T_PHP_DOUBLE_QUOTE_LEFT;
                continue;
            }
            if (inString) {
                continue;
            }
            PsiElement next = i + 1 < end ? content.get(i + 1) : null;

            if (i == 0 && type == LatteTypes.T_MACRO_ARGS && (leaf.getText().equals("*") || leaf.getText().equals(","))) {
                problems.add(LatteInspectionInfo.strictError(leaf, "Expression starts with '" + leaf.getText() + "'"));
                return;
            }
            if (type == LatteTypes.T_PHP_OR_INCLUSIVE && (next == null || type(next) == LatteTypes.T_PHP_COLON)) {
                problems.add(LatteInspectionInfo.strictError(leaf, "Filter after '|' has no name"));
                return;
            }
            if (!isBinary(leaf)) {
                continue;
            }
            // ?: is one operator written as two tokens; the colon answers for both.
            if (type == LatteTypes.T_PHP_NULL_MARK && next != null && type(next) == LatteTypes.T_PHP_COLON) {
                continue;
            }
            // A {var} left without its value is LatteTagVar's to report, and it does.
            if (type == LatteTypes.T_PHP_DEFINITION_OPERATOR && name.equals("var")) {
                continue;
            }
            if (next == null || (type(next) == LatteTypes.T_MACRO_ARGS && next.getText().equals(";"))) {
                problems.add(LatteInspectionInfo.strictError(leaf, "No operand after '" + leaf.getText() + "'"));
                return;
            }
            if (ARITHMETIC.contains(type) || isNullCoalescing(leaf)) {
                if (CANNOT_FOLLOW_AN_OPERATOR.contains(type(next)) || isNullCoalescing(next)) {
                    problems.add(LatteInspectionInfo.strictError(next, "Operator '" + next.getText() + "' cannot follow '" + leaf.getText() + "'"));
                    return;
                }
            }
        }
    }

    private static boolean isBinary(@NotNull PsiElement leaf) {
        IElementType type = type(leaf);
        return BINARY.contains(type) || isNullCoalescing(leaf)
            || (type == LatteTypes.T_PHP_KEYWORD && leaf.getText().equals("instanceof"));
    }

    /** ?? and the spread ... share a token type; only the first is an operator between two operands. */
    private static boolean isNullCoalescing(@NotNull PsiElement leaf) {
        return type(leaf) == LatteTypes.T_PHP_EXPRESSION && leaf.getText().equals("??");
    }

    private static boolean isClosingBracket(@NotNull String text) {
        return text.equals(")") || text.equals("]") || text.equals("}");
    }

    private static boolean pairs(@NotNull String opening, @NotNull String closing) {
        return (opening.equals("(") && closing.equals(")"))
            || (opening.equals("[") && closing.equals("]"))
            || (opening.equals("{") && closing.equals("}"));
    }

    private static IElementType type(@NotNull PsiElement leaf) {
        return leaf.getNode().getElementType();
    }
}
