package dev.noctud.latte.php;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.psi.elements.*;
import dev.noctud.latte.psi.*;
import dev.noctud.latte.psi.elements.*;
import dev.noctud.latte.settings.LatteFunctionSettings;
import dev.noctud.latte.settings.LatteVariableSettings;
import dev.noctud.latte.utils.LattePhpCachedVariable;
import dev.noctud.latte.utils.LatteTypesUtil;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.Set;

public class LattePhpTypeDetector {

    /** A name, said to be nullable or an array of or both, and nothing else. */
    private static final Pattern A_NAME =
        Pattern.compile("\\??\\\\?[A-Za-z_][A-Za-z0-9_]*(\\\\[A-Za-z_][A-Za-z0-9_]*)*(\\[])*");

    /**
     * Words written where a class name is written that name no class and no type either.
     *
     * <p>{@code self}, {@code static} and {@code parent} name the class a type is written inside,
     * and a template is not written inside one: Latte throws the type away rather than compiling
     * it, so there is no enclosing class for them to mean. {@code {templateType X}} says where the
     * parameters come from, not what the template is a part of, and reading {@code self} as
     * {@code X} would be an invention.
     *
     * <p>{@code true} is here for a different reason: Latte's token set has {@code Php_False} and
     * no {@code Php_True}, so a type of {@code true} is refused by Latte and the plugin has no
     * meaning to give it.
     *
     * <p>The built-in types are not on this list. {@link NettePhpType#create} names them itself,
     * so passing one through invents nothing.
     */
    private static final Set<String> NOT_A_CLASS_NAME = Set.of(
        "self", "static", "parent", "true", "empty"
    );

    /**
     * Names for a type the plugin already has, and what to read them as.
     *
     * <p>A name it does not know is read as a class, so every one of these used to make a class of
     * that name - {@code \integer}, {@code \list}, {@code \numeric}. Nothing in an index is
     * called any of them, so nothing was ever reported; the type shown was simply wrong.
     *
     * <p>The hyphenated ones say something narrower than the type they are read as.
     * {@code class-string} is a string and the plugin cannot say which string, so it says string,
     * which is true and is more than the nothing it said before.
     *
     * <p>Case is where the two halves differ, and deliberately. A hyphen is not part of a PHP
     * name, so nothing spelled with one can be a class and the spelling does not matter. The plain
     * words can all be class names - {@code Integer}, {@code Resource}, {@code Numeric} are legal
     * - and are read only as written here, in lower case, which is how PHPDoc spells a type and
     * not how anybody spells a class. Reading a real class as {@code int} would take its members
     * away, which is the failure this whole area exists to remove.
     */
    private static final Map<String, String> ANOTHER_NAME_FOR = Map.ofEntries(
        // PHP's own older names for a type
        Map.entry("integer", "int"),
        Map.entry("boolean", "bool"),
        Map.entry("double", "float"),
        Map.entry("list", "array"),
        Map.entry("noreturn", "never"),
        // PHPDoc names for more than one type at once
        Map.entry("numeric", "int|float"),
        Map.entry("scalar", "bool|float|int|string"),
        // PHPDoc names for a narrower string
        Map.entry("class-string", "string"),
        Map.entry("interface-string", "string"),
        Map.entry("trait-string", "string"),
        Map.entry("enum-string", "string"),
        Map.entry("callable-string", "string"),
        Map.entry("numeric-string", "string"),
        Map.entry("non-empty-string", "string"),
        Map.entry("non-falsy-string", "string"),
        Map.entry("truthy-string", "string"),
        Map.entry("literal-string", "string"),
        Map.entry("lowercase-string", "string"),
        Map.entry("non-empty-lowercase-string", "string"),
        // a narrower int
        Map.entry("positive-int", "int"),
        Map.entry("negative-int", "int"),
        Map.entry("non-positive-int", "int"),
        Map.entry("non-negative-int", "int"),
        Map.entry("non-zero-int", "int"),
        Map.entry("int-mask", "int"),
        // a narrower array or object
        Map.entry("non-empty-array", "array"),
        Map.entry("non-empty-list", "array"),
        Map.entry("callable-array", "array"),
        Map.entry("callable-object", "object")
    );

