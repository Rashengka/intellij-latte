package dev.noctud.latte.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.settings.LatteFilterSettings;
import dev.noctud.latte.settings.LatteFunctionSettings;
import dev.noctud.latte.settings.LatteSettings;
import dev.noctud.latte.settings.LatteTagSettings;
import dev.noctud.latte.settings.LatteVariableSettings;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * The four pages under Settings | Languages &amp; Frameworks | Latte are the only way to define a
 * custom tag, filter, function or variable, so a page that shows or saves the wrong thing is a
 * false-warning generator: with custom filters switched off behind the user's back every project
 * filter is reported as undefined.
 *
 * <p>The checkboxes are private fields bound by the GUI designer and no page exposes them, so the
 * tests reach them by reflection. That is deliberate - the alternative is widening production
 * fields for the tests, and what has to be checked is exactly the designer binding.
 */
public class LatteSettingsFormTest extends BasePlatformTestCase {

    private LatteSettings settings;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The light project is shared between the tests in this class, and so is its settings
        // service - every test starts from lists of its own rather than from what the last one left.
        settings = LatteSettings.getInstance(getProject());
        settings.tagSettings = new ArrayList<>();
        settings.filterSettings = new ArrayList<>();
        settings.functionSettings = new ArrayList<>();
        settings.variableSettings = new ArrayList<>();
        settings.enableCustomMacros = true;
        settings.enableCustomModifiers = true;
        settings.enableCustomFunctions = true;
        settings.enableDefaultVariables = true;
    }

    // Each page has to show the flag it saves. Custom variables are enabled throughout, so a page
    // reading that flag instead of its own would show a checkbox that is on while the feature is off.

    public void testTagPageShowsWhetherCustomTagsAreEnabled() {
        settings.enableCustomMacros = false;

        assertFalse(checkBox(new LatteCustomMacroSettingsForm(getProject()), "enableCustomMacrosCheckBox").isSelected());
    }

    public void testFilterPageShowsWhetherCustomFiltersAreEnabled() {
        settings.enableCustomModifiers = false;

        assertFalse(checkBox(new LatteCustomModifierSettingsForm(getProject()), "enableCustomModifiersCheckBox").isSelected());
    }

    public void testFunctionPageShowsWhetherCustomFunctionsAreEnabled() {
        settings.enableCustomFunctions = false;

        assertFalse(checkBox(new LatteCustomFunctionSettingsForm(getProject()), "enableCustomFunctionsCheckBox").isSelected());
    }

    public void testVariablePageShowsWhetherCustomVariablesAreEnabled() {
        settings.enableDefaultVariables = false;

        assertFalse(checkBox(new LatteVariableSettingsForm(getProject()), "enableCustomSignatureTypesCheckBox").isSelected());
    }

    // Opening a page and pressing Apply without touching anything must leave every flag alone.
    // A page seeded from the wrong flag silently copies that one over its own here.

    public void testApplyingAnUntouchedTagPageKeepsEveryFlag() throws ConfigurationException {
        settings.enableCustomMacros = false;

        new LatteCustomMacroSettingsForm(getProject()).apply();

        assertFalse(settings.enableCustomMacros);
        assertTrue(settings.enableDefaultVariables);
    }

    public void testApplyingAnUntouchedFilterPageKeepsEveryFlag() throws ConfigurationException {
        settings.enableCustomModifiers = false;

        new LatteCustomModifierSettingsForm(getProject()).apply();

        assertFalse(settings.enableCustomModifiers);
        assertTrue(settings.enableDefaultVariables);
    }

    public void testApplyingAnUntouchedFunctionPageKeepsEveryFlag() throws ConfigurationException {
        settings.enableCustomFunctions = false;

        new LatteCustomFunctionSettingsForm(getProject()).apply();

        assertFalse(settings.enableCustomFunctions);
        assertTrue(settings.enableDefaultVariables);
    }

    /**
     * A checkbox toggled with the keyboard fires an item event and no mouse event. A page that
     * notices only the mouse leaves Apply greyed out, and the change is dropped without a word.
     */
    public void testTogglingWithTheKeyboardMarksThePageModified() {
        LatteCustomModifierSettingsForm form = new LatteCustomModifierSettingsForm(getProject());

        checkBox(form, "enableCustomModifiersCheckBox").doClick();

        assertTrue(form.isModified());
    }

    public void testResetPutsTheCheckboxBack() {
        LatteCustomModifierSettingsForm form = new LatteCustomModifierSettingsForm(getProject());
        JCheckBox enabled = checkBox(form, "enableCustomModifiersCheckBox");
        enabled.doClick();

        form.reset();

        assertTrue(enabled.isSelected());
        assertFalse(form.isModified());
    }

    // The list a page shows is not the list it guards against being absent. Nothing keeps these
    // public fields non-null, and a page that reads past its own guard either shows nothing - and
    // then deletes the entries on Apply - or throws while the settings dialog is opening.

    public void testFilterPageKeepsItsFiltersWhenNoCustomTagIsDefined() throws ConfigurationException {
        settings.tagSettings = null;
        settings.filterSettings.add(new LatteFilterSettings("myFilter"));

        LatteCustomModifierSettingsForm form = new LatteCustomModifierSettingsForm(getProject());
        form.apply();

        assertEquals(1, settings.filterSettings.size());
    }

    public void testFunctionPageKeepsItsFunctionsWhenNoCustomTagIsDefined() throws ConfigurationException {
        settings.tagSettings = null;
        settings.functionSettings.add(new LatteFunctionSettings("myFunction"));

        LatteCustomFunctionSettingsForm form = new LatteCustomFunctionSettingsForm(getProject());
        form.apply();

        assertEquals(1, settings.functionSettings.size());
    }

    public void testFilterPageOpensWithoutAFilterList() {
        settings.filterSettings = null;

        assertNotNull(new LatteCustomModifierSettingsForm(getProject()).createComponent());
    }

    public void testFunctionPageOpensWithoutAFunctionList() {
        settings.functionSettings = null;

        assertNotNull(new LatteCustomFunctionSettingsForm(getProject()).createComponent());
    }

    public void testTagPageOpensWithoutATagList() {
        settings.tagSettings = null;

        assertNotNull(new LatteCustomMacroSettingsForm(getProject()).createComponent());
    }

    public void testVariablePageOpensWithoutAVariableList() {
        settings.variableSettings = null;

        assertNotNull(new LatteVariableSettingsForm(getProject()).createComponent());
    }

    // Entries defined before the dialog was opened have to come back out of it unchanged.

    public void testApplyKeepsTheEntriesThePageWasOpenedWith() throws ConfigurationException {
        settings.tagSettings.add(new LatteTagSettings("myBlock", LatteTagSettings.Type.PAIR));
        settings.variableSettings.add(new LatteVariableSettings("myVar", "\\App\\Model\\Article"));

        new LatteCustomMacroSettingsForm(getProject()).apply();
        new LatteVariableSettingsForm(getProject()).apply();

        assertEquals(1, settings.tagSettings.size());
        assertEquals("myBlock", settings.tagSettings.get(0).getMacroName());
        assertEquals(1, settings.variableSettings.size());
        assertEquals("myVar", settings.variableSettings.get(0).getVarName());
    }

    private static JCheckBox checkBox(Configurable form, String name) {
        try {
            Field field = form.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (JCheckBox) field.get(form);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("No checkbox " + name + " on " + form.getClass().getSimpleName(), e);
        }
    }

    /**
     * The version control is asserted through the settings object rather than through the combo's
     * text, because what has to survive is the stored value - a line written into latte.xml as
     * "3.0", never the position it happened to have in the list.
     */
    public void testThePageCarriesTheVersionControl() {
        LatteSettingsForm form = new LatteSettingsForm(getProject());

        JComponent page = form.createComponent();
        assertNotNull("The settings page did not build", page);
        assertNotNull("The settings page has no version control", comboIn(page));
    }

    public void testAForcedLineIsStoredAsItsOwnNameAndNotAsAPosition() throws ConfigurationException {
        LatteSettingsForm form = new LatteSettingsForm(getProject());
        JComboBox<?> combo = comboIn(form.createComponent());
        assertNotNull(combo);

        combo.setSelectedIndex(1);
        form.apply();

        assertEquals("2.11", LatteSettings.getInstance(getProject()).latteVersionOverride);
    }

    public void testAutoDetectIsTheEmptyValueAndThePageOpensOnWhatIsStored() throws ConfigurationException {
        LatteSettings.getInstance(getProject()).latteVersionOverride = "3.1";

        LatteSettingsForm form = new LatteSettingsForm(getProject());
        JComboBox<?> combo = comboIn(form.createComponent());
        assertNotNull(combo);
        assertEquals("the page has to open on what is stored", 3, combo.getSelectedIndex());

        combo.setSelectedIndex(0);
        form.apply();

        assertEquals("", LatteSettings.getInstance(getProject()).latteVersionOverride);
    }

    /**
     * Everything on this page decides what the registry answers, and a template already open is
     * parsed and highlighted from what the registry said before. Without telling the IDE, the old
     * errors stay on screen until the file is edited - and a setting that appears to do nothing is
     * indistinguishable from one that is broken.
     */
    public void testApplyingThePageRefreshesTemplatesThatAreAlreadyOpen() throws ConfigurationException {
        myFixture.configureByText("template.latte", "{includeblock 'other.latte'}");
        LatteSettingsForm form = new LatteSettingsForm(getProject());
        JComboBox<?> combo = comboIn(form.createComponent());
        assertNotNull(combo);
        long before = PsiModificationTracker.getInstance(getProject()).getModificationCount();

        combo.setSelectedIndex(3);
        form.apply();

        assertTrue("an open template has to be reparsed, or it keeps the errors of the old version",
            PsiModificationTracker.getInstance(getProject()).getModificationCount() > before);
    }

    private static JComboBox<?> comboIn(Component component) {
        if (component instanceof JComboBox) {
            return (JComboBox<?>) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JComboBox<?> found = comboIn(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
