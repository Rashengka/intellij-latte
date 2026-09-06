package dev.noctud.latte;

import com.intellij.core.CoreInjectedLanguageManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataFile;
import dev.noctud.latte.parser.LatteParserDefinition;
import dev.noctud.latte.psi.LattePhpVariable;
import dev.noctud.latte.version.LatteVersionService;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

abstract public class BasePsiParsingTestCase extends ParsingTestCase {

    protected BasePsiParsingTestCase() {
        super("", "latte", new LatteParserDefinition());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // ParsingTestCase's mock project does not provide it, but CachedValuesManager asks it
        // whether a PSI dependency is physical, so anything caching through it fails without one.
        getProject().registerService(InjectedLanguageManager.class, new CoreInjectedLanguageManager());
        // The registry asks which Latte version a file belongs to, and the mock project registers
        // nothing on its own. Here it answers "undetermined" - there is no composer.lock above a
        // light virtual file - which is the answer that withholds nothing, so these tests see the
        // whole registry exactly as they did before versions existed.
        getProject().registerService(LatteVersionService.class);
    }

    protected String loadFile(@NotNull @NonNls @TestDataFile String name) throws IOException {
        return FileUtil.loadFile(new File(myFullDataPath, name), CharsetToolkit.UTF8, true);
    }

    protected PsiFile parseFile(@NotNull String fileName) throws IOException {
        return parseFile(fileName, loadFile(fileName));
    }

    protected List<LattePhpVariable> collectVariables(PsiElement parent) {
        List<LattePhpVariable> variables = new ArrayList<>();
        parent.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof LattePhpVariable) {
                    variables.add((LattePhpVariable) element);
                } else {
                    super.visitElement(element);
                }
            }
        });
        return variables;
    }

}
