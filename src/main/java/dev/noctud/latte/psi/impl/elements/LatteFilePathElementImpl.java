package dev.noctud.latte.psi.impl.elements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import dev.noctud.latte.psi.LatteTypes;
import dev.noctud.latte.psi.elements.LatteFilePathElement;
import dev.noctud.latte.psi.impl.LattePsiElementImpl;
import dev.noctud.latte.psi.impl.LattePsiImplUtil;
import dev.noctud.latte.reference.references.LatteParentBlockReference;
import dev.noctud.latte.utils.LatteBlockUtil;
import dev.noctud.latte.utils.LatteUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class LatteFilePathElementImpl extends LattePsiElementImpl implements LatteFilePathElement {
    private @Nullable PsiElement identifier = null;

    public LatteFilePathElementImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        identifier = null;
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        if (identifier == null) {
            identifier = LattePsiImplUtil.findFirstChildWithType(this, LatteTypes.T_FILE_PATH);
        }

        return identifier;
    }

    @Override
    public @NotNull String getFilePath() {
        return this.getText();
    }

    /**
     * The platform asks for the references from several threads at once, which a lazily populated
     * field cannot survive - the second thread is handed a list the first one is still filling.
     */
    @Override
    public PsiReference @NotNull [] getReferences() {
        return CachedValuesManager.getCachedValue(
            this,
            () -> CachedValueProvider.Result.create(computeReferences(), this)
        );
    }

    private PsiReference @NotNull [] computeReferences() {
        // {include parent} and {include this} are read as file paths by the lexer, but they name
        // the block the tag stands in. A file of that name is not what a click on them should look
        // for, and pointing at one that happens to exist is worse than pointing at nothing.
        if (LatteBlockUtil.isBlockKeyword(getFilePath()) && LatteUtil.matchParentMacroName(this, "include")) {
            return getFilePath().equals(LatteBlockUtil.KEYWORD_PARENT)
                ? new PsiReference[]{new LatteParentBlockReference(this, new TextRange(0, getTextLength()))}
                : PsiReference.EMPTY_ARRAY;
        }

        List<PsiReference> references = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int textRangeIndex = 0;

        for (String entity : this.getFilePath().trim().split("/")) {
            if (!entity.isEmpty()) {
                int finalTextRangeIndex = textRangeIndex;

                references.add(new PsiReferenceBase<PsiElement>(this, new TextRange(finalTextRangeIndex, finalTextRangeIndex + entity.length()), true) {
                    private final String directoryPath = current.append('/').toString();
                    private final String path = current.append(entity).toString();

                    @Override
                    public @Nullable PsiElement resolve() {
                        PsiDirectory containingDir = myElement.getContainingFile().getContainingDirectory();
                        if (containingDir == null) {
                            return null;
                        }
                        VirtualFile virtual = relativeTo(containingDir, path);
                        if (virtual == null) {
                            return null;
                        }

                        PsiFileSystemItem fileOrDirectory = PsiManager.getInstance(myElement.getProject()).findDirectory(virtual);
                        if (fileOrDirectory == null) {
                            fileOrDirectory = PsiManager.getInstance(myElement.getProject()).findFile(virtual);
                        }

                        return fileOrDirectory;
                    }

                    @Override
                    public Object @NotNull [] getVariants() {
                        PsiDirectory variantsDir = getContainingFile().getOriginalFile().getContainingDirectory();
                        if (variantsDir == null) {
                            return new Object[0];
                        }
                        VirtualFile virtual = relativeTo(variantsDir, directoryPath);
                        if (virtual == null || !virtual.isDirectory()) {
                            return new Object[0];
                        }

                        PsiDirectory directory = PsiManager.getInstance(getProject()).findDirectory(virtual);
                        if (directory == null) {
                            return new Object[0];
                        }

                        List<PsiFileSystemItem> items = new ArrayList<>();
                        for (VirtualFile file : virtual.getChildren()) {
                            if (!file.isDirectory() && !file.getName().endsWith(".latte")) {
                                continue;
                            }

                            PsiFileSystemItem fileOrDirectory = PsiManager.getInstance(getProject()).findDirectory(file);
                            if (fileOrDirectory == null) {
                                fileOrDirectory = PsiManager.getInstance(getProject()).findFile(file);
                            }

                            items.add(fileOrDirectory);
                        }

                        return items.toArray();
                    }
                });
                textRangeIndex += entity.length() + 1;

            } else {
                textRangeIndex++;
            }
        }

        return references.toArray(new PsiReference[0]);
    }

    /**
     * The path is walked from the directory's own VirtualFile rather than looked up by a
     * {@code file://} URL built out of its path. A URL of that scheme names a file on the local
     * disk whatever filesystem the project is actually in, so the link resolved to nothing
     * everywhere else - the same defect MissingFileInspection had.
     */
    private static @Nullable VirtualFile relativeTo(@NotNull PsiDirectory directory, @NotNull String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        if (relative.endsWith("/")) {
            relative = relative.substring(0, relative.length() - 1);
        }
        VirtualFile base = directory.getVirtualFile();
        return relative.isEmpty() ? base : base.findFileByRelativePath(relative);
    }
}
