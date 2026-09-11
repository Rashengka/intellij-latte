package dev.noctud.latte.reference;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Navigation between a template and the class it names, the presenter action a link points at and
 * the component a {control} renders - go to declaration, find usages and, for a class, rename -
 * against the real PHP index.
 *
 * <p>A template finds its presenter by the directory it sits in ({@code templates/Article/} belongs to
 * {@code ArticlePresenter}), and only a subclass of {@code Nette\Application\UI\Presenter} is a
 * presenter, so both are declared here. A link and a control never answer "yes" to
 * {@code isReferenceTo} on purpose - renaming a PHP method must not rewrite a link - so their usages
 * are found by the plugin's own search.
 */
public class ClassAndPresenterNavigationTest extends BasePlatformTestCase {

    private static final String NETTE_PRESENTER_PHP =
        "<?php\n"
            + "\n"
            + "namespace Nette\\Application\\UI;\n"
            + "\n"
            + "abstract class Presenter\n"
            + "{\n"
            + "}\n";

    private static final String ARTICLE_PRESENTER_PHP =
        "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace App\\Presenters;\n"
            + "\n"
            + "final class ArticlePresenter extends \\Nette\\Application\\UI\\Presenter\n"
            + "{\n"
            + "    public function renderDefault(): void\n"
            + "    {\n"
            + "    }\n"
            + "\n"
            + "    public function renderDetail(int $id): void\n"
            + "    {\n"
            + "    }\n"
            + "\n"
            + "    protected function createComponentCart(): \\App\\Model\\Article\n"
            + "    {\n"
            + "        return new \\App\\Model\\Article();\n"
            + "    }\n"
            + "}\n";

    private static final String ARTICLE_PHP =
        "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace App\\Model;\n"
            + "\n"
            + "final class Article\n"
            + "{\n"
            + "}\n";

    private static final String TEMPLATE_DIR = "app/Presenters/templates/Article/";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("vendor/nette/application/Presenter.php", NETTE_PRESENTER_PHP);
        myFixture.addFileToProject("app/Presenters/ArticlePresenter.php", ARTICLE_PRESENTER_PHP);
        myFixture.addFileToProject("app/Model/Article.php", ARTICLE_PHP);
    }

    public void testAClassNameGoesToTheClass() {
        PsiElement target = resolveAtCaret("default.latte", "{varType App\\Model\\Arti<caret>cle $article}\n");
        assertInstanceOf(target, PhpClass.class);
        assertEquals("\\App\\Model\\Article", ((PhpClass) target).getFQN());
    }

    public void testFindUsagesOfTheClassFindsTheTemplate() {
        template("default.latte", "{varType App\\Model\\Article $article}\n");
        assertEquals(List.of("default.latte"), usageFiles(phpClass("\\App\\Model\\Article")));
    }

    public void testAClassOfTheSameNameInAnotherNamespaceIsNotAUsage() {
        myFixture.addFileToProject("app/Other/Article.php",
            "<?php declare(strict_types=1);\n\nnamespace App\\Other;\n\nfinal class Article\n{\n}\n");
        template("default.latte", "{varType App\\Model\\Article $article}\n");
        assertEquals(List.of(), usageFiles(phpClass("\\App\\Other\\Article")));
    }

    public void testALinkGoesToTheAction() {
        PsiElement target = resolveAtCaret("default.latte", "<a n:href=\"deta<caret>il\">x</a>\n");
        assertInstanceOf(target, Method.class);
        assertEquals("renderDetail", ((Method) target).getName());
    }

    public void testFindUsagesOfTheActionFindsTheLink() {
        template("default.latte", "<a n:href=\"detail\">x</a>\n");
        assertEquals(List.of("default.latte"), usageFiles(method("\\App\\Presenters\\ArticlePresenter", "renderDetail")));
    }

    public void testALinkToAnotherActionIsNotAUsage() {
        template("default.latte", "<a n:href=\"default\">x</a>\n");
        assertEquals(List.of(), usageFiles(method("\\App\\Presenters\\ArticlePresenter", "renderDetail")));
    }

    public void testAControlGoesToTheFactory() {
        PsiElement target = resolveAtCaret("default.latte", "{control ca<caret>rt}\n");
        assertInstanceOf(target, Method.class);
        assertEquals("createComponentCart", ((Method) target).getName());
    }

    public void testFindUsagesOfTheFactoryFindsTheControl() {
        template("default.latte", "{control cart}\n");
        assertEquals(List.of("default.latte"), usageFiles(method("\\App\\Presenters\\ArticlePresenter", "createComponentCart")));
    }

    /** See PhpMemberNavigationTest: a Latte file added without opening it is empty until committed (N20). */
    private void template(String name, String text) {
        myFixture.addFileToProject(TEMPLATE_DIR + name, text);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    private PsiElement resolveAtCaret(String name, String text) {
        PsiFile file = myFixture.addFileToProject(TEMPLATE_DIR + name, text.replace("<caret>", ""));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        int caret = text.indexOf("<caret>");
        PsiReference reference = myFixture.getFile().findReferenceAt(caret);
        assertNotNull("no reference at the caret", reference);
        PsiElement target = reference.resolve();
        assertNotNull("the reference resolves to nothing", target);
        return target;
    }

    private PhpClass phpClass(String fqn) {
        Collection<PhpClass> classes = PhpIndex.getInstance(getProject()).getClassesByFQN(fqn);
        assertEquals("exactly one " + fqn, 1, classes.size());
        return classes.iterator().next();
    }

    private Method method(String classFqn, String name) {
        Method method = phpClass(classFqn).findOwnMethodByName(name);
        assertNotNull(classFqn + " has no method " + name, method);
        return method;
    }

    private List<String> usageFiles(PhpNamedElement target) {
        List<String> files = new ArrayList<>();
        for (PsiReference reference : ReferencesSearch.search(target).findAll()) {
            String path = reference.getElement().getContainingFile().getVirtualFile().getPath();
            if (path.endsWith(".latte")) {
                files.add(path.substring(path.lastIndexOf('/') + 1));
            }
        }
        return files;
    }
}