    /** Whether a name is read whatever its case: only one that could not be a class anyway. */
    private static boolean isSpelledFreely(@NotNull String name) {
        return name.indexOf('-') >= 0;
    }

    /**
     * The same type with its name replaced by the one the plugin knows, or the text unchanged when
     * the name is not one of those. The {@code ?} and the {@code []} round it are kept: a nullable
     * {@code positive-int} is a nullable {@code int} and not a fresh question.
     */
    private static @Nullable String underAKnownName(@NotNull String text) {
        String prefix = text.startsWith("?") ? "?" : "";
        String name = text.substring(prefix.length());
        StringBuilder suffix = new StringBuilder();
        while (name.endsWith("[]")) {
            name = name.substring(0, name.length() - 2);
            suffix.append("[]");
        }
        String lowered = name.toLowerCase(java.util.Locale.ROOT);
        String known = ANOTHER_NAME_FOR.get(lowered);
        if (known == null || (!isSpelledFreely(lowered) && !name.equals(lowered))) {
            return null;
        }
        // A name for several types is an array of each of them, not an array of the last one.
        List<String> named = new ArrayList<>();
        for (String one : known.split("\\|")) {
            named.add(one + suffix);
        }
        return prefix + String.join("|", named);
    }

    private static @NotNull String stripToName(@NotNull String text) {
        String name = text.startsWith("?") ? text.substring(1) : text;
        while (name.endsWith("[]")) {
            name = name.substring(0, name.length() - 2);
        }
        return name.toLowerCase(java.util.Locale.ROOT);
    }
    public static @NotNull NettePhpType detectPhpType(@NotNull PsiElement element) {
        PsiFile file = element instanceof LattePsiElement ? ((LattePsiElement) element).getLatteFile() : element.getContainingFile();
        if (!(file instanceof LatteFile)) {
            return NettePhpType.MIXED;
        }
        return (new Detector((LatteFile) file, element)).detect();
    }

    public static @NotNull NettePhpType detectPrevPhpType(@NotNull BaseLattePhpElement element) {
        LattePhpStatementPartElement part = element.getPhpStatementPart();
        if (part == null) {
            return NettePhpType.MIXED;
        }
        part = part.getPrevPhpStatementPart();
        if (part == null || part.getPhpElement() == null) {
            return NettePhpType.MIXED;
        }
        NettePhpType type = detectPhpType(part);
        return type;
    }

    private static class Detector {
        @NotNull LatteFile file;
        @NotNull PsiElement element;
        @NotNull Project project;

        /**
         * What this walk is already inside, so that a template which defines a name from itself is
         * answered rather than followed forever.
         *
         * <p>{@code {var $a = $a}} makes the type of {@code $a} the type of the definition's
         * value, which is {@code $a}, whose last definition is that same one; asked what
         * {@code $a->child} is, the walk went round until the stack ran out - and a
         * {@code StackOverflowError} in an inspection takes the whole pass down, not just the one
         * report. Found on a real template.
         *
         * <p>Identity, not equality: two PSI elements with the same text are two elements, and the
         * question is whether this walk is standing on the same one again. Entries are removed on
         * the way out, so this detects a circle rather than remembering an answer - the same
         * element reached twice by two different routes is still worth reading the second time.
         */
        private final Set<PsiElement> visiting = Collections.newSetFromMap(new IdentityHashMap<>());

        Detector(@NotNull LatteFile file, @NotNull PsiElement element) {
            this.file = file;
            this.element = element;
            this.project = file.getProject();
        }

        @NotNull NettePhpType detect() {
            return detect(element);
        }

        /**
         * Every circle passes through here: the rules that read a variable, a method, a property or
         * a constant are private and reached only from this one place, so one guard covers them
         * all rather than each of them carrying its own.
         */
        private @NotNull NettePhpType detect(@NotNull PsiElement current) {
            if (!visiting.add(current)) {
                // A type that is defined in terms of itself is not something that can be worked
                // out, and what cannot be worked out is reported as mixed rather than guessed.
                return NettePhpType.MIXED;
            }
            try {
                return detectUnguarded(current);
            } finally {
                visiting.remove(current);
            }
        }

