package dev.noctud.latte.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.ReadOnlyBlock;
import com.intellij.psi.formatter.xml.XmlFormattingPolicy;
import com.intellij.psi.tree.IElementType;
import com.intellij.xml.template.formatter.AbstractXmlTemplateFormattingModelBuilder;
import com.intellij.xml.template.formatter.TemplateLanguageBlock;
import dev.noctud.latte.psi.LatteMacroClassic;
import dev.noctud.latte.psi.LatteMacroTag;
import dev.noctud.latte.psi.LatteTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LatteBlock extends TemplateLanguageBlock {
    final private SpacingBuilder spacingBuilder;

    final private boolean isPair;

    public LatteBlock(
        AbstractXmlTemplateFormattingModelBuilder abstractTemplateLanguageFormattingModelBuilder,
        @NotNull ASTNode astNode,
        @Nullable Wrap wrap,
        @Nullable Alignment alignment,
        CodeStyleSettings codeStyleSettings,
        XmlFormattingPolicy xmlFormattingPolicy,
        Indent indent,
        SpacingBuilder spacingBuilder
    ) {
        super(abstractTemplateLanguageFormattingModelBuilder, astNode, wrap, alignment, codeStyleSettings, xmlFormattingPolicy, indent);
        this.spacingBuilder = spacingBuilder;
        if (getNode().getFirstChildNode() == null) {
            isPair = false;
        } else {
            IElementType lastType = getNode().getLastChildNode().getElementType();
            IElementType firstType = getNode().getFirstChildNode().getElementType();
            isPair = firstType == LatteTypes.MACRO_OPEN_TAG && lastType == LatteTypes.MACRO_CLOSE_TAG;
        }
    }

    /**
     * The children less the blocks that hold nothing but whitespace.
     *
     * <p>Whitespace gets a block of its own here in two ways. Inside an inline element the HTML between
     * the tags of a pair is text, and the platform hands that text over block by block - the whitespace
     * around an {@code {else}} included, as read-only blocks. And where Latte pairs two HTML tags the
     * markup does not - a row closed inside an if - the whitespace between the children of the pair is
     * Latte text. The formatter may not change what is inside a block, so where it needed a line break
     * it added one of its own next to the whitespace - one more every time it ran. Left out, the
     * whitespace is what lies between two blocks, which the formatter does change.
     */
    @Override
    protected List<Block> buildChildren() {
        List<Block> children = new ArrayList<>();
        for (Block child : super.buildChildren()) {
            if (!isWhitespaceOnly(child)) {
                children.add(child);
            }
        }
        return children;
    }

    private static boolean isWhitespaceOnly(Block block) {
        boolean text = block instanceof LatteBlock latteBlock && latteBlock.getNode().getElementType() == LatteTypes.T_TEXT;
        if (!text && !(block instanceof ReadOnlyBlock)) {
            return false;
        }
        ASTNode node = ((ASTBlock) block).getNode();
        return node != null && !node.getText().isEmpty() && node.getText().isBlank();
    }

    @NotNull
    @Override
    protected Indent getChildIndent(@NotNull ASTNode astNode) {
        if (isBellowType(astNode, LatteTypes.MACRO_CONTENT)
            && astNode.getTreePrev() != null
            && astNode.getTreePrev().getElementType() == TokenType.WHITE_SPACE) {
            return Indent.getNormalIndent();
        }
        if (!isPair || isOpening(astNode) || isClosing(astNode)) {
            return Indent.getNoneIndent();
        }
        if (!(astNode.getPsi() instanceof LatteMacroClassic)) {
            return Indent.getNormalIndent();
        }
        PsiElement el = astNode.getPsi();
        LatteMacroTag openTag = ((LatteMacroClassic) el).getOpenTag();
        if (openTag.matchMacroName("else") || openTag.matchMacroName("elseif") || openTag.matchMacroName("elseifset")) {
            return Indent.getNoneIndent();
        }
        return Indent.getNormalIndent();
    }

    @Override
    protected Spacing getSpacing(TemplateLanguageBlock templateLanguageBlock) {
        return null;
    }

    @Nullable
    @Override
    public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        return spacingBuilder.getSpacing(this, child1, child2);
    }

    private boolean isOpening(ASTNode node) {
        return isBellowType(node, LatteTypes.MACRO_OPEN_TAG);
    }

    private boolean isClosing(ASTNode node) {
        return isBellowType(node, LatteTypes.MACRO_CLOSE_TAG);
    }

    private boolean isBellowType(ASTNode node, IElementType type) {
        do {
            if (node.getElementType() == type) {
                return true;
            }
            if (node == getNode()) {
                return false;
            }
            node = node.getTreeParent();
        } while (node != null);
        return false;
    }

}
