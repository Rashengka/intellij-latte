package dev.noctud.latte.php;

import com.intellij.openapi.project.Project;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class NettePhpType {
    private static final Map<String, NettePhpType> instances = new HashMap<>();

    final private static String[] nativeClassConstants = new String[]{"class"};

    /**
     * The words a type can be made of that name no class.
     *
     * <p>{@code void}, {@code never} and {@code false} are here for the reason the rest are: a
     * word that is not on this list is read as a class name, and {@code {varType void $a}} used to
     * come out as the class {@code \void}. Nothing found it, so nothing was reported - but the
     * type shown was wrong and the settings table called it an undefined class.
     *
     * <p>{@code true} is not here and does not need to be: Latte's token set has
     * {@code Php_False} and no {@code Php_True}, so a type of {@code true} is refused before the
     * plugin ever sees it.
     */
    final private static String[] nativeTypeHints = new String[]{"string", "int", "bool", "object", "float", "array", "mixed", "null", "callable", "iterable", "void", "never", "false", "resource"};

    final private static String[] nativeIterableTypeHints = new String[]{"array", "iterable"};

    final private static String[] magicMethods = new String[]{"__construct", "__callstatic", "__call", "__get", "__isset", "__clone", "__set", "__unset"};

    final public static NettePhpType MIXED = new NettePhpType("mixed");
    final public static NettePhpType STRING = new NettePhpType("string");
    final public static NettePhpType INT = new NettePhpType("int");
    final public static NettePhpType BOOL = new NettePhpType("bool");
    final public static NettePhpType OBJECT = new NettePhpType("object");
    final public static NettePhpType FLOAT = new NettePhpType("float");
    final public static NettePhpType ARRAY = new NettePhpType("array");
    final public static NettePhpType NULL = new NettePhpType("null");
    final public static NettePhpType CALLABLE = new NettePhpType("callable");
    final public static NettePhpType ITERABLE = new NettePhpType("iterable");
    final public static NettePhpType VOID = new NettePhpType("void");
    final public static NettePhpType NEVER = new NettePhpType("never");
    final public static NettePhpType FALSE = new NettePhpType("false");
    final public static NettePhpType RESOURCE = new NettePhpType("resource");

    final private static Map<String, NettePhpType[]> nativeTypes = new HashMap<String, NettePhpType[]>() {{
        put("string", new NettePhpType[]{STRING, new NettePhpType("string|null"), new NettePhpType("string[]")});
        put("int", new NettePhpType[]{INT, new NettePhpType("int|null"), new NettePhpType("int[]")});
        put("bool", new NettePhpType[]{BOOL, new NettePhpType("bool|null"), new NettePhpType("bool[]")});
        put("object", new NettePhpType[]{OBJECT, new NettePhpType("object|null"), new NettePhpType("object[]")});
        put("float", new NettePhpType[]{FLOAT, new NettePhpType("float|null"), new NettePhpType("float[]")});
        put("array", new NettePhpType[]{ARRAY, new NettePhpType("array|null"), new NettePhpType("array[]")});
        put("mixed", new NettePhpType[]{MIXED, new NettePhpType("mixed|null"), new NettePhpType("mixed[]")});
        put("null", new NettePhpType[]{NULL, new NettePhpType("null"), new NettePhpType("null[]")});
        put("callable", new NettePhpType[]{CALLABLE, new NettePhpType("callable|null"), new NettePhpType("callable[]")});
        put("iterable", new NettePhpType[]{ITERABLE, new NettePhpType("iterable|null"), new NettePhpType("iterable[]")});
        put("void", new NettePhpType[]{VOID, new NettePhpType("void|null"), new NettePhpType("void[]")});
        put("never", new NettePhpType[]{NEVER, new NettePhpType("never|null"), new NettePhpType("never[]")});
        put("false", new NettePhpType[]{FALSE, new NettePhpType("false|null"), new NettePhpType("false[]")});
        put("resource", new NettePhpType[]{RESOURCE, new NettePhpType("resource|null"), new NettePhpType("resource[]")});
    }};

    private final @Nullable String name;
    private final List<String> types = new ArrayList<>();
    private final Map<Integer, List<String>> wholeTypes = new HashMap<>();
    private final Set<Integer> nullable = new HashSet<>();
    private final Set<Integer> mixed = new HashSet<>();
    private final Set<Integer> iterable = new HashSet<>();
    private final Set<Integer> natives = new HashSet<>();
    private final Set<Integer> objectOnly = new HashSet<>();
    private final Set<Integer> nativeObjects = new HashSet<>();
    private final Map<Integer, List<String>> classes = new HashMap<>();
    private final Map<Integer, NettePhpType> forDepth = new HashMap<>();

    public static @NotNull NettePhpType create(final @Nullable String type, boolean nullable) {
        return create(null, type, nullable);
    }

    public static @NotNull NettePhpType create(final @Nullable String name, final @Nullable String type) {
        return create(name, type, false);
    }

    public static @NotNull NettePhpType create(final @Nullable String type) {
        return create(null, type, false);
    }

    public static @NotNull NettePhpType create(final @NotNull PhpType phpType) {
        return create(null, String.join("|", phpType.getTypes()), LattePhpUtil.isNullable(phpType));
    }

    public static @NotNull NettePhpType create(final @NotNull List<String> types) {
        Set<String> temp = new LinkedHashSet<>(
            Arrays.asList(String.join("|", types).split("\\|"))
        );
        return create(null, String.join("|", temp));
    }

    public static @NotNull NettePhpType create(final @Nullable String name, final @Nullable String type, final boolean nullable) {
        if (type == null || type.trim().length() == 0) {
            return NettePhpType.MIXED;
        }

        String trimmed = type.trim().toLowerCase();
        if (isNativeTypeHint(trimmed)) {
            return nativeTypes.get(normalizeTypeHint(trimmed))[0];

        } else if (trimmed.endsWith("[]")) {
            String typeHint = trimmed.substring(0, trimmed.length() - 2);
            if (isNativeTypeHint(typeHint)) {
                return nativeTypes.get(normalizeTypeHint(typeHint))[2];
            }
        }

        String withoutNull = withoutATrailingNullMark(trimmed);
        if (withoutNull != null && isNativeTypeHint(withoutNull)) {
            return nativeTypes.get(normalizeTypeHint(withoutNull))[1];
        }

        if (!instances.containsKey(type) || instances.get(type) == null) {
            NettePhpType nettePhpType = new NettePhpType(name, type, nullable);
            if (instances.containsKey(type)) {
                instances.replace(type, nettePhpType);
            } else {
                instances.put(type, nettePhpType);
            }
        }

        NettePhpType out = instances.get(type);
        return out != null ? out : MIXED;
    }

    /**
     * The type without a trailing {@code |null}, or null when it does not end in one.
     *
     * <p>One ternary used to answer this for all three ways of writing a nullable type, and it
     * knew about two of them: it cut off the five characters of "|null" from {@code ?X} as well,
     * so a type shorter than that asked for a negative length and threw - {@code ?int} and
     * {@code ?Foo} among them. {@code ?string} is long enough to survive, computed nonsense, and
     * reached the right answer further down by another path, which is why it never looked broken.
     *
     * <p>The other two spellings are deliberately not answered here. The constructor below reads a
     * leading {@code ?} and a leading {@code null|} itself and always has, so they never needed
     * this shortcut - the {@code null|} branch of that ternary computed a hint no lookup could
     * match and its result was thrown away every time. Sending them through the table instead
     * would print the same type with the null on the other side and change what the editor says
     * about every {@code ?int} in a {@code {parameters}} tag. The crash is the defect; the order
     * is not.
     */
    private static @Nullable String withoutATrailingNullMark(@NotNull String trimmed) {
        return trimmed.endsWith(NULL_LAST) ? trimmed.substring(0, trimmed.length() - NULL_LAST.length()) : null;
    }

    private static final String NULL_LAST = "|null";

    private NettePhpType(@NotNull String type) {
        this(null, type, false);
    }

    private NettePhpType(final @Nullable String name, @NotNull String typeString, final boolean nullable) {
        List<String> parts = new ArrayList<>();
        if (typeString.startsWith("?")) {
            parts.add("null");
            typeString = typeString.substring(1);
        }

        // check for iterable types
        for (String part : typeString.split("\\|")) {
            String clean = part.trim();
            if (clean.contains("<") && clean.endsWith(">")) {
                String inner = clean.substring(clean.indexOf('<') + 1, clean.length() - 1);
                String[] generics = inner.split(",", -1);
                String type = generics.length == 2 ? generics[1].trim() : generics[0].trim();
                parts.add(type + "[]");
            }
        }

        parts.addAll(Arrays.asList(typeString.split("\\|")));
        Map<Integer, Boolean> isObjectOnly = new HashMap<>();
        Map<Integer, Integer> partsAtDepth = new HashMap<>();
        int totalParts = 0;
        for (String part : parts) {
            part = part.trim();
            if (part.length() == 0) {
                continue;
            }

            totalParts++;
            NettePhpType.TypePart typePart = new NettePhpType.TypePart(part);
            partsAtDepth.merge(typePart.depth, 1, Integer::sum);
            if (!typePart.isClass() && !typePart.isObject()) {
                isObjectOnly.put(typePart.depth, false);
            } else if (!isObjectOnly.containsKey(typePart.depth)) {
                isObjectOnly.put(typePart.depth, true);
            }

            if (typePart.isClass()) {
                if (!this.classes.containsKey(typePart.depth)) {
                    classes.put(typePart.depth, new ArrayList<>());
                }
                String className = typePart.arrayOf != null ? typePart.arrayOf.getPart() : typePart.getPart();
                if (!this.classes.get(typePart.depth).contains(className)) {
                    classes.get(typePart.depth).add(className);
                }

            } else if (typePart.isNullable()) {
                this.nullable.add(typePart.depth);

            } else if (typePart.isMixed()) {
                mixed.add(typePart.depth);

            } else if (typePart.isNative()) {
                natives.add(typePart.depth);
            }

            if (typePart.isObject()) {
                nativeObjects.add(typePart.depth);
            }

            if (typePart.isIterable()) {
                for (int i = typePart.depth - 1; i >= 0; i--) {
                    iterable.add(i);
                }
            }

            wholeTypes.putIfAbsent(typePart.depth, new ArrayList<>());
            wholeTypes.get(typePart.depth).add(typePart.getWholePart());

            types.add(typePart.getPart());
        }
        this.name = name == null ? null : LattePhpVariableUtil.normalizePhpVariable(name);

        if (nullable) {
            this.nullable.add(0);
        }

        for (int depth : isObjectOnly.keySet()) {
            if (isObjectOnly.get(depth) && partsAtDepth.getOrDefault(depth, 0) == totalParts) {
                this.objectOnly.add(depth);
            }
        }
    }

    @Nullable
    public String getName() {
        return name;
    }

    public boolean containsClasses() {
        return this.classes.containsKey(0);
    }

    public boolean contains(NettePhpType type) {
        if (isNullable() && type.isNullable() || isMixed() || (type.isOneDepthOnly() && isMixed(type.getFirstDepth()))) {
            return true;
        }
        if (types.contains("object") && type.containsObjectsOnly()) {
            return true;
        }
        if (type.isOneDepthOnly() && containsNativeObject(type.getFirstDepth()) && type.containsObjectsOnly(type.getFirstDepth())) {
            return true;
        }
        for (String part : type.getTypes()) {
            if (!types.contains(part)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasUndefinedClass(final @NotNull Project project) {
        if (!containsClasses()) {
            return false;
        }

        for (String className : findClasses()) {
            if (LattePhpUtil.getClassesByFQN(project, className).size() == 0) {
                return true;
            }
        }
        return false;
    }

    public boolean hasClass(final @NotNull String className) {
        if (!containsClasses()) {
            return false;
        }
        String normalizedName = LattePhpUtil.normalizeClassName(className);
        return classes.containsKey(0) && classes.get(0).contains(normalizedName);
    }

    public boolean hasClass(final @NotNull Collection<PhpClass> phpClasses) {
        if (!containsClasses()) {
            return false;
        }
        for (PhpClass phpClass : phpClasses) {
            if (hasClass(phpClass.getFQN())) {
                return true;
            }
        }
        return false;
    }

    public boolean isNullable() {
        return isNullable(0);
    }

    public boolean isNullable(int depth) {
        return nullable.contains(depth);
    }

    public boolean isOneDepthOnly() {
        return wholeTypes.size() == 1;
    }

    private int getFirstDepth() {
        return wholeTypes.keySet().stream().findFirst().orElse(0);
    }

    public boolean isDepthOnly(int depth) {
        return isOneDepthOnly() && wholeTypes.containsKey(depth);
    }

    public boolean isNative() {
        return isNative(0);
    }

    public boolean isNative(final int depth) {
        return natives.contains(depth);
    }

    public boolean containsObjectsOnly() {
        return containsObjectsOnly(0);
    }

    public boolean containsObjectsOnly(final int depth) {
        return objectOnly.contains(depth);
    }

    public boolean containsNativeObject() {
        return containsNativeObject(0);
    }

    public boolean containsNativeObject(final int depth) {
        return nativeObjects.contains(depth);
    }

    public boolean isMixed() {
        return isMixed(0);
    }

    public boolean isMixed(final int depth) {
        return mixed.contains(depth);
    }

    public boolean isIterable(final @NotNull Project project) {
        return isIterable(project, 0);
    }

    public boolean isIterable(final @NotNull Project project, final int depth) {
        if (iterable.contains(depth)) {
            return true;
        }

        Collection<PhpClass> classes = getPhpClasses(project, depth);
        Collection<PhpClass> iterableClasses = LattePhpUtil.getInterfacesByFQN(project, "\\iterable");

        for (PhpClass phpClass : classes) {
            for (PhpClass iterableClass : iterableClasses) {
                if (phpClass.getClass().isInstance(iterableClass)) {
                    return true;
                }
            }
        }

        return false;
    }

    boolean isIterable(final int depth) {
        return iterable.contains(depth);
    }

    boolean isIterable() {
        return isIterable(0);
    }

    @NotNull
    public Collection<PhpClass> getPhpClasses(final @NotNull Project project) {
        return getPhpClasses(project, 0);
    }

    @NotNull
    public Collection<PhpClass> getPhpClasses(final @NotNull Project project, final int depth) {
        List<PhpClass> output = new ArrayList<>();
        for (String wholeType : findClasses(depth)) {
            output.addAll(LattePhpUtil.getClassesByFQN(project, wholeType));
        }
        return output;
    }

    @NotNull
    String[] findClasses() {
        return findClasses(0);
    }

    @NotNull
    String[] findClasses(final int depth) {
        if (!this.classes.containsKey(depth)) {
            return new String[0];
        }
        return this.classes.get(depth).toArray(new String[0]);
    }

    @NotNull
    public NettePhpType withDepth(final int depth) {
        if (depth == 0) {
            return this;
        }

        NettePhpType found = forDepth.get(depth);
        if (found != null) {
            return found;
        }

        List<String> depthTypes = getTypesForDepth(depth);
        if (depthTypes.size() > 0) {
            found = new NettePhpType(String.join("|", depthTypes));
        }
        found = found == null ? NettePhpType.MIXED : found;
        forDepth.put(depth, found);
        return found;
    }

    @NotNull
    private List<String> getTypesForDepth(final int depth) {
        if (depth == 0) {
            return types;
        }

        List<String> depthTypes = new ArrayList<>();

        for (int key : wholeTypes.keySet()) {
            if (key < depth) {
                continue;
            }
            getTypesForDepth(depthTypes, depth, key - depth);
        }
        return depthTypes;
    }

    private void getTypesForDepth(final @NotNull List<String> depthTypes, final int depth, final int subDepth) {
        List<String> currentTypes = wholeTypes.get(depth + subDepth);
        if (currentTypes == null) {
            return;
        }
        depthTypes.addAll(
            currentTypes.stream()
                .map(type -> type + String.join("", Collections.nCopies(subDepth, "[]")))
                .collect(Collectors.toList())
        );
    }

    @Override
    @NotNull
    public String toString() {
        return String.join("|", types);
    }

    @NotNull
    public List<String> getTypes() {
        return Collections.unmodifiableList(types);
    }

    private static class TypePart {
        int depth = 0;

        private final String part;
        private boolean isNative = false;
        private boolean isNull = false;
        private boolean isMixed = false;
        private boolean isObject = false;
        private boolean isClass = false;
        private @Nullable
        NettePhpType.TypePart arrayOf = null;

        TypePart(@NotNull String part) {
            if (part.endsWith("[]")) {
                String[] parts = part.split("\\[]", -1);
                String type = "mixed";
                int depth = 1;
                if (parts.length > 0) {
                    type = parts[0];
                    depth = parts.length - 1;
                }
                loadArrayOf(type, depth);

            } else if (NettePhpType.isNativeTypeHint(part)) {
                part = part.toLowerCase();

                if (NettePhpType.isIterable(part)) {
                    loadArrayOf("mixed", depth + 1);
                } else if (NettePhpType.isNull(part)) {
                    this.isNull = true;
                } else if (NettePhpType.isMixed(part)) {
                    this.isMixed = true;
                } else if (NettePhpType.isObject(part)) {
                    this.isObject = true;
                }
                this.isNative = true;

            } else {
                part = LattePhpUtil.normalizeClassName(part);
                this.isClass = true;
            }
            this.part = part;
        }

        private void loadArrayOf(final @NotNull String type, final int depth) {
            this.depth = depth;
            this.arrayOf = new NettePhpType.TypePart(type);
        }

        boolean isIterable() {
            return arrayOf != null;
        }

        boolean isClass() {
            return isClass || (arrayOf != null && arrayOf.isClass());
        }

        boolean isMixed() {
            return isMixed || (arrayOf != null && arrayOf.isMixed());
        }

        boolean isObject() {
            return isObject || (arrayOf != null && arrayOf.isObject());
        }

        boolean isNative() {
            return isNative || (arrayOf != null && arrayOf.isNative());
        }

        boolean isNullable() {
            return isNull || (arrayOf != null && arrayOf.isNullable());
        }

        @NotNull
        String getPart() {
            return arrayOf == null || isNative
                ? part
                : (arrayOf.getPart() + String.join("", Collections.nCopies(depth, "[]")));
        }

        @NotNull
        String getWholePart() {
            return arrayOf == null || isNative ? part : arrayOf.getWholePart();
        }
    }

    @NotNull
    public static String[] getNativeClassConstants() {
        return nativeClassConstants;
    }

    @NotNull
    public static String[] getNativeTypeHints() {
        return nativeTypeHints;
    }

    public static boolean isNativeTypeHint(final @NotNull String value) {
        return Arrays.asList(nativeTypeHints).contains(normalizeTypeHint(value));
    }

    public static boolean isIterable(final @NotNull String value) {
        return Arrays.asList(nativeIterableTypeHints).contains(normalizeTypeHint(value));
    }

    public static boolean isNull(final @NotNull String value) {
        return "null".equals(normalizeTypeHint(value));
    }

    public static boolean isMixed(final @NotNull String value) {
        return "mixed".equals(normalizeTypeHint(value));
    }

    public static boolean isObject(final @NotNull String value) {
        return "object".equals(normalizeTypeHint(value));
    }

    public static boolean isMagicMethod(final @NotNull String value) {
        return Arrays.asList(magicMethods).contains(value.toLowerCase());
    }

    @NotNull
    public static String normalizeTypeHint(@NotNull String value) {
        value = value.toLowerCase();
        return value.startsWith("\\") ? value.substring(1) : value;
    }

}
