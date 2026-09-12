package dev.noctud.latte.reference;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import dev.noctud.latte.refactoring.LatteRenamePsiElementProcessor;

import java.util.Collection;

/**
 * Renaming a PHP method from a template's project stays PHP's business. The plugin's rename processor
 * says yes to PHP methods and fields as well as to its own elements, and it is registered without an
 * order - so whether PHP's processor or this one handles a PHP method depends on which plugin loaded
 * first. PHP's knows about the class hierarchy; the plugin's default one does not.
 *
 * <p>The counterweight is the template: renaming the method still rewrites the call in it.
 */
public class RenameHierarchyTest extends BasePlatformTestCase {

    private static final String BASE_PHP =
        "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace App\\Model;\n"
            + "\n"
            + "class Article\n"
            + "{\n"
            + "    public function getTitle(): string\n"
            + "    {\n"
            + "        return 'title';\n"
            + "    }\n"
            + "}\n";

    private static final String CHILD_PHP =
        "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace App\\Model;\n"
            + "\n"
            + "final class FeaturedArticle extends Article\n"
            + "{\n"
            + "    public function getTitle(): string\n"
            + "    {\n"
            + "        return 'featured';\n"
            + "    }\n"
            + "}\n";

    private static final String TEMPLATE = "{varType App\\Model\\Article $article}\n{$article->getTitle()}\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("app/Model/Article.php", BASE_PHP);
        myFixture.addFileToProject("app/Model/FeaturedArticle.php", CHILD_PHP);
        myFixture.addFileToProject("templates/detail.latte", TEMPLATE);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    public void testAPhpMethodIsNotRenamedByThePluginsProcessor() {
        RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(method("\\App\\Model\\Article", "getTitle"));
        assertFalse("the plugin's processor took over renaming a PHP method: " + processor.getClass().getName(),
            processor instanceof LatteRenamePsiElementProcessor);
    }

    public void testRenamingAMethodRenamesItsOverrideAndTheTemplate() {
        myFixture.renameElement(method("\\App\\Model\\Article", "getTitle"), "getHeadline");
        assertNotNull("the override was not renamed with the method",
            classOf("\\App\\Model\\FeaturedArticle").findOwnMethodByName("getHeadline"));
        myFixture.checkResult("templates/detail.latte", TEMPLATE.replace("getTitle", "getHeadline"), false);
    }

    private PhpClass classOf(String fqn) {
        Collection<PhpClass> classes = PhpIndex.getInstance(getProject()).getClassesByFQN(fqn);
        assertEquals("exactly one " + fqn, 1, classes.size());
        return classes.iterator().next();
    }

    private Method method(String classFqn, String name) {
        Method method = classOf(classFqn).findOwnMethodByName(name);
        assertNotNull(classFqn + " has no method " + name, method);
        return method;
    }
}
