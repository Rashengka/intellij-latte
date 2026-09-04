package dev.noctud.latte.settings;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import dev.noctud.latte.inspections.MethodUsagesInspection;
import dev.noctud.latte.inspections.ModifierDefinitionInspection;
import dev.noctud.latte.inspections.ModifierNotAllowedInspection;
import dev.noctud.latte.inspections.VariablesInspection;
import org.jdom.Element;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The playground template that shows custom definitions off is opened with the definitions already
 * in place, and its whole point is that nothing in it is underlined. Nobody would notice it going
 * red until they opened it, so it is checked here against the very settings file the build copies
 * into the playground.
 *
 * <p>The rest of the playground stays a place to click around in rather than a fixture set - this
 * one file is checked because a mistake in it looks exactly like custom definitions not working.
 */
public class PlaygroundCustomDefinitionsTest extends BasePlatformTestCase {

    private static final String SETTINGS = "data/settings/PlaygroundLatteSettings.xml";
    private static final String TEMPLATE = "sandbox/playground/templates/custom-definitions.latte";

    public void testThePlaygroundTemplateIsQuietWithThePlaygroundSettings() throws Exception {
        LatteSettings playground = readPlaygroundSettings();
        LatteSettings settings = LatteSettings.getInstance(getProject());
        settings.tagSettings = playground.tagSettings;
        settings.filterSettings = playground.filterSettings;
        settings.functionSettings = playground.functionSettings;
        settings.variableSettings = playground.variableSettings;

        myFixture.enableInspections(
            new ModifierDefinitionInspection(),
            new ModifierNotAllowedInspection(),
            new VariablesInspection(),
            new MethodUsagesInspection()
        );
        myFixture.configureByText("custom-definitions.latte", readPlaygroundTemplate());

        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            problems.add(info.getSeverity().getName() + ":" + info.getDescription() + " at '" + info.getText() + "'");
        }
        assertEquals(List.of(), problems);
    }

    private static String readPlaygroundTemplate() throws Exception {
        File template = new File(TEMPLATE);
        assertTrue("Missing " + template.getAbsolutePath(), template.isFile());
        return Files.readString(template.toPath(), StandardCharsets.UTF_8);
    }

    private static LatteSettings readPlaygroundSettings() throws Exception {
        try (InputStream stream = PlaygroundCustomDefinitionsTest.class.getClassLoader().getResourceAsStream(SETTINGS)) {
            assertNotNull("Missing " + SETTINGS, stream);
            Element component = JDOMUtil.load(stream).getChild("component");
            assertNotNull("No <component> in " + SETTINGS, component);
            return XmlSerializer.deserialize(component, LatteSettings.class);
        }
    }
}
