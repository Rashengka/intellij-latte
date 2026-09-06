package dev.noctud.latte.config;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.JBColor;
import dev.noctud.latte.php.LattePhpVariableUtil;
import dev.noctud.latte.version.LatteAvailability;
import dev.noctud.latte.version.LatteLanguageReference;
import dev.noctud.latte.version.LatteVersion;
import dev.noctud.latte.version.LatteVersionService;
import dev.noctud.latte.settings.*;
import dev.noctud.latte.settings.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;

public class LatteConfiguration {

    public static String LATTE_HELP_URL = "https://latte.nette.org/";
    public static String FORUM_URL = "https://forum.nette.org/";

    public enum Vendor {
        OTHER("Other", (JBColor) JBColor.GREEN.darker()),
        NETTE_APPLICATION("nette/application", JBColor.BLUE),
        NETTE_FORMS("nette/forms", JBColor.BLUE),
        LATTE("Latte", JBColor.ORANGE),
        CUSTOM("Custom", JBColor.GRAY);

        private final String name;

        private final JBColor color;

        Vendor(String name, JBColor color) {
            this.name = name;
            this.color = color;
        }

        public String getName() {
            return name;
        }

        public JBColor getColor() {
            return color;
        }
    }

    private static final Map<Project, LatteConfiguration> instances = new HashMap<>();

    @NotNull
    private final Project project;

    public LatteConfiguration(@NotNull Project project) {
        this.project = project;
    }

    public static LatteConfiguration getInstance(@NotNull Project project) {
        if (!instances.containsKey(project)) {
            instances.put(project, new LatteConfiguration(project));
        }
        return instances.get(project);
    }

    /**
     * @return tag with given name or null tag is not available
     */
    @Nullable
    public LatteTagSettings getTag(String name) {
        return getTag(name, null);
    }

    /**
     * @param context the element being looked at, which is what says which Latte version answers.
     *                Null means no version at all rather than the project's: a caller with no
     *                template in hand - a settings form asking who owns a name - is asking a
     *                question the version has no part in.
     * @return tag with given name or null tag is not available
     */
    @Nullable
    public LatteTagSettings getTag(String name, @Nullable PsiElement context) {
        return getTags(context).get(name);
    }

    /**
     * @return filter with given name or null filter is not available
     */
    @Nullable
    public LatteFilterSettings getFilter(String name) {
        return getFilter(name, null);
    }

    @Nullable
    public LatteFilterSettings getFilter(String name, @Nullable PsiElement context) {
        return findIgnoringCase(getFilters(context), name);
    }