        private @NotNull NettePhpType detectUnguarded(@NotNull PsiElement current) {
            if (current instanceof LattePhpVariable) {
                return detect((LattePhpVariable) current).withDepth(((LattePhpVariable) current).getPhpArrayLevel());
            } else if (current instanceof LattePhpMethod) {
                return detect(((LattePhpMethod) current)).withDepth(((LattePhpMethod) current).getPhpArrayLevel());
            } else if (current instanceof LattePhpProperty) {
                return detect(((LattePhpProperty) current)).withDepth(((LattePhpProperty) current).getPhpArrayLevel());
            } else if (current instanceof LattePhpConstant) {
                return detect(((LattePhpConstant) current)).withDepth(((LattePhpConstant) current).getPhpArrayLevel());
            } else if (current instanceof LattePhpStaticVariable) {
                return detect(((LattePhpStaticVariable) current)).withDepth(((LattePhpStaticVariable) current).getPhpArrayLevel());
            } else if (current instanceof LattePhpType) {
                return ((LattePhpType) current).getReturnType(); // called from element, because type is cached in PhpType
            } else if (current instanceof LattePhpTypedPartElement) {
                LattePhpType typeElement = ((LattePhpTypedPartElement) current).getPhpType();
                if (typeElement != null) {
                    return detect(typeElement); // use detect from LattePhpType
                }
                // A type the structured rule could not read is kept as written text instead, so
                // whatever can be made of that text is made here.
                LattePhpOpaqueType written = PsiTreeUtil.getChildOfType(current, LattePhpOpaqueType.class);
                return written == null ? NettePhpType.MIXED : detect(written);
            } else if (current instanceof LattePhpClassUsage) {
                return ((LattePhpClassUsage) current).getReturnType(); // called from element, because type is cached in ClassUsage
            } else if (current instanceof LattePhpClassReference) {
                return detect(((LattePhpClassReference) current).getPhpClassUsage()); // use detect from LattePhpClassUsage
            } else if (current instanceof LattePhpNamespaceReference) {
                return ((LattePhpNamespaceReference) current).getReturnType(); // called from element, because type is cached in NamespaceReference
            } else if (current instanceof LattePhpStatement) {
                return detect((LattePhpStatement) current);
            } else if (current instanceof LattePhpStatementPartElement) {
                return detect((LattePhpStatementPartElement) current);
            } else if (current instanceof LattePhpExpression) {
                return detect((LattePhpExpression) current);
            } else if (current instanceof LattePhpExpressionElement) {
                return detect((LattePhpExpressionElement) current);
            } else if (current instanceof LattePhpArray) {
                return detect((LattePhpArray) current);
            }
            return NettePhpType.MIXED;
        }

        /**
         * Whether a typed part carries a type at all, in either of the two ways one can be
         * written down: read by the grammar, or kept as text the grammar could not read.
         *
         * <p>Asking only about the first is what kept a written type out of reach here after the
         * second appeared - the walk never even descended into the part that held it.
         */
        private boolean saysWhatItIs(@Nullable LattePhpTypedPartElement typedPart) {
            return typedPart != null
                && (typedPart.getPhpType() != null
                    || PsiTreeUtil.getChildOfType(typedPart, LattePhpOpaqueType.class) != null);
        }

