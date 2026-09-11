package dev.noctud.latte.inspections;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.LatteLanguage;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Every inspection the plugin registers has a description, and every description belongs to an
 * inspection. The description is what the settings page and "More..." on a report show; without it
 * nobody can tell what an inspection checks.
 *
 * <p>The second half is the one a missing file would not catch: a description saved under a name no
 * inspection has is never loaded, and nothing says so - the file is there, it just is not read.
 */
public class InspectionDescriptionsTest extends BasePlatformTestCase {

    private static final String DIRECTORY = "inspectionDescriptions";

    public void testEveryInspectionHasADescription() {
        List<String> missing = new ArrayList<>();
        for (String shortName : registeredShortNames()) {
            if (getClass().getClassLoader().getResource(DIRECTORY + "/" + shortName + ".html") == null) {
                missing.add(shortName);
            }
        }
        assertEquals("these inspections show an empty description", List.of(), missing);
    }

    /** Read from the sources: on the test classpath the resources sit in a jar, not in a directory. */
    public void testEveryDescriptionBelongsToAnInspection() throws IOException {
        Path directory = Paths.get("src/main/resources", DIRECTORY);
        assertTrue("Missing " + directory.toAbsolutePath(), Files.isDirectory(directory));
        Set<String> registered = registeredShortNames();
        List<String> orphans = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.map(file -> file.getFileName().toString())
                .filter(name -> name.endsWith(".html"))
                .map(name -> name.substring(0, name.length() - ".html".length()))
                .filter(name -> !registered.contains(name))
                .sorted()
                .forEach(orphans::add);
        }
        assertEquals("these descriptions are never loaded - no inspection has that name", List.of(), orphans);
    }

    private static Set<String> registeredShortNames() {
        Set<String> names = new TreeSet<>();
        for (LocalInspectionEP ep : LocalInspectionEP.LOCAL_INSPECTION.getExtensionList()) {
            if (LatteLanguage.INSTANCE.getID().equals(ep.language)) {
                names.add(((LocalInspectionTool) ep.instantiateTool()).getShortName());
            }
        }
        assertFalse("the plugin registers no Latte inspection at all", names.isEmpty());
        return names;
    }
}
