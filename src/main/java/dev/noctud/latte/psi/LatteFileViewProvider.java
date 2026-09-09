package dev.noctud.latte.psi;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import dev.noctud.latte.LatteLanguage;
import dev.noctud.latte.utils.LatteHtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class LatteFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider implements TemplateLanguageFileViewProvider {

    public static LatteOuterElementType OUTER_LATTE = new LatteOuterElementType("Outer latte");
    private static final Pattern xmlContentType = Pattern.compile("^\\{contentType [^}]*xml[^}]*}.*");
    private static final int SNIFF_LENGTH = 256;
    private static IElementType templateDataElement = new LatteTemplateDataElementType(
        "Outer HTML/XML in Latte",
        LatteLanguage.INSTANCE,
        LatteHtmlUtil.HTML_TOKENS,
        OUTER_LATTE
    );

    /**
     * The data language the provider this one was copied from answers, or null in an original.
     *
     * <p>The copy used to work it out for itself and that is what took the whole pass down. When a
     * document is committed the platform asks the provider for its languages and then asks a copy
     * of it for the PSI of each one - and the two hold different files, the original the real one
     * and the copy a light one made for the commit. Halfway through an edit those two do not say
     * the same thing, so the original answered XML, the copy answered HTML, and a copy with no XML
     * root to give makes the platform refuse to parse the file at all.
     *
     * <p>Which of the two was right changed with the direction of the edit, which is why the
     * corpus run died on adding a content type and the editor on taking one away.
     */
    private final @Nullable Language inheritedDataLanguage;

    public LatteFileViewProvider(PsiManager manager, VirtualFile virtualFile, boolean eventSystemEnabled) {
        this(manager, virtualFile, eventSystemEnabled, null);
    }

    private LatteFileViewProvider(
        PsiManager manager,
        VirtualFile virtualFile,
        boolean eventSystemEnabled,
        @Nullable Language inheritedDataLanguage
    ) {
        super(manager, virtualFile, eventSystemEnabled);
        this.inheritedDataLanguage = inheritedDataLanguage;
    }

    @NotNull
    @Override
    public Language getBaseLanguage() {
        return LatteLanguage.INSTANCE;
    }

    @NotNull
    public Set<Language> getLanguages() {
        Set<Language> languages = new HashSet<>(3);
        languages.add(LatteLanguage.INSTANCE);
        languages.add(getTemplateDataLanguage());

        return languages;
    }

    /**
     * The copy is told what the original answers rather than reading the file itself. The two hold
     * different files - the original the real one, the copy a light one made for the commit - and
     * during a commit those two do not say the same thing.
     */
    @Override
    protected @NotNull MultiplePsiFilesPerDocumentFileViewProvider cloneInner(@NotNull VirtualFile fileCopy) {
        return new LatteFileViewProvider(getManager(), fileCopy, false, getTemplateDataLanguage());
    }

    @NotNull
    @Override
    public Language getTemplateDataLanguage() {
        return inheritedDataLanguage != null ? inheritedDataLanguage : languageOfTheTextItHolds();
    }

    @Nullable
    protected PsiFile createFile(@NotNull Language lang) {
        ParserDefinition parser = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
        if (parser == null) {
            return null;
        } else if (lang == XMLLanguage.INSTANCE || lang == HTMLLanguage.INSTANCE) {
            PsiFileImpl file = (PsiFileImpl) parser.createFile(this);
            file.setContentElementType(templateDataElement);
            return file;
        } else {
            return lang == this.getBaseLanguage() ? parser.createFile(this) : null;
        }
    }

    /**
     * What the text this provider holds asks for, read from the provider's own contents.
     *
     * <p>Not from the file: during a commit the file still holds the old bytes while the document
     * holds the new ones, so an original reading the file and a copy reading its own light file
     * answer differently about the same edit. {@code getContents()} is the text the PSI is being
     * built from, which is the one both sides have to agree about, and it costs no disk read.
     */
    private @NotNull Language languageOfTheTextItHolds() {
        CharSequence contents = getContents();

        return detectXmlContentType(contents.subSequence(0, Math.min(contents.length(), SNIFF_LENGTH)))
            ? XMLLanguage.INSTANCE
            : HTMLLanguage.INSTANCE;
    }

    static boolean detectXmlContentType(@NotNull CharSequence head) {
        int newline = indexOfNewline(head);
        CharSequence firstLine = newline > 0 ? head.subSequence(0, newline) : head;
        return xmlContentType.matcher(firstLine).matches();
    }

    private static int indexOfNewline(@NotNull CharSequence seq) {
        for (int i = 0, n = seq.length(); i < n; i++) {
            char c = seq.charAt(i);
            if (c == '\n' || c == '\r') return i;
        }
        return -1;
    }
}
