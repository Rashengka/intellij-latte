package dev.noctud.latte.reference.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.utils.LatteBlockUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code parent} of {@code {include parent}}: the block of the same name in the template this
 * one extends.
 *
 * <p>The lookup happens here rather than where the reference is built, because building references
 * runs at moments when reaching into other files is not allowed. It resolves to nothing whenever
 * the parent template cannot be named from the sources - a link that leads nowhere is what this
 * reference replaces.
 */
public class LatteParentBlockReference extends PsiReferenceBase<PsiElement> {

    public LatteParentBlockReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement) {
        super(element, rangeInElement, true);
    }

    @Override
    public @Nullable PsiElement resolve() {
        if (!(myElement.getContainingFile() instanceof LatteFile file)) {
            return null;
        }

        String blockName = LatteBlockUtil.findEnclosingBlockName(myElement);
        return blockName == null ? null : LatteBlockUtil.findParentBlock(file, blockName);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newName) {
        return getElement();
    }
}
