package dev.noctud.latte.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor;
import dev.noctud.latte.psi.LatteFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts back the whitespace after a JavaScript brace in a template, which Latte needs to tell the
 * brace from a tag.
 *
 * <p>Latte opens a tag at a brace followed by anything but whitespace, a quote or another brace -
 * both ends of the supported range read it so. The JavaScript formatter, left to its default
 * style, writes an object literal without spaces inside the braces, and {@code var o = { a: 1 }}
 * came out as {@code var o = {a: 1}}: an unknown tag {@code {a}} to Latte. A block on one line
 * fares worse, {@code if (x) {y()}} prints the PHP call {@code y()} without any error.
 *
 * <p>What the brace belongs to does not matter to Latte, so neither does it here: every brace of
 * the JavaScript in a template, in an object, in a call, in a block, gets a space after it when
 * the character that follows would open a tag. The formatter of JavaScript is left as it is - a
 * setting would not reach a block written on one line, and a user can change a setting - and the
 * brace is recognised by the language of its leaf, so the plugin needs no JavaScript plugin to do
 * it.
 *
 * <p>The braces are found before formatting and fixed after it. Found afterwards there would be
 * nothing to find: once the formatter has closed a brace up, the text is a Latte tag, and the
 * JavaScript tree no longer holds a brace there at all. So the braces are marked in the document
 * before, the marks travel with the text through the formatter, and each is checked after.
 */
public class ScriptBraceSpacer implements PreFormatProcessor, PostFormatProcessor {

    private static final Key<List<RangeMarker>> BRACES = Key.create("latte.script.braces");

    @Override
    public @NotNull TextRange process(@NotNull ASTNode element, @NotNull TextRange range) {
        PsiFile file = element.getPsi().getContainingFile();
        if (file == null || !(file.getViewProvider() instanceof LatteFileViewProvider provider)) {
            return range;
        }
        Language javaScript = Language.findLanguageByID("JavaScript");
        PsiFile markup = provider.getPsi(provider.getTemplateDataLanguage());
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (javaScript == null || markup == null || document == null) {
            return range;
        }

        List<RangeMarker> braces = new ArrayList<>();
        markup.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement leaf) {
                if (leaf.getFirstChild() != null) {
                    super.visitElement(leaf);
                    return;
                }
                TextRange brace = leaf.getTextRange();
                if ("{".equals(leaf.getText()) && leaf.getLanguage().isKindOf(javaScript) && range.contains(brace)) {
                    braces.add(document.createRangeMarker(brace));
                }
            }
        });
        release(document);
        if (!braces.isEmpty()) {
            document.putUserData(BRACES, braces);
        }
        return range;
    }

    @Override
    public @NotNull PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
        PsiFile file = source.getContainingFile();
        if (file != null) {
            processText(file, source.getTextRange(), settings);
        }
        return source;
    }

    @Override
    public @NotNull TextRange processText(
        @NotNull PsiFile source,
        @NotNull TextRange rangeToReformat,
        @NotNull CodeStyleSettings settings
    ) {
        PsiDocumentManager documents = PsiDocumentManager.getInstance(source.getProject());
        Document document = documents.getDocument(source);
        List<RangeMarker> braces = document == null ? null : release(document);
        if (braces == null) {
            return rangeToReformat;
        }

        documents.doPostponedOperationsAndUnblockDocument(document);
        CharSequence text = document.getCharsSequence();
        List<Integer> spaces = new ArrayList<>();
        for (RangeMarker brace : braces) {
            int after = brace.getEndOffset();
            if (brace.isValid() && after < text.length() && opensATag(text.charAt(after))) {
                spaces.add(after);
            }
            brace.dispose();
        }
        if (spaces.isEmpty()) {
            return rangeToReformat;
        }

        spaces.sort(null);
        for (int i = spaces.size() - 1; i >= 0; i--) {
            document.insertString(spaces.get(i), " ");
        }
        documents.commitDocument(document);
        return rangeToReformat.grown(spaces.size());
    }

    /** Takes the marks off the document, so a formatting that is left unfinished leaves none behind. */
    private static @Nullable List<RangeMarker> release(@NotNull Document document) {
        List<RangeMarker> braces = document.getUserData(BRACES);
        document.putUserData(BRACES, null);
        return braces;
    }

    /** Whether Latte reads a brace followed by this character as the start of a tag. */
    private static boolean opensATag(char next) {
        return !Character.isWhitespace(next) && next != '\'' && next != '"' && next != '{' && next != '}';
    }
}
