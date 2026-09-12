package dev.noctud.latte.editorActions;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.LatteLanguage;
import dev.noctud.latte.psi.LatteMacroTag;
import dev.noctud.latte.psi.LatteNetteAttr;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Handles individual keystrokes.
 *
 * <p>A brace opens a tag anywhere in the text, so it is closed wherever it is typed. Parentheses and
 * square brackets mean something only inside a tag or an n:attribute: they are paired there, and a
 * closing one is stepped over there - outside, in the HTML text, they are characters like any other.
 */
public class LatteTypedHandler extends TypedHandlerDelegate {

    private static final Map<Character, Character> pairs = new HashMap<Character, Character>(3);
    private static final Set<Character> chars = new HashSet<Character>(3);

    static {
        pairs.put('{', '}');
        pairs.put('(', ')');
        pairs.put('[', ']');
        chars.add('}');
        chars.add(')');
        chars.add(']');
    }

    @Override
    public @NotNull Result beforeCharTyped(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
        // ignores typing '}' before '}' - the tag's own, not one in the text around it
        if (chars.contains(charTyped) && file.getViewProvider().getLanguages().contains(LatteLanguage.INSTANCE)) {
            CaretModel caretModel = editor.getCaretModel();
            CharSequence charsSeq = editor.getDocument().getCharsSequence();
            int offset = caretModel.getOffset();
            if (offset < charsSeq.length() && charsSeq.charAt(offset) == charTyped && isInsideATag(editor, file, offset)) {
                caretModel.moveToOffset(offset + 1);
                return Result.STOP;
            }
        }
        return super.beforeCharTyped(charTyped, project, editor, file, fileType);
    }

    @Override
    public @NotNull Result charTyped(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        // auto-inserts '}' after typing '{', and ')' or ']' after typing '(' or '[' inside a tag
        if (pairs.containsKey(charTyped) && file.getViewProvider().getLanguages().contains(LatteLanguage.INSTANCE)) {
            int offset = editor.getCaretModel().getOffset();
            CharSequence charsSeq = editor.getDocument().getCharsSequence();
            Character pairChar = pairs.get(charTyped);
            if ((offset >= charsSeq.length() || charsSeq.charAt(offset) != pairChar)
                && (charTyped == '{' || isInsideATag(editor, file, offset - 1))) {
                editor.getDocument().insertString(offset, pairChar.toString());
                return Result.STOP;
            }
        }

        return super.charTyped(charTyped, project, editor, file);
    }

    private static boolean isInsideATag(@NotNull Editor editor, @NotNull PsiFile file, int offset) {
        if (offset < 0) {
            return false;
        }
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
        PsiElement element = file.getViewProvider().findElementAt(offset, LatteLanguage.INSTANCE);
        return element != null && PsiTreeUtil.getParentOfType(element, LatteMacroTag.class, LatteNetteAttr.class) != null;
    }
}
