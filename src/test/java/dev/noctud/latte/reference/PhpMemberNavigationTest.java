package dev.noctud.latte.reference;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Navigation from a template to the PHP it calls, and back: go to declaration, find usages and
 * rename, against the real PHP index. None of it had a test - the references were only ever built,
 * never resolved, so a reference that resolved to nothing would have passed everything.
 *
 * <p>Each positive case has its counterweight: a member the class does not have resolves to nothing,
 * and a method of the same name on another class is not a usage of this one.
 */
public class PhpMemberNavigationTest extends BasePlatformTestCase {

    private static final String ARTICLE_PHP =
        "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace App\\Model;\n"
            + "\n"
            + "final class Article\n"
            + "{\n"
            + "    public const STATUS = 'published';\n"
            + "    public static array $instances = [];\n"
            + "    public string $title = 'title';\n"
            + "\n"
            + "    public function getTitle(): string\n"
            + "    {\n"
            + "        return $this->title;\n"
            + "    }\n"
            + "}\n";

    private static final String COMMENT_PHP =
        "<?php declare(strict_types=1);\n"
            + "\n"
            + "namespace App\\Model;\n"
            + "\n"
            + "final class Comment\n"
            + "{\n"
            + "    public function getTitle(): string\n"
            + "    {\n"
            + "        return 'comment';\n"
            + "    }\n"
            + "}\n";

    private static final String TEMPLATE_HEAD = "{varType App\\Model\\Article $article}\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("app/Model/Article.php", ARTICLE_PHP);
        myFixture.addFileToProject("app/Model/Comment.php", COMMENT_PHP);
    }

    public void testAMethodCallGoesToTheMethod() {
        PsiElement target = resolveAtCaret("{$article->getTi<caret>tle()}\n");
        assertInstanceOf(target, Method.class);
        assertEquals("getTitle", ((Method) target).getName());
        assertEquals("Article", ((Method) target).getContainingClass().getName());
    }

    public void testAPropertyGoesToTheProperty() {
        PsiElement target = resolveAtCaret("{$article->ti<caret>tle}\n");
        assertInstanceOf(target, Field.class);
        assertEquals("title", ((Field) target).getName());
    }

    public void testAClassConstantGoesToTheConstant() {
        PsiElement target = resolveAtCaret("{$article::STA<caret>TUS}\n");
        assertInstanceOf(target, Field.class);
        assertTrue(((Field) target).isConstant());
        assertEquals("STATUS", ((Field) target).getName());
    }

    public void testAStaticPropertyGoesToTheProperty() {
        PsiElement target = resolveAtCaret("{$article::$inst<caret>ances}\n");
        assertInstanceOf(target, Field.class);
        assertEquals("instances", ((Field) target).getName());
    }

    public void testAMemberTheClassDoesNotHaveGoesNowhere() {
        myFixture.configureByText("missing.latte", TEMPLATE_HEAD + "{$article->noSuch<caret>Method()}\n");
        PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertTrue("an unknown method has to resolve to nothing", reference == null || reference.resolve() == null);
    }

    public void testFindUsagesOfTheMethodFindsTheCallInTheTemplate() {
        template("templates/detail.latte", TEMPLATE_HEAD + "{$article->getTitle()}\n");
        assertEquals(List.of("templates/detail.latte"), usageFiles(method("\\App\\Model\\Article", "getTitle")));
    }

    public void testAMethodOfTheSameNameOnAnotherClassIsNotAUsage() {
        template("templates/detail.latte", TEMPLATE_HEAD + "{$article->getTitle()}\n");
        assertEquals(List.of(), usageFiles(method("\\App\\Model\\Comment", "getTitle")));
    }

    public void testFindUsagesOfThePropertyFindsTheTemplate() {
        template("templates/detail.latte", TEMPLATE_HEAD + "{$article->title}\n");
        assertEquals(List.of("templates/detail.latte"), usageFiles(field("\\App\\Model\\Article", "title", false)));
    }

    public void testFindUsagesOfTheConstantFindsTheTemplate() {
        template("templates/detail.latte", TEMPLATE_HEAD + "{$article::STATUS}\n");
        assertEquals(List.of("templates/detail.latte"), usageFiles(field("\\App\\Model\\Article", "STATUS", true)));
    }

    public void testFindUsagesOfTheStaticPropertyFindsTheTemplate() {
        template("templates/detail.latte", TEMPLATE_HEAD + "{$article::$instances}\n");
        assertEquals(List.of("templates/detail.latte"), usageFiles(field("\\App\\Model\\Article", "instances", false)));
    }

    public void testRenamingTheMethodRenamesTheCallInTheTemplate() {
        template("templates/detail.latte", TEMPLATE_HEAD + "{$article->getTitle()}\n");
        myFixture.renameElement(method("\\App\\Model\\Article", "getTitle"), "getHeadline");
        myFixture.checkResult("templates/detail.latte", TEMPLATE_HEAD + "{$article->getHeadline()}\n", false);
    }

    /**
     * A template that is not open in an editor. The documents are committed because a Latte file
     * added this way reads as empty until they are - an HTML file added the same way does not.
     * That is its own finding (N20 in the coverage review) and not what these tests are about.
     */
    private void template(String path, String text) {
        myFixture.addFileToProject(path, text);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    private PsiElement resolveAtCaret(String body) {
        myFixture.configureByText("detail.latte", TEMPLATE_HEAD + body);
        PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("no reference at the caret", reference);
        PsiElement target = reference.resolve();
        assertNotNull("the reference resolves to nothing", target);
        return target;
    }

    private Method method(String classFqn, String name) {
        Collection<PhpClass> classes = PhpIndex.getInstance(getProject()).getClassesByFQN(classFqn);
        assertEquals("exactly one " + classFqn, 1, classes.size());
        Method method = classes.iterator().next().findOwnMethodByName(name);
        assertNotNull(classFqn + " has no method " + name, method);
        return method;
    }

    private Field field(String classFqn, String name, boolean constant) {
        Collection<PhpClass> classes = PhpIndex.getInstance(getProject()).getClassesByFQN(classFqn);
        assertEquals("exactly one " + classFqn, 1, classes.size());
        Field field = classes.iterator().next().findOwnFieldByName(name, constant);
        assertNotNull(classFqn + " has no field " + name, field);
        return field;
    }

    private List<String> usageFiles(PhpNamedElement target) {
        List<String> files = new ArrayList<>();
        for (PsiReference reference : ReferencesSearch.search(target).findAll()) {
            String path = reference.getElement().getContainingFile().getVirtualFile().getPath();
            if (path.endsWith(".latte")) {
                files.add(path.substring(path.indexOf("templates/")));
            }
        }
        return files;
    }
}
