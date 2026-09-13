package dev.noctud.latte.reference.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.psi.LatteNetteAttr;
import dev.noctud.latte.psi.elements.LatteLinkElement;
import dev.noctud.latte.psi.elements.LatteLinkPartElement;
import dev.noctud.latte.psi.elements.LatteMacroTagElement;
import dev.noctud.latte.reference.TemplateComponent;
import dev.noctud.latte.utils.LattePresenterUtil;

import java.util.ArrayList;
import java.util.List;

public class LatteLinkReference extends PsiReferenceBase<PsiElement> {
    private final String text;
    private final String currentPresenter;
    @NotNull
    private final List<String> previousPresenters;

    public LatteLinkReference(@NotNull PsiElement element, TextRange rangeInElement, boolean soft, String text, String currentPresenter, @NotNull List<String> previousPresenters) {
        super(element, rangeInElement, soft);
        this.text = text;
        this.currentPresenter = currentPresenter;
        this.previousPresenters = previousPresenters;
    }

    @Override
    public @Nullable PsiElement resolve() {
        LatteFile file = (LatteFile) myElement.getContainingFile();
        if (file == null || text.equals(":") || text.contains("/")) {
            return null;
        }

        TemplateComponent owner = TemplateComponent.of(file);
        if (owner != null) {
            if (!isPresenterLink()) {
                // Nette reads every destination inside a component as one of its signals, and a colon
                // as naming a subcomponent rather than a presenter.
                return destination().contains(":") || text.equals("this") ? null : owner.signal(text);
            }
            if (currentPresenter == null && previousPresenters.isEmpty()) {
                // {plink} without a presenter means the one rendering the component, which the template does not name
                return null;
            }
        }

        PhpClass presenterClass = null;
        if (currentPresenter != null) {
            presenterClass = file.getLinkResolver().resolvePresenter(currentPresenter, previousPresenters, false);

        } else if (!previousPresenters.isEmpty()) {
            String lastPrev = previousPresenters.get(previousPresenters.size() - 1);
            List<String> context = new ArrayList<>(previousPresenters.subList(0, previousPresenters.size() - 1));
            presenterClass = file.getLinkResolver().resolvePresenter(lastPrev, context, false);
        }

        if (text.endsWith("!")) {
            return file.getLinkResolver().resolveSignal(presenterClass, text.substring(0, text.length() - 1));

        } else if (!text.equals(Strings.capitalize(text))) {
            return file.getLinkResolver().resolveAction(presenterClass, text);

        } else if (text.equals(currentPresenter)) {
            return file.getLinkResolver().resolvePresenter(text, previousPresenters, false);

        } else {
            // a module: its folder, never a base presenter that merely shares the name
            int index = previousPresenters.indexOf(text);
            if (currentPresenter == null || index < 0 || index != previousPresenters.lastIndexOf(text)) {
                return null;
            }
            return file.getLinkResolver().resolveModule(index, previousPresenters, currentPresenter);
        }
    }

    @Override
    public Object @NotNull [] getVariants() {
        List<LookupElement> variants = new ArrayList<>();

        LatteFile file = (LatteFile) myElement.getContainingFile();
        if (file == null) {
            return variants.toArray();
        }

        TemplateComponent owner = TemplateComponent.of(file);
        if (owner != null && !isPresenterLink()) {
            if (owner.getComponent() != null) {
                variants.addAll(file.getLinkResolver().getSignalsForAutoComplete(owner.getComponent()));
            }
            return variants.toArray();
        }

        PhpClass presenter = null;
        if (currentPresenter != null) {
            presenter = file.getLinkResolver().resolvePresenter(currentPresenter, previousPresenters, false);
        } else if (!previousPresenters.isEmpty()) {
            String lastPrev = previousPresenters.get(previousPresenters.size() - 1);
            List<String> context = new ArrayList<>(previousPresenters.subList(0, previousPresenters.size() - 1));
            presenter = file.getLinkResolver().resolvePresenter(lastPrev, context, false);
        }

        String parentLinkText;
        PsiElement parent = myElement.getParent();
        if (parent instanceof LatteLinkElement lle) {
            parentLinkText = lle.getLink();
        } else {
            parentLinkText = parent != null ? parent.getText() : "";
        }
        String cleanLink = parentLinkText.replace("IntellijIdeaRulezzz", "");

        if (text.isEmpty() || text.equals(StringUtils.capitalize(text))) {
            String parentForAutocomplete = !previousPresenters.isEmpty() ? previousPresenters.get(previousPresenters.size() - 1) : null;
            variants.addAll(file.getLinkResolver().getPresentersForAutoComplete(parentForAutocomplete, parentLinkText.startsWith(":"), !cleanLink.trim().contains(":")));
        }

        if ((presenter == null || !presenter.isAbstract()) && (text.isEmpty() || !text.equals(StringUtils.capitalize(text))) && !cleanLink.equals(":")) {
            // a component's template has no presenter of its own to guess
            PhpClass templatePresenter = owner == null ? file.getLinkResolver().findPresenter(previousPresenters, false) : null;
            if (presenter == null && templatePresenter != null) {
                presenter = templatePresenter;
            }

            boolean isCurrent = presenter != null && templatePresenter != null && presenter.isEquivalentTo(templatePresenter);

            if (isCurrent) {
                variants.add(LookupElementBuilder.create("this").withTailText(" in " + presenter.getName()).withIcon(AllIcons.Actions.Execute));
            }

            if (presenter != null) {
                variants.addAll(file.getLinkResolver().getActionsForAutoComplete(presenter));

                if (isCurrent) {
                    variants.addAll(file.getLinkResolver().getSignalsForAutoComplete(presenter));
                }
            }
        }

        return variants.toArray();
    }

    /** {@code {plink}} links a presenter; {@code {link}} and {@code n:href} link whatever renders the template. */
    private boolean isPresenterLink() {
        PsiElement owner = PsiTreeUtil.getParentOfType(myElement, LatteMacroTagElement.class, LatteNetteAttr.class);
        return owner instanceof LatteMacroTagElement tag && tag.getMacroName().equals("plink");
    }

    private @NotNull String destination() {
        return myElement instanceof LatteLinkPartElement part ? part.getParentLink().getLink().replace("IntellijIdeaRulezzz", "") : text;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        return false; // so rename won't start rename of php classes or methods
    }

    public boolean isRefTo(@NotNull PsiElement element) {
        // Optimizations to avoid heavy resolve() calls

        if (element instanceof PhpClass cls && currentPresenter != null) {
            if (!LattePresenterUtil.isPresenter(cls.getName()) || !LattePresenterUtil.matchPresenterName(currentPresenter, cls.getName())) {
                boolean matched = false;
                for (String previousPresenter : previousPresenters) {
                    if (LattePresenterUtil.matchPresenterName(previousPresenter, cls.getName())) {
                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    return false;
                }
            }

        } else if (element instanceof Method method) {
            String methodName = LattePresenterUtil.methodToLink(method.getName());
            if (!text.startsWith(methodName) && !text.contains(":" + methodName) && !text.equals("this")) {
                return false;
            }
        }

        return super.isReferenceTo(element);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newName) {
        return getElement();
    }
}
