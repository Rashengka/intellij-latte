package dev.noctud.latte.psi;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import dev.noctud.latte.utils.LatteBlockUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The closing tag that matches nothing, where the template shows that it does match something.
 *
 * <p>The plugin gives the IDE an HTML view of a Latte file so the markup in it is checked, and the
 * IDE checks that view the way it checks any HTML. One of those checks assumes the view is the
 * document: a {@code </div>} with no {@code <div>} in front of it closes nothing. In two shapes of
 * template the view is not the document, and the report is about markup the template never
 * renders.
 *
 * <p>A template that declares a parent is not a whole document - the layout opens the element and
 * the block closes it - so each file on its own is unbalanced, and that is what it is meant to be.
 * 197 of the reports over a corpus of 2 867 templates were this one, more than every other report
 * from that layer put together, and each underlined a template that was right.
 *
 * <p>A template that opens an element in one branch of a condition and closes it in the matching
 * branch of a later one - {@code {if $a}<a href="#">{else}<em>{/if}x{if $a}</a>{else}</em>{/if}} -
 * renders either element, closed. The view holds the text of every branch at once,
 * {@code <a><em>x</a></em>}, in which {@code </a>} closes the {@code em} early and leaves
 * {@code </em>} nothing to close.
 *
 * <p>This is not an inspection being turned off, which {@code CLAUDE.md} forbids and would be the
 * wrong shape anyway - there is nothing to fix in the resolving, because the plugin cannot have
 * the document the template is a part of, nor the branch a render will take. It is a foreign check
 * narrowed to the files and places where its premise holds. The narrowing is kept as tight as it
 * can be made:
 *
 * <ul>
 *   <li>only in a Latte file,</li>
 *   <li>only this one report, recognised by what it sits on rather than by what it says: the
 *       {@code </} of a closing tag the HTML parser could not attach to anything. Reading the
 *       message would break the day the IDE is run in another language,</li>
 *   <li>and only where the template names a parent it will be rendered inside - a template that
 *       declares none, or declares {@code none}, may well be a whole document and is checked as
 *       before - or where the closing tag stands in a branch of a condition and a start tag of the
 *       same name stands in a branch of a condition before it. A closing tag with no such start
 *       tag is still reported, in a branch or not.</li>
 * </ul>
 *
 * <p>Everything else the layer says is untouched. Over the same corpus it makes thirty-three
 * reports about markup that really is broken - a missing space between attributes, a duplicated
 * one, a {@code th} closed by a {@code td} - and those are the reason the layer is worth having.
 */
public class DanglingCloseTagFilter implements HighlightInfoFilter {

    /** The tags whose content is rendered or not depending on a value. */
    private static final Set<String> CONDITIONS = Set.of("if", "ifset", "ifchanged", "switch");

    @Override
    public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
        if (file == null) {
            return true;
        }
        LatteFile latteFile = latteFileOf(file);
        if (latteFile == null) {
            return true;
        }
        PsiElement closing = aClosingTagAttachedToNothing(
            file.getViewProvider(), highlightInfo.getStartOffset(), highlightInfo.getEndOffset()
        );
        if (closing == null) {
            return true;
        }
        return !LatteBlockUtil.declaresAParent(latteFile) && !closesAnElementOpenedInABranch(latteFile, closing);
    }

    /**
     * The Latte file behind whatever is being highlighted - the file itself, or the HTML or XML
     * view of it, since the report comes from the view rather than from the template.
     */
    private static @Nullable LatteFile latteFileOf(@NotNull PsiFile file) {
        if (file instanceof LatteFile latte) {
            return latte;
        }
        PsiFile base = file.getViewProvider().getPsi(file.getViewProvider().getBaseLanguage());

        return base instanceof LatteFile latte ? latte : null;
    }

    /**
     * The {@code </} of a closing tag the parser could not attach to anything, if the report sits
     * on one. Such a tag is left as an error element holding the two characters that opened it,
     * which is a shape rather than a sentence and so survives the IDE being localised.
     *
     * <p>The report need not start on it: right after a Latte tag - {@code {else}</em>} - the range
     * reported starts on that tag. So the whole range is looked through, a position at a time,
     * asking the view again at each one: on a Latte tag the view answers with a leaf of the Latte
     * tree, and walking on from that leaf would stay in the Latte tree and never reach the
     * {@code </}.
     */
    private static @Nullable PsiElement aClosingTagAttachedToNothing(@NotNull FileViewProvider provider, int start, int end) {
        for (com.intellij.lang.Language language : new com.intellij.lang.Language[]{HTMLLanguage.INSTANCE, XMLLanguage.INSTANCE}) {
            PsiFile view = provider.getPsi(language);
            if (view == null) {
                continue;
            }
            for (int offset = start; offset < Math.max(end, start + 1); ) {
                PsiElement at = view.findElementAt(offset);
                if (at == null) {
                    break;
                }
                if (at.getNode().getElementType() == XmlTokenType.XML_END_TAG_START
                    && at.getParent() instanceof PsiErrorElement) {
                    return at;
                }
                offset = Math.max(offset + 1, at.getTextRange().getEndOffset());
            }
        }
        return null;
    }

    /**
     * Whether the closing tag stands in a branch of a condition, with a start tag of the same name
     * in a branch of a condition before it - the shape in which the two may well pair up once a
     * render has taken one branch of each.
     */
    private static boolean closesAnElementOpenedInABranch(@NotNull LatteFile latteFile, @NotNull PsiElement closing) {
        PsiElement name = PsiTreeUtil.nextLeaf(closing);
        if (name == null || name.getNode().getElementType() != XmlTokenType.XML_NAME
            || !inABranch(latteFile, closing.getTextOffset())) {
            return false;
        }
        for (XmlTag tag : PsiTreeUtil.findChildrenOfType(closing.getContainingFile(), XmlTag.class)) {
            if (tag.getTextOffset() < closing.getTextOffset()
                && tag.getName().equalsIgnoreCase(name.getText())
                && inABranch(latteFile, tag.getTextOffset())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inABranch(@NotNull LatteFile latteFile, int offset) {
        // Asked of the file, an offset in markup is answered from the HTML tree, which knows
        // nothing of the conditions; the Latte tree itself is asked instead.
        com.intellij.lang.ASTNode leaf = latteFile.getNode().findLeafElementAt(offset);
        PsiElement at = leaf == null ? null : leaf.getPsi();
        for (
            LattePairMacro macro = PsiTreeUtil.getParentOfType(at, LattePairMacro.class);
            macro != null;
            macro = PsiTreeUtil.getParentOfType(macro, LattePairMacro.class)
        ) {
            LatteMacroTag openTag = macro.getMacroOpenTag();
            if (openTag != null && CONDITIONS.contains(openTag.getMacroName())) {
                return true;
            }
        }
        return false;
    }
}