        /**
         * What a written type says when the grammar could not read it.
         *
         * <p>Only a name is read, optionally nullable and optionally an array of it -
         * {@code Thing}, {@code ?Thing}, {@code Thing[]}, {@code App\Model\Thing}. That is the
         * one shape whose meaning is not in doubt, and it is the shape a class named without a
         * namespace has, which is the whole reason it could not be read before.
         *
         * <p>A word that is a built-in type rather than a name - {@code never}, {@code false},
         * {@code void} - is passed on too, because {@link NettePhpType#create} names it instead of
         * inventing a class of that name. The words on {@link #NOT_A_CLASS_NAME} are not, since
         * they name neither.
         *
         * <p>Everything else stays {@code mixed} on purpose. Reading an intersection or a generic
         * is separate work; until it is done the plugin says nothing about them, which is what it
         * does with anything it cannot work out.
         */
        private @NotNull NettePhpType detect(@NotNull LattePhpOpaqueType written) {
            String text = written.getText().replaceAll("\\s+", "");
            String known = underAKnownName(text);
            if (known != null) {
                return NettePhpType.create(known);
            }
            if (!A_NAME.matcher(text).matches() || NOT_A_CLASS_NAME.contains(stripToName(text))) {
                return NettePhpType.MIXED;
            }
            return NettePhpType.create(text);
        }

        private @NotNull NettePhpType detect(@NotNull LattePhpVariableElement variable) {
            LattePhpCachedVariable cachedVariable = variable.getCachedVariable();
            if (cachedVariable == null) {
                return NettePhpType.MIXED;
            }

            if (cachedVariable.isCaptureDefinition()) {
                return NettePhpType.STRING;

            } else if (cachedVariable.isDefinitionInForeach()) {
                PsiElement nextElement = PsiTreeUtil.skipWhitespacesForward(variable);
                IElementType type = nextElement != null ? nextElement.getNode().getElementType() : null;
                if (type != LatteTypes.T_PHP_DOUBLE_ARROW) {
                    LattePhpForeach phpForeach = PsiTreeUtil.getParentOfType(variable, LattePhpForeach.class);
                    return phpForeach != null && phpForeach.getPhpExpression().getPhpStatementList().size() > 0
                        ? detect(phpForeach.getPhpExpression()).withDepth(variable.getParent().getNode().getElementType() == LatteTypes.PHP_ARRAY_OF_VARIABLES ? 2 : 1)
                        : NettePhpType.MIXED;
                }
            }

            // Check if this variable itself has a type annotation (e.g. in {define}, {var}, {parameters} tags)
            LattePhpTypedPartElement ownTypedPart = PsiTreeUtil.getParentOfType(variable, LattePhpTypedPartElement.class);
            if (saysWhatItIs(ownTypedPart)) {
                return detect(ownTypedPart);
            }

            // For definitions, check the assignment value of the definition itself
            if (cachedVariable.isDefinition()) {
                LattePhpStatement valueStatement = cachedVariable.getNextStatement();
                if (valueStatement != null && valueStatement != (variable.getPhpStatementPart() != null ? cachedVariable.getPhpStatement() : null)) {
                    return detect(valueStatement);
                }
            }

            List<LattePhpCachedVariable> definitions = file.getCachedVariableDefinitions(variable);
            if (definitions.size() > 0) {
                LattePhpCachedVariable lastDefinition = definitions.get(definitions.size() - 1);

                LattePhpTypedPartElement typedPart = PsiTreeUtil.getParentOfType(lastDefinition.getElement(), LattePhpTypedPartElement.class);
                if (saysWhatItIs(typedPart)) {
                    return detect(typedPart);
                }

                LattePhpStatement valueStatement = lastDefinition.getNextStatement();
                if (valueStatement != null && valueStatement != (variable.getPhpStatementPart() != null ? lastDefinition.getPhpStatement() : null)) {
                    return detect(valueStatement);
                }

                if (variable != lastDefinition.getElement()) {
                    return detect(lastDefinition.getElement());
                }
            }

            NettePhpType phpType = detectVariableTypeFromTemplateType(cachedVariable);
            if (phpType != null) {
                return phpType;
            }

            NettePhpType configurationType = detectVariableTypeFromConfiguration(cachedVariable);
            if (configurationType != null) {
                return configurationType;
            }
            return detectPrimitiveType(cachedVariable);
        }

