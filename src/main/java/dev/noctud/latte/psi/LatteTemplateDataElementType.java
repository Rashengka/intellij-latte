package dev.noctud.latte.psi;

import com.intellij.lang.Language;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.templateLanguages.TemplateDataModifications;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LatteTemplateDataElementType extends TemplateDataElementType {

    private final TokenSet elementTypesSet;

    public LatteTemplateDataElementType(@NonNls String debugName,
                                        Language language,
                                        @NotNull TokenSet htmlTemplateElementType,
                                        @NotNull IElementType outerElementType) {
        super(debugName, language, htmlTemplateElementType.getTypes()[0], outerElementType);
        elementTypesSet = htmlTemplateElementType;
    }

    /**
     * Which parts of the template the data language is given, and which are held back as Latte.
     *
     * <p>This used to override {@code createTemplateText}, which the platform keeps only for
     * subclasses written before {@code TemplateDataModifications} existed - it checks whether a
     * subclass overrides it and takes an older path for those. That path does not know about
     * inserting anything in place of an outer range, so overriding the old method quietly ruled
     * out the one mechanism the platform has for keeping the data language parseable across a
     * hole. Same behaviour, said in the terms the platform now uses.
     *
     * <p>The assertion is kept because it names the lexer that broke, and a gap between two tokens
     * would otherwise show up much later as a range that maps to the wrong text.
     */
    @Override
    protected TemplateDataModifications collectTemplateModifications(@NotNull CharSequence sourceCode,
                                                                     @NotNull Lexer baseLexer) {
        TemplateDataModifications modifications = new TemplateDataModifications();
        baseLexer.start(sourceCode);

        TextRange currentRange = TextRange.EMPTY_RANGE;
        int tagStart = -1;
        while (baseLexer.getTokenType() != null) {
            TextRange newRange = TextRange.create(baseLexer.getTokenStart(), baseLexer.getTokenEnd());
            assert currentRange.getEndOffset() == newRange.getStartOffset() :
                "Inconsistent tokens stream from " + baseLexer +
                    ": " + getRangeDump(currentRange, sourceCode) + " followed by " + getRangeDump(newRange, sourceCode);
            currentRange = newRange;
            if (elementTypesSet.contains(baseLexer.getTokenType())) {
                modifications.addAll(appendCurrentTemplateToken(baseLexer.getTokenEnd(), sourceCode));
                tagStart = -1;
            } else {
                if (tagStart < 0) {
                    tagStart = currentRange.getStartOffset();
                    CharSequence finishing = whatTheHoleWouldCutInHalf(sourceCode, tagStart);
                    if (finishing != null) {
                        modifications.addRangeToRemove(tagStart, finishing);
                    }
                }
                modifications.addOuterRange(currentRange);
            }
            baseLexer.advance();
        }

        return modifications;
    }

    /**
     * What to put in the hole so that the data language is not left holding half a token.
     *
     * <p>A tag is taken out of the text the data language is given, which is fine where it stood
     * for a whole thing and not fine where it stood for the end of one. Two of those, both
     * measured on real templates:
     *
     * <ul>
     *   <li>{@code background-color: #{$colour}} - the hash of a colour whose digits the template
     *       was given. Without them CSS has a hash and no term.</li>
     *   <li>{@code var {$name} = 1} - a script naming one variable per item of a loop. Without a
     *       name JavaScript reads the brace that opened the tag as a binding of its own.</li>
     * </ul>
     *
     * <p>Nothing else gets anything, and that is the finding rather than caution. A word in every
     * hole was tried first and measured over 2 866 templates: it took away the six reports these
     * two shapes make and added eight of its own, because one word cannot be right everywhere CSS
     * can stand. {@code width: {$percent}%} wants a number and got a name; {@code style="{$rule}"}
     * wants a whole declaration and got a term with no colon. The only text that helps is text
     * that finishes the token that was already begun.
     */
    private static @Nullable CharSequence whatTheHoleWouldCutInHalf(@NotNull CharSequence sourceCode, int tagStart) {
        if (tagStart > 0 && sourceCode.charAt(tagStart - 1) == '#') {
            return "000000";
        }
        return followsABinding(sourceCode, tagStart) ? "aName" : null;
    }

    /** Whether a JavaScript binding keyword and its space are what stands in front of the tag. */
    private static boolean followsABinding(@NotNull CharSequence sourceCode, int tagStart) {
        int end = tagStart;
        while (end > 0 && (sourceCode.charAt(end - 1) == ' ' || sourceCode.charAt(end - 1) == '\t')) {
            end--;
        }
        if (end == tagStart) {
            return false;
        }
        for (String binding : new String[]{"var", "let", "const"}) {
            int start = end - binding.length();
            if (start >= 0 && CharSequence.compare(sourceCode.subSequence(start, end), binding) == 0
                && (start == 0 || !Character.isLetterOrDigit(sourceCode.charAt(start - 1)))) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private static String getRangeDump(@NotNull TextRange range, @NotNull CharSequence sequence) {
        return "'" + StringUtil.escapeLineBreak(range.subSequence(sequence).toString()) + "' " + range;
    }

}
