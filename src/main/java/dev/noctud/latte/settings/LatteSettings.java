package dev.noctud.latte.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import dev.noctud.latte.config.LatteConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
    name = "LattePluginSettings",
    storages = {
        @Storage("latte.xml")
    }
)
public class LatteSettings implements PersistentStateComponent<LatteSettings> {

    /**
     * The Latte line the user forced, such as "2.11", or empty to auto-detect from Composer.
     *
     * Stored as a string rather than an enum ordinal on purpose: the set of supported lines will
     * grow, and ordinals already written into latte.xml would not survive that.
     *
     * The override wins over detection unconditionally. Detection says what is installed; the
     * override says what the developer is writing for, and when those disagree the developer is
     * right - the case it exists for is writing templates against a newer Latte than the one the
     * running application still needs.
     */
    public String latteVersionOverride = "";

    public boolean enableXmlLoading = true;

    public boolean enableNette = true;

    public boolean enableNetteForms = true;

    public boolean enableDefaultVariables = true;

    public boolean enableCustomMacros = true;

    public boolean enableCustomModifiers = true;

    public boolean enableCustomFunctions = true;

    public List<LatteVariableSettings> variableSettings = new ArrayList<>();

    @Tag("customMacroSettings")
    public List<LatteTagSettings> tagSettings = new ArrayList<>();

    @Tag("customModifierSettings")
    public List<LatteFilterSettings> filterSettings = new ArrayList<>();

    @Tag("customFunctionSettings")
    public List<LatteFunctionSettings> functionSettings = new ArrayList<>();

    public static LatteSettings getInstance(Project project) {
        return project.getService(LatteSettings.class);
    }

    public boolean isEnabledSourceVendor(LatteConfiguration.Vendor vendor) {
        if (vendor == LatteConfiguration.Vendor.NETTE_APPLICATION) {
            return enableNette;
        } else if (vendor == LatteConfiguration.Vendor.NETTE_FORMS) {
            return enableNetteForms;
        }
        return true;
    }

    @Nullable
    @Override
    public LatteSettings getState() {
        // add initializing here if needed
        return this;
    }

    @Override
    public void loadState(@NotNull LatteSettings settings) {
        XmlSerializerUtil.copyBean(settings, this);
    }
}

