package dev.noctud.latte.psi;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTokenType;
import dev.noctud.latte.utils.LatteBlockUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The closing tag that matches nothing, in a template that says it is part of something else.
 *
 * <p>The plugin gives the IDE an HTML view of a Latte file so the markup in it is checked, and the
 * IDE checks that view the way it checks any HTML. One of those checks assumes the file is a whole
 * document: a {@code </div>} with no {@code <div>} in front of it closes nothing. A template that
 * declares a parent is not a whole document - the layout opens the element and the block closes
 * it - so each file on its own is unbalanced, and that is what it is meant to be.
 *
 * <p>197 of the reports over a corpus of 2 867 templates were this one, more than every other
 * report from that layer put together, and each underlined a template that was right.
 *
 * <p>This is not an inspection being turned off, which {@code CLAUDE.md} forbids and would be the
 * wrong shape anyway - there is nothing to fix in the resolving, because the plugin cannot have
 * the document the template is a part of. It is a foreign check narrowed to the files where its
 * premise holds. The narrowing is kept as tight as it can be made:
 *
 * <ul>
 *   <li>only in a Latte file,</li>
 *   <li>only where the template names a parent it will be rendered inside - a template that
 *       declares none, or declares {@code none}, may well be a whole document and is checked as
 *       before,</li>
 *   <li>only this one report, recognised by what it sits on rather than by what it says: the
 *       {@code </} of a closing tag the HTML parser could not attach to anything. Reading the
 *       message would break the day the IDE is run in another language.</li>
 * </ul>
 *
 * <p>Everything else the layer says is untouched. Over the same corpus it makes thirty-three
 * reports about markup that really is broken - a missing space between attributes, a duplicated
 * one, a {@code th} closed by a {@code td} - and those are the reason the layer is worth having.
 */
public class DanglingCloseTagFilter implements HighlightInfoFilter {

    @Override
    public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
        if (file == null) {
            return true;
        }
        LatteFile latteFile = latteFileOf(file);
        if (latteFile == null || !LatteBlockUtil.declaresAParent(latteFile)) {
            return true;
        }
        return !isAClosingTagAttachedToNothing(file.getViewProvider(), highlightInfo.getStartOffset());
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
     * Whether the report sits on the {@code </} of a closing tag the parser could not attach to
     * anything. Such a tag is left as an error element holding the two characters that opened it,
     * which is a shape rather than a sentence and so survives the IDE being localised.
     */
    private static boolean isAClosingTagAttachedToNothing(@NotNull FileViewProvider provider, int offset) {
        for (com.intellij.lang.Language language : new com.intellij.lang.Language[]{HTMLLanguage.INSTANCE, XMLLanguage.INSTANCE}) {
            PsiFile view = provider.getPsi(language);
            PsiElement at = view == null ? null : view.findElementAt(offset);
            if (at != null
                && at.getNode().getElementType() == XmlTokenType.XML_END_TAG_START
                && at.getParent() instanceof PsiErrorElement) {
                return true;
            }
        }
        return false;
    }
}
