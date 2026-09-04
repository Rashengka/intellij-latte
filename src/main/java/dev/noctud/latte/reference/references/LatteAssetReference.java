package dev.noctud.latte.reference.references;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The file named by {@code {asset 'vite:assets/app.ts'}}, without the mapper name in front of it.
 *
 * <p>Which directory a mapper serves is written in the application configuration, which the plugin
 * does not read. What is left is the one thing the sources do show: whether the path names a file
 * of this project. It resolves when they do and stays unresolved when they do not, and nothing
 * anywhere reports the difference - an asset appears in a project after the template that uses it,
 * not before.
 */
public class LatteAssetReference extends PsiReferenceBase<PsiElement> {

    public LatteAssetReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement) {
        super(element, rangeInElement, true);
    }

    @Override
    public @Nullable PsiElement resolve() {
        String path = getValue();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty()) {
            return null;
        }

        for (VirtualFile root : ProjectRootManager.getInstance(myElement.getProject()).getContentRoots()) {
            VirtualFile file = root.findFileByRelativePath(path);
            if (file != null && file.isValid() && !file.isDirectory()) {
                return PsiManager.getInstance(myElement.getProject()).findFile(file);
            }
        }

        return null;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newName) {
        return getElement();
    }
}
