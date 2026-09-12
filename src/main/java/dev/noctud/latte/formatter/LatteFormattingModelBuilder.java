package dev.noctud.latte.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlFormattingPolicy;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.template.formatter.AbstractXmlTemplateFormattingModelBuilder;
import dev.noctud.latte.LatteLanguage;
import dev.noctud.latte.codeStyle.LatteCodeStyleSettings;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.psi.LatteFileViewProvider;
import dev.noctud.latte.psi.LatteMacroCloseTag;
import dev.noctud.latte.psi.LatteMacroOpenTag;
import dev.noctud.latte.psi.LattePairMacro;
import dev.noctud.latte.psi.LatteUnpairedMacro;
import dev.noctud.latte.psi.LatteTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LatteFormattingModelBuilder extends AbstractXmlTemplateFormattingModelBuilder {

    @Override
    protected boolean isTemplateFile(PsiFile psiFile) {
        return psiFile instanceof LatteFile;
    }

    @Override
    public boolean isOuterLanguageElement(PsiElement psiElement) {
        return psiElement.getNode().getElementType() == LatteFileViewProvider.OUTER_LATTE;
    }

    @Override
    public boolean isMarkupLanguageElement(PsiElement psiElement) {
        return psiElement.getNode().getElementType() == LatteTypes.OUTER_HTML;
    }

    /**
     * The elements of the template between the first and the last tag in one run of HTML text, less
     * the whitespace between them.
     *
     * <p>The Latte tree models the HTML around its tags, and the text between two tags - whitespace
     * included - is a text token there, not whitespace. Handed over as a template block, a run of
     * whitespace before an element sat inside a block, where the formatter may not change it, so it
     * added a line break of its own in front of the element - one more every time it ran.
     */
    @Override
    protected @NotNull List<PsiElement> getTemplateElements(@NotNull TextRange range, @NotNull TemplateLanguageFileViewProvider viewProvider) {
        List<PsiElement> elements = new ArrayList<>();
        for (PsiElement element : super.getTemplateElements(range, viewProvider)) {
            if (element.getNode().getElementType() != LatteTypes.T_TEXT || !element.getText().isBlank()) {
                elements.add(element);
            }
            if (isMarkupLanguageElement(element)) {
                elements.addAll(tagsNoMarkupHolds(element, range, viewProvider));
            }
        }
        return elements;
    }

    /**
     * The Latte tags inside a piece of markup that no HTML element in the range holds.
     *
     * <p>A piece of markup gets no block from this side - the HTML side formats it - and the Latte
     * tags in it are picked up by the HTML element they stand in. Latte and HTML may pair the
     * markup differently, though: in {@code <tr>{if $b}</tr>{/if}...</a>} Latte pairs the row with
     * the stray closing tag and keeps the whole pair as one piece of markup, while HTML closes the
     * row inside the {@code if}. The {@code {/if}} is then inside the markup but outside every HTML
     * element, nobody gives it a block, and the formatter threw on the text around it.
     *
     * <p>Such tags are handed over here, and a pair tag in pieces - its opening and closing tag
     * each on its own - so that no block crosses the edge of an HTML element the pair straddles. A
     * tag an HTML element in the range holds is left to that element, as before; where every tag
     * is held, which is the usual case, nothing changes.
     */
    private @NotNull List<PsiElement> tagsNoMarkupHolds(
        @NotNull PsiElement markup,
        @NotNull TextRange range,
        @NotNull TemplateLanguageFileViewProvider viewProvider
    ) {
        PsiFile html = viewProvider.getPsi(viewProvider.getTemplateDataLanguage());
        if (html == null) {
            return List.of();
        }
        List<TextRange> held = new ArrayList<>();
        for (XmlTag tag : PsiTreeUtil.findChildrenOfType(html, XmlTag.class)) {
            TextRange tagRange = tag.getTextRange();
            if (range.contains(tagRange) && !tagRange.equals(range)) {
                held.add(tagRange);
            }
        }

        List<PsiElement> tags = new ArrayList<>();
        markup.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                boolean whole = element instanceof LatteUnpairedMacro
                    || ((element instanceof LatteMacroOpenTag || element instanceof LatteMacroCloseTag)
                    && element.getParent() instanceof LattePairMacro);
                if (!whole) {
                    super.visitElement(element);
                    return;
                }
                TextRange tagRange = element.getTextRange();
                if (range.contains(tagRange) && held.stream().noneMatch(one -> one.contains(tagRange))) {
                    tags.add(element);
                }
            }
        });
        return tags;
    }

    @Override
    protected Block createTemplateLanguageBlock(ASTNode astNode, CodeStyleSettings codeStyleSettings, XmlFormattingPolicy xmlFormattingPolicy, Indent indent, @Nullable Alignment alignment, @Nullable Wrap wrap) {
        return new LatteBlock(this, astNode, wrap, alignment, codeStyleSettings, xmlFormattingPolicy, indent, createSpaceBuilder(codeStyleSettings));
    }

    private static SpacingBuilder createSpaceBuilder(CodeStyleSettings settings) {
        return new SpacingBuilder(settings, LatteLanguage.INSTANCE)
            .around(TokenSet.create(LatteTypes.T_PHP_ASSIGNMENT_OPERATOR, LatteTypes.T_PHP_DEFINITION_OPERATOR))
            .spaceIf(settings.getCommonSettings(LatteLanguage.INSTANCE.getID()).SPACE_AROUND_ASSIGNMENT_OPERATORS)

            .around(LatteTypes.T_PHP_OPERATOR)
            .spaceIf(settings.getCommonSettings(LatteLanguage.INSTANCE.getID()).SPACE_AROUND_EQUALITY_OPERATORS)

            .around(LatteTypes.T_PHP_LOGIC_OPERATOR)
            .spaceIf(settings.getCommonSettings(LatteLanguage.INSTANCE.getID()).SPACE_AROUND_LOGICAL_OPERATORS)

            .around(LatteTypes.T_PHP_RELATIONAL_OPERATOR)
            .spaceIf(settings.getCommonSettings(LatteLanguage.INSTANCE.getID()).SPACE_AROUND_RELATIONAL_OPERATORS)

            .around(LatteTypes.T_PHP_BITWISE_OPERATOR)
            .spaceIf(settings.getCommonSettings(LatteLanguage.INSTANCE.getID()).SPACE_AROUND_BITWISE_OPERATORS)

            .around(LatteTypes.T_PHP_SHIFT_OPERATOR)
            .spaceIf(settings.getCommonSettings(LatteLanguage.INSTANCE.getID()).SPACE_AROUND_SHIFT_OPERATORS)

            .around(LatteTypes.T_PHP_UNARY_OPERATOR)
            .spaceIf(settings.getCommonSettings(LatteLanguage.INSTANCE.getID()).SPACE_AROUND_UNARY_OPERATOR)

            .around(LatteTypes.T_PHP_MULTIPLICATIVE_OPERATORS)
            .spaceIf(settings.getCommonSettings(LatteLanguage.INSTANCE.getID()).SPACE_AROUND_MULTIPLICATIVE_OPERATORS)

            .around(LatteTypes.T_PHP_CONCATENATION)
            .spaceIf(LatteCodeStyleSettings.SPACE_AROUND_CONCATENATION)

            .before(LatteTypes.T_MACRO_TAG_CLOSE)
            .none()

            .before(LatteTypes.T_PHP_MACRO_SEPARATOR)
            .none()

            .after(LatteTypes.T_MACRO_NAME)
            .spaces(1);
    }

}