        private @NotNull NettePhpType detect(@NotNull LattePhpMethod method) {
            NettePhpType type = method.getPrevReturnType();
            Collection<PhpClass> phpClasses = type.getPhpClasses(project);
            String name = method.getMethodName();
            if (phpClasses.size() == 0) {
                LatteFunctionSettings customFunction = LatteConfiguration.getInstance(project).getFunction(name, method);
                return customFunction == null ? NettePhpType.MIXED : NettePhpType.create(customFunction.getFunctionReturnType());
            }

            // Special handling for enum::cases() method
            if (name.equals("cases")) {
                for (PhpClass phpClass : phpClasses) {
                    if (phpClass.isEnum()) {
                        // Return array of the enum type instead of UnitEnum[]
                        return NettePhpType.create(phpClass.getFQN() + "[]");
                    }
                }
            }

            List<String> types = new ArrayList<>();
            for (PhpClass phpClass : phpClasses) {
                for (Method phpMethod : phpClass.getMethods()) {
                    if (phpMethod.getName().equals(name)) {
                        String foundType = phpMethod.getType().toString();
                        for (String text : phpMethod.getType().getTypesWithParametrisedParts()) {
                            if (text.contains("<")) {
                                foundType = text;
                            }
                        }
                        types.add(foundType);
                    }
                }
            }
            return types.size() > 0 ? NettePhpType.create(types) : NettePhpType.MIXED;
        }

        private @NotNull NettePhpType detect(@NotNull LattePhpProperty property) {
            NettePhpType type = property.getPrevReturnType();
            Collection<PhpClass> phpClasses = type.getPhpClasses(project);
            String name = property.getPropertyName();

            List<String> types = new ArrayList<>();
            for (PhpClass phpClass : phpClasses) {
                for (Field field : phpClass.getFields()) {
                    if (!field.isConstant() && !field.getModifier().isStatic() && field.getModifier().isPublic() && field.getName().equals(name)) {
                        String foundType = field.getType().toString();
                        for (String text : field.getType().getTypesWithParametrisedParts()) {
                            if (text.contains("<")) {
                                foundType = text;
                            }
                        }
                        types.add(foundType);
                    }
                }
            }
            return types.size() > 0 ? NettePhpType.create(types) : NettePhpType.MIXED;
        }

        private @NotNull NettePhpType detect(@NotNull LattePhpStaticVariable property) {
            NettePhpType type = property.getPrevReturnType();
            Collection<PhpClass> phpClasses = type.getPhpClasses(project);
            String name = property.getVariableName();

            List<String> types = new ArrayList<>();
            for (PhpClass phpClass : phpClasses) {
                for (Field field : phpClass.getFields()) {
                    if (!field.isConstant() && field.getModifier().isStatic() && field.getModifier().isPublic() && field.getName().equals(name)) {
                        String foundType = field.getType().toString();
                        for (String text : field.getType().getTypesWithParametrisedParts()) {
                            if (text.contains("<")) {
                                foundType = text;
                            }
                        }
                        types.add(foundType);
                    }
                }
            }
            return types.size() > 0 ? NettePhpType.create(types) : NettePhpType.MIXED;
        }

        private @NotNull NettePhpType detect(@NotNull LattePhpConstant constant) {
            NettePhpType type = constant.getPrevReturnType();
            Collection<PhpClass> phpClasses = type.getPhpClasses(project);
            String name = constant.getConstantName();

            List<String> types = new ArrayList<>();
            for (PhpClass phpClass : phpClasses) {
                // Check for enum cases first
                if (phpClass.isEnum()) {
                    for (com.jetbrains.php.lang.psi.elements.PhpEnumCase enumCase : phpClass.getEnumCases()) {
                        if (enumCase.getName().equals(name)) {
                            // For enum cases, the type is the enum class itself
                            types.add(phpClass.getFQN());
                        }
                    }
                }

                // Check for constants
                for (Field field : phpClass.getFields()) {
                    if (field.isConstant() && field.getModifier().isPublic() && field.getName().equals(name)) {
                        String foundType = field.getType().toString();
                        for (String text : field.getType().getTypesWithParametrisedParts()) {
                            if (text.contains("<")) {
                                foundType = text;
                            }
                        }
                        types.add(foundType);
                    }
                }
            }
            return types.size() > 0 ? NettePhpType.create(types) : NettePhpType.MIXED;
        }