    /**
     * Latte 2.11 matches filter names case-insensitively; Latte 3 matches them exactly. Until the
     * plugin knows which version a project uses, an exact match is tried first and a
     * case-insensitive one only as a fallback - so a name spelled the way Latte 2 allows resolves
     * instead of being reported as undefined.
     */
    @Nullable
    static <T> T findIgnoringCase(@NotNull Map<String, T> values, @Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        T exact = values.get(name);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, T> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Nullable
    public LatteFunctionSettings getFunction(String name) {
        return getFunction(name, null);
    }

    @Nullable
    public LatteFunctionSettings getFunction(String name, @Nullable PsiElement context) {
        for (LatteFunctionSettings functionSettings : getFunctions(context)) {
            if (functionSettings.getFunctionName().equals(name)) {
                return functionSettings;
            }
        }
        return null;
    }

    /**
     * @return variable with given name
     */
    @Nullable
    public LatteVariableSettings getVariable(String name) {
        name = LattePhpVariableUtil.normalizePhpVariable(name);
        for (LatteVariableSettings variable : getVariables()) {
            if (variable.getVarName().equals(name)) {
                return variable;
            }
        }
        return null;
    }

    @NotNull
    private LatteSettings getSettings() {
        return LatteSettings.getInstance(project);
    }

    @NotNull
    public Collection<LatteVariableSettings> getVariables() {
        return getVariables(true).values();
    }

    @NotNull
    public Map<String, LatteVariableSettings> getVariables(boolean enableCustom) {
        LatteSettings settings = getSettings();
        Map<String, LatteVariableSettings> variableSettings = new HashMap<>();
        if (enableCustom && settings.enableDefaultVariables && settings.variableSettings != null) {
            for (LatteVariableSettings variableSetting : settings.variableSettings) {
                variableSettings.put(variableSetting.getVarName(), variableSetting);
            }
        }

        for (Vendor vendor : getDefaultConfiguration().getVendors()) {
            if (settings.isEnabledSourceVendor(vendor)) {
                for (LatteVariableSettings variableSetting : getDefaultConfiguration().getVariables(vendor).values()) {
                    if (!variableSettings.containsKey(variableSetting.getVarName())) {
                        variableSettings.put(variableSetting.getVarName(), variableSetting);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(variableSettings);
    }

    @NotNull
    public Collection<LatteFunctionSettings> getFunctions() {
        return getFunctions((PsiElement) null);
    }

    @NotNull
    public Collection<LatteFunctionSettings> getFunctions(@Nullable PsiElement context) {
        return getFunctions(true, versionFor(context)).values();
    }

    @NotNull
    private Map<String, LatteFunctionSettings> getFunctions(boolean enableCustom, @Nullable LatteVersion version) {
        LatteSettings settings = getSettings();

        Map<String, LatteFunctionSettings> functionSettings = new HashMap<>();
        if (enableCustom && settings.enableCustomFunctions && settings.functionSettings != null) {
            for (LatteFunctionSettings functionSetting : settings.functionSettings) {
                functionSettings.put(functionSetting.getFunctionName(), functionSetting);
            }
        }

        LatteLanguageReference reference = LatteLanguageReference.getInstance();
        for (Vendor vendor : getDefaultConfiguration().getVendors()) {
            if (settings.isEnabledSourceVendor(vendor)) {
                for (LatteFunctionSettings functionSetting : getDefaultConfiguration().getFunctions(vendor).values()) {
                    String name = functionSetting.getFunctionName();
                    if (!functionSettings.containsKey(name)
                        && exists(version, reference.availabilityOfFunction(name))) {
                        functionSettings.put(name, functionSetting);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(functionSettings);
    }

    @NotNull
    public Map<String, LatteTagSettings> getTags() {
        return getTags((PsiElement) null);
    }

    @NotNull
    public Map<String, LatteTagSettings> getTags(@Nullable PsiElement context) {
        return getTags(true, versionFor(context));
    }

    @NotNull
    private Map<String, LatteTagSettings> getTags(boolean enableCustom, @Nullable LatteVersion version) {
        LatteSettings settings = getSettings();

        Map<String, LatteTagSettings> projectTags = new HashMap<>();
        if (enableCustom && settings.enableCustomMacros && settings.tagSettings != null) {
            for (LatteTagSettings tagSetting : settings.tagSettings) {
                projectTags.put(tagSetting.getMacroName(), tagSetting);
            }
        }

        LatteLanguageReference reference = LatteLanguageReference.getInstance();
        for (Vendor vendor : getDefaultConfiguration().getVendors()) {
            if (settings.isEnabledSourceVendor(vendor)) {
                for (LatteTagSettings tagSetting : getDefaultConfiguration().getTags(vendor).values()) {
                    String name = tagSetting.getMacroName();
                    if (!projectTags.containsKey(name) && exists(version, reference.availabilityOfTag(name))) {
                        projectTags.put(name, tagSetting);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(projectTags);
    }

    @NotNull
    public Map<String, LatteFilterSettings> getFilters() {
        return getFilters((PsiElement) null);
    }

    @NotNull
    public Map<String, LatteFilterSettings> getFilters(@Nullable PsiElement context) {
        return getFilters(true, versionFor(context));
    }

    @NotNull
    private Map<String, LatteFilterSettings> getFilters(boolean enableCustom, @Nullable LatteVersion version) {
        LatteSettings settings = getSettings();
        Map<String, LatteFilterSettings> projectFilters = new HashMap<>();
        if (enableCustom && settings.enableCustomModifiers && settings.filterSettings != null) {
            for (LatteFilterSettings filterSetting : settings.filterSettings) {
                projectFilters.put(filterSetting.getModifierName(), filterSetting);
            }
        }

        LatteLanguageReference reference = LatteLanguageReference.getInstance();
        for (Vendor vendor : getDefaultConfiguration().getVendors()) {
            if (settings.isEnabledSourceVendor(vendor)) {
                for (LatteFilterSettings filterSetting : getDefaultConfiguration().getFilters(vendor).values()) {
                    String name = filterSetting.getModifierName();
                    if (!projectFilters.containsKey(name) && exists(version, reference.availabilityOfFilter(name))) {
                        projectFilters.put(name, filterSetting);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(projectFilters);
    }

    @NotNull
    public VendorResult getVendorForTag(String name) {
        return getVendorForSettings(getTags(false, null).getOrDefault(name, null));
    }

    @NotNull
    public VendorResult getVendorForFilter(String name) {
        return getVendorForSettings(getFilters(false, null).getOrDefault(name, null));
    }

    @NotNull
    public VendorResult getVendorForVariable(String name) {
        return getVendorForSettings(getVariables(false).getOrDefault(name, null));
    }

    @NotNull
    public VendorResult getVendorForFunction(String name) {
        return getVendorForSettings(getFunctions(false, null).getOrDefault(name, null));
    }

    private VendorResult getVendorForSettings(BaseLatteSettings settings) {
        if (settings != null) {
            return new VendorResult(settings.getVendor(), settings.getVendorName());
        }
        return VendorResult.CUSTOM;
    }

    /**
     * Which Latte version answers for this element, or null when there is nothing to answer for.
     *
     * A null context is not "the project's version". Callers that have no template in hand keep
     * the whole registry, because the version says which Latte the templates are written against
     * and a settings form is not a template.
     */
    @Nullable
    private LatteVersion versionFor(@Nullable PsiElement context) {
        if (context == null) {
            return null;
        }
        return LatteVersionService.getInstance(project).getVersion(PsiUtilCore.getVirtualFile(context));
    }

    /**
     * Whether an item is one this Latte has. Withholding it is what makes the plugin report an
     * unknown tag or filter, so everything the tables do not positively place outside this version
     * stays - including everything they do not mention, and everything at all when the version
     * could not be established.
     */
    private static boolean exists(@Nullable LatteVersion version, @NotNull LatteAvailability availability) {
        return version == null
            || availability.covers(version, LatteLanguageReference.getInstance().getDocumentedLines());
    }

    private LatteDefaultConfiguration getDefaultConfiguration() {
        return LatteDefaultConfiguration.getInstance();
    }

    public static class VendorResult implements Serializable {
        public static VendorResult CUSTOM = new VendorResult(LatteConfiguration.Vendor.CUSTOM, "");

        public final String vendorName;
        public final LatteConfiguration.Vendor vendor;

        public VendorResult(LatteConfiguration.Vendor vendor, String vendorName) {
            this.vendor = vendor;
            this.vendorName = vendorName;
        }
    }

}
