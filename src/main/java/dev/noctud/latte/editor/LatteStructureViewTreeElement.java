package dev.noctud.latte.editor;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.psi.PsiElement;
import dev.noctud.latte.icons.LatteIcons;
import dev.noctud.latte.psi.LatteAutoClosedBlock;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.psi.LatteMacroClassic;
import dev.noctud.latte.psi.LatteNetteAttr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LatteStructureViewTreeElement extends PsiTreeElementBase<PsiElement> {

    public LatteStructureViewTreeElement(PsiElement psiElement) {
        super(psiElement);
    }

    @NotNull
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
        Collection<StructureViewTreeElement> elements = new ArrayList<>();
        if (getElement() == null) {
            return elements;
        }
        for (PsiElement el : getElement().getChildren()) {
            collect(el, elements);
        }
        return elements;
    }

    /**
     * A tag is listed wherever it stands - a block whose content sits in a {@code <div>} used to show
     * as empty, because a tag inside an element is not a direct child. Elements that are not tags are
     * looked through; a tag lists what is inside it itself.
     */
    private static void collect(@NotNull PsiElement element, @NotNull Collection<StructureViewTreeElement> elements) {
        if (element instanceof LatteMacroClassic || element instanceof LatteAutoClosedBlock || element instanceof LatteNetteAttr) {
            elements.add(new LatteStructureViewTreeElement(element));
            return;
        }
        for (PsiElement child : element.getChildren()) {
            collect(child, elements);
        }
    }

    @Override
    public Icon getIcon(boolean open) {
        PsiElement element = getElement();
        if (element instanceof LatteMacroClassic || element instanceof LatteAutoClosedBlock) {
            return LatteIcons.MACRO;
        } else if (element instanceof LatteNetteAttr) {
            return LatteIcons.N_TAG;
        } else if (element instanceof LatteFile) {
            return LatteIcons.FILE;
        }
        return super.getIcon(open);
    }

    @Nullable
    @Override
    public String getPresentableText() {
        PsiElement element = getElement();
        if (element instanceof LatteMacroClassic) {
            LatteMacroClassic macroClassic = (LatteMacroClassic) element;
            String presentableText = macroClassic.getOpenTag().getMacroName();
            if (macroClassic.getOpenTag().getMacroContent() != null) {
                String macroText = macroClassic.getOpenTag().getMacroContent().getText().trim();
                Pattern pattern = Pattern.compile("([\\S]+).*");
                Matcher matcher = pattern.matcher(macroText);
                if (matcher.matches()) {
                    presentableText += " " + matcher.group(1);
                }

            }
            return presentableText;
        } else if (element instanceof LatteNetteAttr) {
            return ((LatteNetteAttr) element).getAttrName().getText();
        } else if (element instanceof LatteFile) {
            return ((LatteFile) element).getName();
        }
        return "";
    }
}