        private @NotNull NettePhpType detect(@NotNull LattePhpStatement statement) {
            BaseLattePhpElement last = statement.getLastPhpElement();
            return last != null ? detect(last) : NettePhpType.MIXED;
        }

        private @NotNull NettePhpType detect(@NotNull LattePhpStatementPartElement statementPart) {
            BaseLattePhpElement phpElement = statementPart.getPhpElement();
            return phpElement == null ? NettePhpType.MIXED : detect(phpElement);
        }

        private @NotNull NettePhpType detect(@NotNull LattePhpArray phpArray) {
            //phpArray.getPhpArrayDefinitionContent().getPhpArrayItemList()
            return NettePhpType.ARRAY; //todo: detect array content like int[], etc.
        }

        private @NotNull NettePhpType detect(@NotNull LattePhpExpression expression) {
            return detect((LattePhpExpressionElement) expression);
        }

        private @NotNull NettePhpType detect(@NotNull LattePhpExpressionElement expressionElement) {
            List<LattePhpStatement> statements = expressionElement.getPhpStatementList();
            if (statements.size() > 0) {
                return detect(statements.get(0));
            }
            return NettePhpType.MIXED;
        }

        @Nullable
        public NettePhpType detectVariableTypeFromTemplateType(@NotNull LattePhpCachedVariable variable) {
            NettePhpType templateType = file.getFirstLatteTemplateType();
            if (templateType == null) {
                return null;
            }

            String variableName = variable.getVariableName();
            Collection<PhpClass> classes = templateType.getPhpClasses(project);
            for (PhpClass phpClass : classes) {
                for (Field field : phpClass.getFields()) {
                    if (!field.isConstant() && field.getModifier().isPublic() && variableName.equals(field.getName())) {
                        String type = field.getType().toString();

                        for (String text : field.getType().getTypesWithParametrisedParts()) {
                            if (text.contains("<")) {
                                type = text;
                            }
                        }

                        return NettePhpType.create(field.getName(), type, LattePhpUtil.isNullable(field.getType()));
                    }
                }
            }
            return null;
        }

        @Nullable
        public NettePhpType detectVariableTypeFromConfiguration(@NotNull LattePhpCachedVariable variable) {
            LatteVariableSettings defaultVariable = LatteConfiguration.getInstance(project).getVariable(variable.getVariableName());
            if (defaultVariable != null) {
                return defaultVariable.toPhpType();
            }
            return null;
        }

        private @NotNull NettePhpType detectPrimitiveType(@NotNull LattePhpCachedVariable variable) {
            PsiElement statement = variable.getNextDefinedElement();
            if (statement == null) {
                return NettePhpType.MIXED;
            }

            List<PsiElement> otherParts = new ArrayList<>();
            if (statement instanceof LattePhpStatement) {
                statement.acceptChildren(new PsiElementVisitor() {
                    @Override
                    public void visitElement(@NotNull PsiElement element) {
                        if (!LatteTypesUtil.whitespaceTokens.contains(element.getNode().getElementType()) && !(element instanceof LatteMacroModifier)) {
                            otherParts.add(element);
                        }
                    }
                });
            } else {
                otherParts.add(statement);
            }

            if (otherParts.stream().anyMatch(element -> element instanceof LattePhpString || element.getNode().getElementType() == LatteTypes.T_PHP_CONCATENATION)) {
                return NettePhpType.STRING;

            } else if (otherParts.stream().anyMatch(element -> element.getNode().getElementType() == LatteTypes.T_MACRO_ARGS_NUMBER)) {
                return NettePhpType.INT;

            } else if (otherParts.stream().anyMatch(element -> element instanceof LattePhpArray || element instanceof LattePhpArrayOfVariables)) {
                if (otherParts.size() == 1 && otherParts.get(0) instanceof LattePhpArray) {
                    return detect((LattePhpArray) otherParts.get(0));
                }
                return NettePhpType.ARRAY;
            }
            return NettePhpType.MIXED;
        }
    }

}
