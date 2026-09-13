package dev.noctud.latte.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;

import java.util.List;

/**
 * Links and controls in the template of a component rather than of a presenter.
 *
 * <p>Inside a component Nette reads every destination of {@code {link}} and {@code n:href} as a signal
 * of that component ({@code LinkGenerator::createRequest}: a component that is not a presenter takes
 * the signal branch), a colon naming a subcomponent rather than a presenter; only {@code {plink}}
 * links a presenter, and which presenter renders the component the template does not say. A
 * presenter whose name happens to match the component's must not be offered as the target.
 */
public class ComponentTemplateLinkTest extends BasePlatformTestCase {

    private static final String NETTE_UI_PHP =
        "<?php\n"
            + "\n"
            + "namespace Nette\\Application\\UI;\n"
            + "\n"
            + "abstract class Component\n"
            + "{\n"
            + "}\n"
            + "\n"
            + "abstract class Control extends Component\n"
            + "{\n"
            + "}\n"
            + "\n"
            + "abstract class Presenter extends Control\n"
            + "{\n"
            + "}\n";

    private static final String COMPONENT_DIR = "app/Components/ArticleList/";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("vendor/nette/application/UI.php", NETTE_UI_PHP);
        myFixture.addFileToProject(COMPONENT_DIR + "ArticleList.php", component("App\\Components\\ArticleList", "ArticleList"));
        myFixture.addFileToProject("app/Components/Pager.php", plainControl("App\\Components", "Pager"));
        // Same name as the component, and it has everything the component's template asks for.
        myFixture.addFileToProject("app/Presenters/ArticleListPresenter.php", presenter("ArticleListPresenter"));
        myFixture.addFileToProject("app/Presenters/HomePresenter.php", presenter("HomePresenter"));
    }

    public void testASignalGoesToTheComponent() {
        assertMethod("\\App\\Components\\ArticleList\\ArticleList::handleLoadMore",
            resolveAtCaret(COMPONENT_DIR + "list.latte", "<a n:href=\"loadMo<caret>re!\">x</a>\n"));
    }

    public void testATargetWithoutTheExclamationMarkIsStillTheComponentsSignal() {
        assertMethod("\\App\\Components\\ArticleList\\ArticleList::handleLoadMore",
            resolveAtCaret(COMPONENT_DIR + "list.latte", "{link loadMo<caret>re}\n"));
    }

    public void testATargetTheComponentHasNoHandlerForGoesNowhere() {
        assertNull(resolveAtCaret(COMPONENT_DIR + "list.latte", "{link defa<caret>ult}\n"));
    }

    public void testAColonNamesASubcomponentAndNotAPresenter() {
        assertNull(resolveAtCaret(COMPONENT_DIR + "list.latte", "{link Ho<caret>me:default}\n"));
    }

    public void testAPresenterLinkWithoutAPresenterGoesNowhere() {
        assertNull(resolveAtCaret(COMPONENT_DIR + "list.latte", "{plink defa<caret>ult}\n"));
    }

    public void testAPresenterLinkThatNamesItsPresenterStillWorks() {
        assertMethod("\\App\\Presenters\\HomePresenter::renderDefault",
            resolveAtCaret(COMPONENT_DIR + "a.latte", "{plink :Home:defa<caret>ult}\n"));
        assertClass("\\App\\Presenters\\HomePresenter",
            resolveAtCaret(COMPONENT_DIR + "b.latte", "{plink :Ho<caret>me:default}\n"));
    }

    public void testAControlComesFromTheComponent() {
        assertMethod("\\App\\Components\\ArticleList\\ArticleList::createComponentPager",
            resolveAtCaret(COMPONENT_DIR + "list.latte", "{control pa<caret>ger}\n"));
    }

    public void testTheTemplateTypeNamesTheComponentWhereverTheTemplateLies() {
        myFixture.addFileToProject(COMPONENT_DIR + "ArticleListTemplate.php",
            "<?php\n\nnamespace App\\Components\\ArticleList;\n\nfinal class ArticleListTemplate\n{\n}\n");

        assertMethod("\\App\\Components\\ArticleList\\ArticleList::handleLoadMore",
            resolveAtCaret("app/templates/components/list.latte",
                "{templateType App\\Components\\ArticleList\\ArticleListTemplate}\n<a n:href=\"loadMo<caret>re!\">x</a>\n"));
    }

    public void testTwoComponentsInOneFolderLeaveTheSignalUnresolved() {
        myFixture.addFileToProject(COMPONENT_DIR + "ArticleFilter.php", component("App\\Components\\ArticleList", "ArticleFilter"));

        assertNull(resolveAtCaret(COMPONENT_DIR + "list.latte", "<a n:href=\"loadMo<caret>re!\">x</a>\n"));
    }

    /** Nette 3.2 keeps a presenter and its templates in one folder; a component beside it does not make them its own. */
    public void testATemplateBesideAPresenterStaysThePresentersTemplate() {
        myFixture.addFileToProject("app/Presentation/Article/ArticlePresenter.php",
            "<?php\n\nnamespace App\\Presentation\\Article;\n\nfinal class ArticlePresenter extends \\Nette\\Application\\UI\\Presenter\n{\n"
                + "    public function renderDetail(): void\n    {\n    }\n}\n");
        myFixture.addFileToProject("app/Presentation/Article/ArticleGrid.php", plainControl("App\\Presentation\\Article", "ArticleGrid"));

        assertMethod("\\App\\Presentation\\Article\\ArticlePresenter::renderDetail",
            resolveAtCaret("app/Presentation/Article/default.latte", "<a n:href=\"deta<caret>il\">x</a>\n"));
    }

    public void testCompletionOffersTheComponentsSignalsAndNoPresenter() {
        PsiFile file = myFixture.addFileToProject(COMPONENT_DIR + "list.latte", "<a n:href=\"lo\">x</a>\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset("<a n:href=\"lo".length());
        myFixture.completeBasic();
        // Two signals match, so the list is shown rather than one item inserted without asking.
        List<String> offered = myFixture.getLookupElementStrings();

        assertNotNull("no list was shown; the document reads: " + myFixture.getEditor().getDocument().getText(), offered);
        assertTrue("the component's signals are offered: " + offered, offered.containsAll(List.of("loadMore!", "loadLess!")));
        assertFalse("a presenter's action is not: " + offered, offered.contains("default"));
        assertFalse("nor a presenter: " + offered, offered.stream().anyMatch(s -> s.startsWith("ArticleList:") || s.startsWith("Home:")));
    }

    private static String component(String namespace, String className) {
        return "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace " + namespace + ";\n"
            + "\n"
            + "final class " + className + " extends \\Nette\\Application\\UI\\Control\n"
            + "{\n"
            + "    public function handleLoadMore(): void\n"
            + "    {\n"
            + "    }\n"
            + "\n"
            + "    public function handleLoadLess(): void\n"
            + "    {\n"
            + "    }\n"
            + "\n"
            + "    protected function createComponentPager(): \\App\\Components\\Pager\n"
            + "    {\n"
            + "        return new \\App\\Components\\Pager();\n"
            + "    }\n"
            + "}\n";
    }

    private static String plainControl(String namespace, String className) {
        return "<?php declare(strict_types=1);\n\nnamespace " + namespace + ";\n\nfinal class " + className
            + " extends \\Nette\\Application\\UI\\Control\n{\n}\n";
    }

    private static String presenter(String className) {
        return "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace App\\Presenters;\n"
            + "\n"
            + "final class " + className + " extends \\Nette\\Application\\UI\\Presenter\n"
            + "{\n"
            + "    public function renderDefault(): void\n"
            + "    {\n"
            + "    }\n"
            + "\n"
            + "    public function handleLoadMore(): void\n"
            + "    {\n"
            + "    }\n"
            + "\n"
            + "    protected function createComponentPager(): \\App\\Components\\Pager\n"
            + "    {\n"
            + "        return new \\App\\Components\\Pager();\n"
            + "    }\n"
            + "}\n";
    }

    private PsiElement resolveAtCaret(String path, String text) {
        PsiFile file = myFixture.addFileToProject(path, text.replace("<caret>", ""));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PsiReference reference = myFixture.getFile().findReferenceAt(text.indexOf("<caret>"));
        assertNotNull("no reference at the caret", reference);
        return reference.resolve();
    }

    private static void assertClass(String fqn, PsiElement target) {
        assertInstanceOf(target, PhpClass.class);
        assertEquals(fqn, ((PhpClass) target).getFQN());
    }

    private static void assertMethod(String expected, PsiElement target) {
        assertInstanceOf(target, Method.class);
        Method method = (Method) target;
        assertNotNull(method.getContainingClass());
        assertEquals(expected, method.getContainingClass().getFQN() + "::" + method.getName());
    }
}
