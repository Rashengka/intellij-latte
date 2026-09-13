package dev.noctud.latte.completion.resolvers;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.utils.LattePresenterUtil;

import java.util.ArrayList;
import java.util.List;

public class LinkResolver extends PresenterResolver {
    //private final HashMap<String, @Nullable PsiElement> cache = new HashMap<>();

    public LinkResolver(LatteFile file) {
        super(file);
    }

    public void reset() {
        //cache.clear();
    }

    public @Nullable PsiElement resolveAction(PhpClass presenter, String action) {
        String key = (presenter != null ? presenter.getFQN() : "null") + ":" + action;
        /*if (cache.containsKey(key)) {
            return cache.get(key);
        }*/

        PsiElement result = calculateAction(presenter, action);
        //cache.put(key, result);
        return result;
    }

    private @Nullable PsiElement calculateAction(PhpClass presenterClass, String action) {
        List<String> actions = new ArrayList<>(action.equals("this") ? guessActionName() : List.of(action));
        if (presenterClass == null) {
            List<PhpClass> matchingPresenters = getMatchingPresenters(null, false);
            if (matchingPresenters.isEmpty()) {
                return null;
            }
            for (PhpClass candidate : matchingPresenters) {
                for (String actionName : actions) {
                    Method method = findMethod(candidate, List.of("action" + StringUtils.capitalize(actionName), "render" + StringUtils.capitalize(actionName), "startup"));
                    if (method != null && (!method.getName().equals("startup") || method.getClass().getName().equals(candidate.getName()))) {
                        return method;
                    }
                }
            }

            return null;
        }

        for (String actionName : actions) {
            Method method = findMethod(presenterClass, List.of(LattePresenterUtil.actionToMethod(actionName), "render" + StringUtils.capitalize(actionName), "startup"));
            if (method != null && (!method.getName().equals("startup") || method.getClass().getName().equals(presenterClass.getName()))) {
                return method;
            }
        }

        return null;
    }

    public @Nullable PsiElement resolveSignal(PhpClass presenter, String signal) {
        String key = (presenter != null ? presenter.getFQN() : "null") + ":" + signal + "!";
        /*if (cache.containsKey(key)) {
            return cache.get(key);
        }*/

        PsiElement result = calculateSignal(presenter, signal);
        //cache.put(key, result);
        return result;
    }

    private @Nullable PsiElement calculateSignal(PhpClass presenterClass, String signal) {
        if (presenterClass == null) {
            List<PhpClass> matchingPresenters = getMatchingPresenters(null, false);
            if (matchingPresenters.isEmpty()) {
                return null;
            }
            for (PhpClass candidate : matchingPresenters) {
                PsiElement method = findMethod(candidate, List.of(LattePresenterUtil.signalToMethod(signal)));
                if (method != null) {
                    return method;
                }
            }

            return null;
        }

        return findMethod(presenterClass, List.of(LattePresenterUtil.signalToMethod(signal)));
    }

    /**
     * The folder of the module at {@code index} in {@code modules}, the modules a link names before
     * its presenter.
     *
     * <p>Nette makes every module one whole part of the presenter's namespace, bare or with the Module
     * suffix, and knows nothing about folders - those come from the autoloader. So the folder is not
     * guessed from the name: it is found by walking up from the presenter's file while every folder
     * on the way is named like its part of the namespace. When that does not hold - a mapping for a
     * single module leaves the module out of the namespace, a classmap puts the file elsewhere - the
     * folder cannot be proven and the answer is null.
     */
    public @Nullable PsiDirectory resolveModule(int index, List<String> modules, String presenter) {
        for (PhpClass candidate : getMatchingPresenters(presenter, false, modules)) {
            PsiDirectory directory = moduleDirectory(candidate, modules, index);
            if (directory != null) {
                return directory;
            }
        }

        return null;
    }

    private static @Nullable PsiDirectory moduleDirectory(PhpClass presenter, List<String> modules, int index) {
        String[] parts = StringUtils.removeStart(presenter.getFQN(), "\\").split("\\\\");
        int namespaceLength = parts.length - 1;

        int start = -1;
        for (int i = 0; i + modules.size() <= namespaceLength; i++) {
            if (holdsModules(parts, i, modules, "") || holdsModules(parts, i, modules, "Module")) {
                if (start >= 0) {
                    return null; // named twice in the namespace, no telling which
                }
                start = i;
            }
        }
        if (start < 0) {
            return null;
        }

        PsiFile file = presenter.getContainingFile();
        PsiDirectory directory = file != null ? file.getContainingDirectory() : null;
        for (int i = namespaceLength - 1; directory != null && directory.getName().equals(parts[i]); i--) {
            if (i == start + index) {
                return directory;
            }
            directory = directory.getParentDirectory();
        }

        return null;
    }

    private static boolean holdsModules(String[] parts, int start, List<String> modules, String suffix) {
        for (int i = 0; i < modules.size(); i++) {
            if (!parts[start + i].equals(modules.get(i) + suffix)) {
                return false;
            }
        }

        return true;
    }

    public List<LookupElement> getActionsForAutoComplete(PhpClass presenter) {
        List<LookupElement> actions = new ArrayList<>();
        for (Method method : presenter.getMethods()) {
            if (LattePresenterUtil.isAction(method)) {
                String className = method.getContainingClass() != null ? method.getContainingClass().getName() : presenter.getName();
                actions.add(LookupElementBuilder.create(LattePresenterUtil.methodToLink(method.getName())).withTailText(" in " + className).withIcon(AllIcons.Actions.Execute));
            }
        }

        return actions;
    }

    public List<LookupElement> getSignalsForAutoComplete(PhpClass presenter) {
        List<LookupElement> signals = new ArrayList<>();
        for (Method method : presenter.getMethods()) {
            if (LattePresenterUtil.isSignal(method) && !method.getName().equals("handleInvalidLink")) {
                String className = method.getContainingClass() != null ? method.getContainingClass().getName() : presenter.getName();
                signals.add(LookupElementBuilder.create(LattePresenterUtil.methodToLink(method.getName()) + "!").withTailText(" in " + className).withIcon(AllIcons.Actions.Lightning));
            }
        }

        return signals;
    }

    public List<String> guessActionName() {
        List<String> actions = new ArrayList<>();

        String fileName = file.getOriginalFile().getName().replace(".latte", "");

        for (String separator : List.of("-", "_")) {
            if (fileName.contains(separator)) {
                String[] parts = fileName.split(separator);
                if (parts.length > 1) {
                    actions.add(parts[1]);
                }
            }
        }

        actions.add(StringUtils.uncapitalize(fileName));

        if (!actions.contains("default")) {
            actions.add("default");
        }

        return actions;
    }
}
