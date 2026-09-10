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
        while (baseLexer.getTokenType() != null) {
            TextRange newRange = TextRange.create(baseLexer.getTokenStart(), baseLexer.getTokenEnd());
            assert currentRange.getEndOffset() == newRange.getStartOffset() :
                "Inconsistent tokens stream from " + baseLexer +
                    ": " + getRangeDump(currentRange, sourceCode) + " followed by " + getRangeDump(newRange, sourceCode);
            currentRange = newRange;
            if (elementTypesSet.contains(baseLexer.getTokenType())) {
                modifications.addAll(appendCurrentTemplateToken(baseLexer.getTokenEnd(), sourceCode));
            } else {
                modifications.addOuterRange(currentRange);
            }
            baseLexer.advance();
        }

        return modifications;
    }

    @NotNull
    private static String getRangeDump(@NotNull TextRange range, @NotNull CharSequence sequence) {
        return "'" + StringUtil.escapeLineBreak(range.subSequence(sequence).toString()) + "' " + range;
    }

}
