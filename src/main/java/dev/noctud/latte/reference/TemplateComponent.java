package dev.noctud.latte.reference;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import dev.noctud.latte.php.NettePhpType;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.utils.LattePresenterUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The component a template belongs to, when it belongs to one rather than to a presenter.
 *
 * <p>It matters because Nette reads a link inside a component differently: every destination of
 * {@code {link}} and {@code n:href} is a signal of that component, and {@code {control}} asks the
 * component for its subcomponents. A presenter whose name matches the component's is a confident
 * wrong answer to both.
 *
 * <p>Nothing in a template says whose it is, so only two things count as proof. A
 * {@code {templateType}} naming {@code XTemplate} where {@code X} is a component. Or the template's
 * folder: a presenter there makes it the presenter's (Nette 3.2 keeps presenters and their
 * templates together, components beside them included), and otherwise a component there makes it
 * the component's. Two components in the folder make it a component's template without saying
 * whose, which is enough to stay quiet about presenters and not enough to name a signal.
 */
public final class TemplateComponent {

	private static final String CONTROL = "\\Nette\\Application\\UI\\Control";

	private static final String PRESENTER = "\\Nette\\Application\\UI\\Presenter";

	private static final Key<CachedValue<TemplateComponent>> KEY = Key.create("latte.template.component");

	/** Cached for a template that is not a component's, which null cannot be. */
	private static final TemplateComponent NONE = new TemplateComponent(null);

	private final @Nullable PhpClass component;

	private TemplateComponent(@Nullable PhpClass component) {
		this.component = component;
	}

	/** Null when the template is a presenter's or nothing says whose it is. */
	public static @Nullable TemplateComponent of(@NotNull LatteFile file) {
		PsiFile original = file.getOriginalFile();
		LatteFile template = original instanceof LatteFile latte ? latte : file;
		TemplateComponent found = CachedValuesManager.getCachedValue(template, KEY,
			() -> CachedValueProvider.Result.create(find(template), PsiModificationTracker.MODIFICATION_COUNT));
		return found == NONE ? null : found;
	}

	/** Null when the template is a component's but more than one component could own it. */
	public @Nullable PhpClass getComponent() {
		return component;
	}

	/** The handler of a signal of this component: {@code handleLoadMore()} for {@code loadMore} and {@code loadMore!}. */
	public @Nullable Method signal(@NotNull String name) {
		String signal = StringUtil.trimEnd(name, "!");
		return component == null || signal.isEmpty() ? null : component.findMethodByName(LattePresenterUtil.signalToMethod(signal));
	}

	/** The factory of a subcomponent: {@code createComponentPager()} for {@code pager}. */
	public @Nullable Method factory(@NotNull String name) {
		return component == null ? null : component.findMethodByName("createComponent" + StringUtils.capitalize(name));
	}

	public @NotNull List<Method> factories() {
		List<Method> factories = new ArrayList<>();
		if (component != null) {
			for (Method method : component.getMethods()) {
				if (method.getName().startsWith("createComponent") && !method.getName().equals("createComponent")) {
					factories.add(method);
				}
			}
		}
		return factories;
	}

	private static @NotNull TemplateComponent find(@NotNull LatteFile template) {
		NettePhpType templateType = template.getFirstLatteTemplateType();
		if (templateType != null && !templateType.getTypes().isEmpty() && templateType.getTypes().get(0).endsWith("Template")) {
			String type = StringUtil.trimEnd(templateType.getTypes().get(0), "Template");
			Collection<PhpClass> owners = PhpIndex.getInstance(template.getProject()).getClassesByFQN(type.startsWith("\\") ? type : "\\" + type);
			if (owners.size() == 1) {
				PhpClass owner = owners.iterator().next();
				if (inherits(owner, PRESENTER)) {
					return NONE;
				}
				if (inherits(owner, CONTROL)) {
					return new TemplateComponent(owner);
				}
			}
		}

		PsiDirectory folder = template.getContainingDirectory();
		if (folder == null) {
			return NONE;
		}
		List<PhpClass> components = new ArrayList<>();
		for (PsiFile file : folder.getFiles()) {
			if (!(file instanceof PhpFile)) {
				continue;
			}
			for (PhpClass cls : PsiTreeUtil.findChildrenOfType(file, PhpClass.class)) {
				if (inherits(cls, PRESENTER)) {
					return NONE;
				}
				if (!cls.isAbstract() && !cls.isInterface() && inherits(cls, CONTROL)) {
					components.add(cls);
				}
			}
		}
		if (components.isEmpty()) {
			return NONE;
		}
		return new TemplateComponent(components.size() == 1 ? components.get(0) : null);
	}

	private static boolean inherits(@NotNull PhpClass cls, @NotNull String fqn) {
		Set<String> seen = new HashSet<>();
		for (PhpClass parent = cls.getSuperClass(); parent != null && seen.add(parent.getFQN()); parent = parent.getSuperClass()) {
			if (parent.getFQN().equals(fqn)) {
				return true;
			}
		}
		return false;
	}
}
