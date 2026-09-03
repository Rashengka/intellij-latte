package dev.noctud.latte.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.BasePsiParsingTestCase;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.psi.LatteControlPart;
import dev.noctud.latte.psi.LatteFilePath;
import dev.noctud.latte.psi.LatteLinkPart;
import dev.noctud.latte.settings.LatteSettings;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@code getReferences()} is called concurrently by the platform - {@code PsiSearchHelperImpl}
 * processes elements in parallel. Implementations that lazily populate a field shared by all
 * callers either crash inside {@code ArrayList.add} or hand a half-built list to the second
 * thread; both were observed in production.
 */
public class ElementReferencesConcurrencyTest extends BasePsiParsingTestCase {

    private static final int THREADS = 8;
    private static final int ELEMENTS = 200;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LatteConfiguration.getInstance(getProject());
        getProject().registerService(LatteSettings.class);
    }

    @Override
    protected String getTestDataPath() {
        return ".";
    }

    @Test
    public void testLinkPartReferencesAreThreadSafe() throws Exception {
        assertReferencesAreThreadSafe(buildSource("{link Alpha%d:Beta%d:default}"), LatteLinkPart.class);
    }

    @Test
    public void testControlPartReferencesAreThreadSafe() throws Exception {
        assertReferencesAreThreadSafe(buildSource("{control fooBar%d-sub%d:baz}"), LatteControlPart.class);
    }

    @Test
    public void testFilePathReferencesAreThreadSafe() throws Exception {
        assertReferencesAreThreadSafe(buildSource("{include 'parts/dir%d/file%d.latte'}"), LatteFilePath.class);
    }

    private String buildSource(String macroFormat) {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < ELEMENTS; i++) {
            source.append(String.format(macroFormat, i, i)).append('\n');
        }
        return source.toString();
    }

    /**
     * Reads the reference count of every element single-threaded on one copy of the source, then
     * hammers the very same elements of a second, untouched copy from several threads at once.
     * Every thread has to see the complete result the single-threaded run produced.
     */
    private <T extends PsiElement> void assertReferencesAreThreadSafe(String source, Class<T> elementType) throws Exception {
        List<T> expectedElements = collect(createParsedFile("expected.latte", source), elementType);
        List<Integer> expected = new ArrayList<>();
        for (T element : expectedElements) {
            expected.add(element.getReferences().length);
        }
        Assert.assertFalse("no " + elementType.getSimpleName() + " parsed from the test source", expected.isEmpty());

        List<T> elements = collect(createParsedFile("concurrent.latte", source), elementType);
        Assert.assertEquals(expected.size(), elements.size());

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<String> mismatches = new CopyOnWriteArrayList<>();
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        List<Thread> threads = new ArrayList<>();

        for (int t = 0; t < THREADS; t++) {
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < elements.size(); i++) {
                        barrier.await();
                        PsiReference[] references = elements.get(i).getReferences();
                        if (references.length != expected.get(i)) {
                            mismatches.add("element #" + i + " expected " + expected.get(i)
                                + " references, got " + references.length);
                        }
                    }
                } catch (Throwable e) {
                    failures.add(e);
                    barrier.reset();
                }
            }, "references-" + t);
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        Assert.assertEquals("getReferences() threw: " + failures, List.of(), failures);
        Assert.assertEquals("getReferences() returned an incomplete result: " + mismatches, List.of(), mismatches);
    }

    private PsiFile createParsedFile(String name, String source) {
        PsiFile file = createPsiFile(name, source);
        ensureParsed(file);
        return file;
    }

    private <T extends PsiElement> List<T> collect(PsiFile file, Class<T> elementType) {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(file, elementType));
    }
}
