package dev.noctud.latte.settings;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import dev.noctud.latte.config.LatteConfiguration;
import org.jdom.Element;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Custom tags, filters, functions and variables are only worth defining if they are still there
 * after the IDE restarts. They live in the project's latte.xml, written and read by the platform's
 * bean serializer, so this walks the same round trip: serialize {@link LatteSettings}, read it back,
 * and check that what the settings dialog can produce survives.
 *
 * <p>One malformed property is enough to take the whole file down - the serializer gives up on the
 * root bean, not on the property - so every kind is asserted separately as well as together.
 */
public class LatteSettingsPersistenceTest {

    @Test
    public void testCustomTagSurvivesTheRoundTrip() {
        LatteSettings settings = new LatteSettings();
        settings.tagSettings.add(new LatteTagSettings(
            "myBlock", LatteTagSettings.Type.PAIR, true, false, LatteConfiguration.Vendor.CUSTOM, "$title"
        ));

        LatteSettings loaded = roundTrip(settings);

        assertEquals(1, loaded.tagSettings.size());
        LatteTagSettings tag = loaded.tagSettings.get(0);
        assertEquals("myBlock", tag.getMacroName());
        assertEquals(LatteTagSettings.Type.PAIR, tag.getType());
        assertEquals("$title", tag.getArguments());
        assertTrue(tag.isAllowedModifiers());
        assertEquals(LatteConfiguration.Vendor.CUSTOM, tag.getVendor());
    }

    /**
     * A tag the dialog produces never carries argument settings - only the built-in configuration
     * defines those, and that one is never written to disk. The list still has to stay out of the
     * serialized form: written as an attribute it comes back as the string "[]", which the setter
     * cannot take, and the exception discards every other setting in the file along with it.
     */
    @Test
    public void testACustomTagDoesNotDiscardTheRestOfTheSettings() {
        LatteSettings settings = new LatteSettings();
        settings.tagSettings.add(new LatteTagSettings("myBlock", LatteTagSettings.Type.PAIR));
        settings.filterSettings.add(new LatteFilterSettings("myFilter"));
        settings.functionSettings.add(new LatteFunctionSettings("myFunction"));
        settings.variableSettings.add(new LatteVariableSettings("myVar", "\\App\\Model\\Article"));
        settings.enableCustomModifiers = false;

        LatteSettings loaded = roundTrip(settings);

        assertEquals(1, loaded.tagSettings.size());
        assertEquals(1, loaded.filterSettings.size());
        assertEquals(1, loaded.functionSettings.size());
        assertEquals(1, loaded.variableSettings.size());
        assertFalse(loaded.enableCustomModifiers);
    }

    @Test
    public void testCustomFilterSurvivesTheRoundTrip() {
        LatteSettings settings = new LatteSettings();
        settings.filterSettings.add(new LatteFilterSettings("myFilter", "description", "help", "::"));

        LatteSettings loaded = roundTrip(settings);

        assertEquals(1, loaded.filterSettings.size());
        LatteFilterSettings filter = loaded.filterSettings.get(0);
        assertEquals("myFilter", filter.getModifierName());
        assertEquals("description", filter.getModifierDescription());
        assertEquals("help", filter.getModifierHelp());
        // Two colons: the filter takes two arguments, which is what the inspection counts.
        assertEquals("::", filter.getModifierInsert());
    }

    @Test
    public void testCustomFunctionSurvivesTheRoundTrip() {
        LatteSettings settings = new LatteSettings();
        settings.functionSettings.add(new LatteFunctionSettings("myFunction", "string", "($count)"));

        LatteSettings loaded = roundTrip(settings);

        assertEquals(1, loaded.functionSettings.size());
        LatteFunctionSettings function = loaded.functionSettings.get(0);
        assertEquals("myFunction", function.getFunctionName());
        assertEquals("string", function.getFunctionReturnType());
        assertEquals("($count)", function.getFunctionHelp());
    }

    @Test
    public void testCustomVariableSurvivesTheRoundTrip() {
        LatteSettings settings = new LatteSettings();
        settings.variableSettings.add(new LatteVariableSettings("myVar", "\\App\\Model\\Article"));

        LatteSettings loaded = roundTrip(settings);

        assertEquals(1, loaded.variableSettings.size());
        LatteVariableSettings variable = loaded.variableSettings.get(0);
        assertEquals("myVar", variable.getVarName());
        assertEquals("\\App\\Model\\Article", variable.getVarType());
    }

