package dev.noctud.latte.reference;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;

/**
 * The modules a link names ({@code :Admin:Home:default}) and the presenter they pick, against the
 * real PHP index.
 *
 * <p>Nette turns every module into one part of the presenter's namespace, bare or with a suffix -
 * {@code App\Presentation\*\**Presenter}, {@code App\Modules\*\Presenters\*Presenter} and
 * {@code App\*Module\Presenters\*Presenter} are all mappings in use - so two presenters of the same
 * name in different modules differ only there.
 */
public class LinkModuleTest extends BasePlatformTestCase {

    private static final String NETTE_PRESENTER_PHP =
        "<?php\n"
            + "\n"
            + "namespace Nette\\Application\\UI;\n"
            + "\n"
            + "abstract class Presenter\n"
            + "{\n"
            + "}\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("vendor/nette/application/Presenter.php", NETTE_PRESENTER_PHP);
    }

    public void testTheModuleInTheLinkPicksThePresenterOverANearerOne() {
        presenter("app/Front/HomePresenter.php", "App\\Front", "HomePresenter");
        presenter("app/Admin/HomePresenter.php", "App\\Admin", "HomePresenter");

        PsiElement target = resolveAtCaret("app/Front/templates/Home/default.latte", "<a n:href=\":Admin:Home:def<caret>ault\">x</a>\n");
        assertMethod("\\App\\Admin\\HomePresenter", "renderDefault", target);
    }

    public void testTheModuleInTheLinkPicksThePresenterSegment() {
        presenter("app/Front/HomePresenter.php", "App\\Front", "HomePresenter");
        presenter("app/Admin/HomePresenter.php", "App\\Admin", "HomePresenter");

        PsiElement target = resolveAtCaret("app/Front/templates/Home/default.latte", "<a n:href=\":Admin:Ho<caret>me:default\">x</a>\n");
        assertClass("\\App\\Admin\\HomePresenter", target);
    }

    public void testAModuleWithTheModuleSuffixPicksThePresenter() {
        presenter("app/FrontModule/Presenters/HomePresenter.php", "App\\FrontModule\\Presenters", "HomePresenter");
        presenter("app/AdminModule/Presenters/HomePresenter.php", "App\\AdminModule\\Presenters", "HomePresenter");

        PsiElement target = resolveAtCaret("app/FrontModule/templates/Home/default.latte", "<a n:href=\":Admin:Home:def<caret>ault\">x</a>\n");
        assertMethod("\\App\\AdminModule\\Presenters\\HomePresenter", "renderDefault", target);
    }

    public void testAModuleOnlyStartingWithTheNameDoesNotPickThePresenter() {
        presenter("app/Front/HomePresenter.php", "App\\Front", "HomePresenter");
        presenter("app/Frontend/HomePresenter.php", "App\\Frontend", "HomePresenter");

        PsiElement target = resolveAtCaret("app/Frontend/templates/Home/default.latte", "<a n:href=\":Front:Home:def<caret>ault\">x</a>\n");
        assertMethod("\\App\\Front\\HomePresenter", "renderDefault", target);
    }

    public void testAModuleGoesToItsFolderUnderThePresentationMapping() {
        presenter("app/Presentation/Front/Home/HomePresenter.php", "App\\Presentation\\Front\\Home", "HomePresenter");

        PsiElement target = resolveAtCaret("app/Presentation/Front/Home/default.latte", "<a n:href=\":Fr<caret>ont:Home:default\">x</a>\n");
        assertDirectory("app/Presentation/Front", target);
    }

    public void testAModuleGoesToItsFolderAndNotToItsBasePresenter() {
        abstractPresenter("app/Modules/Front/Presenters/FrontPresenter.php", "App\\Modules\\Front\\Presenters", "FrontPresenter");
        presenter("app/Modules/Front/Presenters/HomePresenter.php", "App\\Modules\\Front\\Presenters", "HomePresenter");

        PsiElement target = resolveAtCaret("app/Modules/Front/templates/Home/default.latte", "<a n:href=\":Fr<caret>ont:Home:default\">x</a>\n");
        assertDirectory("app/Modules/Front", target);
    }

    public void testAModuleWithTheModuleSuffixGoesToItsFolder() {
        presenter("app/FrontModule/Presenters/HomePresenter.php", "App\\FrontModule\\Presenters", "HomePresenter");

        PsiElement target = resolveAtCaret("app/FrontModule/templates/Home/default.latte", "<a n:href=\":Fr<caret>ont:Home:default\">x</a>\n");
        assertDirectory("app/FrontModule", target);
    }

    public void testNestedModulesGoEachToItsFolder() {
        presenter("app/Modules/Core/Front/Presenters/HomePresenter.php", "App\\Modules\\Core\\Front\\Presenters", "HomePresenter");

        assertDirectory("app/Modules/Core", resolveAtCaret("app/Modules/Core/Front/templates/Home/a.latte", "<a n:href=\":Co<caret>re:Front:Home:\">x</a>\n"));
        assertDirectory("app/Modules/Core/Front", resolveAtCaret("app/Modules/Core/Front/templates/Home/b.latte", "<a n:href=\":Core:Fr<caret>ont:Home:\">x</a>\n"));
    }

    public void testARelativeModuleGoesToItsFolderInsideTheCurrentModule() {
        presenter("app/Modules/Admin/Front/Presenters/HomePresenter.php", "App\\Modules\\Admin\\Front\\Presenters", "HomePresenter");

        PsiElement target = resolveAtCaret("app/Modules/Admin/templates/Dashboard/default.latte", "<a n:href=\"Fr<caret>ont:Home:\">x</a>\n");
        assertDirectory("app/Modules/Admin/Front", target);
    }

    public void testAModuleMissingFromTheNamespaceGoesNowhere() {
        // a mapping for one module (Admin: App\Backend\*Presenter) leaves the module's name out
        abstractPresenter("app/Backend/AdminPresenter.php", "App\\Backend", "AdminPresenter");
        presenter("app/Backend/HomePresenter.php", "App\\Backend", "HomePresenter");

        assertNull(resolveAtCaret("app/Backend/templates/Home/default.latte", "<a n:href=\":Ad<caret>min:Home:\">x</a>\n"));
    }

    public void testAModuleWhoseFolderDoesNotMatchTheNamespaceGoesNowhere() {
        presenter("app/web/Front/HomePresenter.php", "App\\Modules\\Front\\Presenters", "HomePresenter");

        assertNull(resolveAtCaret("app/web/templates/Home/default.latte", "<a n:href=\":Fr<caret>ont:Home:\">x</a>\n"));
    }

    public void testAModuleOnlyStartingWithTheNameGoesNowhere() {
        presenter("app/Modules/Frontend/Presenters/HomePresenter.php", "App\\Modules\\Frontend\\Presenters", "HomePresenter");

        assertNull(resolveAtCaret("app/Modules/Frontend/templates/Home/default.latte", "<a n:href=\":Fr<caret>ont:Home:\">x</a>\n"));
    }

    public void testAModuleNextToOneStartingWithTheNameGoesToItsOwnFolder() {
        presenter("app/Modules/Frontend/Presenters/HomePresenter.php", "App\\Modules\\Frontend\\Presenters", "HomePresenter");
        presenter("app/Modules/Front/Presenters/HomePresenter.php", "App\\Modules\\Front\\Presenters", "HomePresenter");

        PsiElement target = resolveAtCaret("app/Modules/Frontend/templates/Home/default.latte", "<a n:href=\":Fr<caret>ont:Home:\">x</a>\n");
        assertDirectory("app/Modules/Front", target);
    }

    private void abstractPresenter(String path, String namespace, String className) {
        myFixture.addFileToProject(path,
            "<?php declare(strict_types=1);\n"
                + "\n"
                + "namespace " + namespace + ";\n"
                + "\n"
                + "abstract class " + className + " extends \\Nette\\Application\\UI\\Presenter\n"
                + "{\n"
                + "}\n");
    }

    private void presenter(String path, String namespace, String className) {
        myFixture.addFileToProject(path,
            "<?php declare(strict_types=1);\n"
                + "\n"
                + "namespace " + namespace + ";\n"
                + "\n"
                + "final class " + className + " extends \\Nette\\Application\\UI\\Presenter\n"
                + "{\n"
                + "    public function renderDefault(): void\n"
                + "    {\n"
                + "    }\n"
                + "}\n");
    }

    private PsiElement resolveAtCaret(String path, String text) {
        PsiFile file = myFixture.addFileToProject(path, text.replace("<caret>", ""));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PsiReference reference = myFixture.getFile().findReferenceAt(text.indexOf("<caret>"));
        assertNotNull("no reference at the caret", reference);
        return reference.resolve();
    }

    private static void assertDirectory(String path, PsiElement target) {
        assertInstanceOf(target, PsiDirectory.class);
        String actual = ((PsiDirectory) target).getVirtualFile().getPath();
        assertTrue(actual + " is not " + path, actual.endsWith("/" + path));
    }

    private static void assertClass(String fqn, PsiElement target) {
        assertInstanceOf(target, PhpClass.class);
        assertEquals(fqn, ((PhpClass) target).getFQN());
    }

    private static void assertMethod(String classFqn, String name, PsiElement target) {
        assertInstanceOf(target, Method.class);
        Method method = (Method) target;
        assertNotNull(method.getContainingClass());
        assertEquals(classFqn + "::" + name, method.getContainingClass().getFQN() + "::" + method.getName());
    }
}