    @Test
    public void testEnableFlagsSurviveTheRoundTrip() {
        LatteSettings settings = new LatteSettings();
        settings.enableCustomMacros = false;
        settings.enableCustomModifiers = false;
        settings.enableCustomFunctions = false;
        settings.enableDefaultVariables = false;
        settings.enableNette = false;
        settings.enableNetteForms = false;

        LatteSettings loaded = roundTrip(settings);

        assertFalse(loaded.enableCustomMacros);
        assertFalse(loaded.enableCustomModifiers);
        assertFalse(loaded.enableCustomFunctions);
        assertFalse(loaded.enableDefaultVariables);
        assertFalse(loaded.enableNette);
        assertFalse(loaded.enableNetteForms);
    }

    /**
     * Custom definitions belong to the project, not to a Latte line: a project that pins an older
     * Latte keeps the same custom tags and filters. The version override is stored beside the
     * lists and never inside them, so nothing has to be migrated when the version switch lands.
     *
     * @see <a href="file:.ai/plans/05-detekce-verze-latte.md">the version switch</a>
     */
    @Test
    public void testCustomDefinitionsDoNotDependOnTheLatteVersion() {
        LatteSettings settings = new LatteSettings();
        settings.latteVersionOverride = "2.11";
        settings.tagSettings.add(new LatteTagSettings("myBlock", LatteTagSettings.Type.PAIR));
        settings.filterSettings.add(new LatteFilterSettings("myFilter"));

        String written = JDOMUtil.writeElement(XmlSerializer.serialize(settings));
        assertFalse(
            "A custom definition must not carry a Latte version of its own: " + written,
            written.replace("<option name=\"latteVersionOverride\" value=\"2.11\" />", "").contains("2.11")
        );

        settings.latteVersionOverride = "3.1";
        LatteSettings loaded = roundTrip(settings);
        assertEquals("3.1", loaded.latteVersionOverride);
        assertEquals("myBlock", loaded.tagSettings.get(0).getMacroName());
        assertEquals("myFilter", loaded.filterSettings.get(0).getModifierName());
    }

    /**
     * The playground is opened with a latte.xml the build copies into it, so that the custom
     * definitions can be clicked through without filling four settings pages in by hand. A file the
     * IDE cannot read would leave the playground looking as if custom definitions did not work at
     * all, and nothing else checks it - it is data, not code.
     */
    @Test
    public void testThePlaygroundSettingsFileDescribesWhatItsTemplateUses() throws Exception {
        LatteSettings playground = readComponent("data/settings/PlaygroundLatteSettings.xml");

        assertEquals(3, playground.tagSettings.size());
        assertEquals(LatteTagSettings.Type.PAIR, tag(playground, "panel").getType());
        assertEquals(LatteTagSettings.Type.UNPAIRED, tag(playground, "icon").getType());
        assertEquals(LatteTagSettings.Type.ATTR_ONLY, tag(playground, "tooltip").getType());
        assertTrue("{icon} is written with a filter in the template", tag(playground, "icon").isAllowedModifiers());

        assertEquals(1, playground.filterSettings.size());
        assertEquals("excerpt", playground.filterSettings.get(0).getModifierName());
        assertEquals("one required argument", ":", playground.filterSettings.get(0).getModifierInsert());

        assertEquals(1, playground.functionSettings.size());
        assertEquals("formatPrice", playground.functionSettings.get(0).getFunctionName());

        assertEquals(1, playground.variableSettings.size());
        // Stored without the dollar sign - that is what the lookup normalises the name to.
        assertEquals("siteName", playground.variableSettings.get(0).getVarName());
    }

    private static LatteTagSettings tag(LatteSettings settings, String name) {
        for (LatteTagSettings tag : settings.tagSettings) {
            if (name.equals(tag.getMacroName())) {
                return tag;
            }
        }
        throw new AssertionError("No custom tag " + name);
    }

    /** Reads the state the way the project store does: the body of the plugin's own component. */
    private static LatteSettings readComponent(String resource) throws Exception {
        try (InputStream stream = LatteSettingsPersistenceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull("Missing " + resource, stream);
            Element component = JDOMUtil.load(stream).getChild("component");
            assertNotNull("No <component> in " + resource, component);
            return XmlSerializer.deserialize(component, LatteSettings.class);
        }
    }

    private static LatteSettings roundTrip(LatteSettings settings) {
        Element element = XmlSerializer.serialize(settings);
        return XmlSerializer.deserialize(element, LatteSettings.class);
    }
}
