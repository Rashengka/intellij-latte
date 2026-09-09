package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code {foreach}} over an object is valid PHP, and the report said so in its own words.
 *
 * <p>"Expected types: 'array' or 'object'" was written on a template whose variable was typed as
 * an object - and on every other one too. {@code \Traversable}, {@code \Generator}, a class
 * implementing {@code Iterator}, and the word {@code object} itself were all reported, because
 * the question behind the report never returned true for a class:
 *
 * <pre>phpClass.getClass().isInstance(iterableClass)</pre>
 *
 * <p>That is Java reflection - it asks whether the PSI node implementing one class is an instance
 * of another PSI node - and the collection it looped over was empty anyway, since {@code iterable}
 * is a PHP type and not an interface, so the body never ran. The intent survived as dead code
 * next door: {@code LatteTypesUtil.getIterableInterfaces()} names {@code \Iterator} and
 * {@code \Generator} and nobody calls it.
 *
 * <p>The question the inspection actually needs is not "is this Traversable" but "can foreach
 * walk it", and PHP's answer is yes for an array, for anything Traversable, and for any object at
 * all - a plain one iterates its public properties. What is left to report is a value that is
 * definitely none of those: a string, a number, a bool, a null.
 *
 * <p>An unresolved class is silent for the reason the four inspections in
 * {@link UsagesOnUnresolvedTypeTest} are: no class was read, so nothing about it is known.
 */
public class ForeachOverAnObjectTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("app/Model/Plain.php",
            "<?php declare(strict_types=1);\n\nnamespace App\\Model;\n\n"
                + "final class Plain\n{\n    public string $title = '';\n}\n");
        myFixture.addFileToProject("app/Model/Bag.php",
            "<?php declare(strict_types=1);\n\nnamespace App\\Model;\n\n"
                + "final class Bag implements \\IteratorAggregate\n{\n"
                + "    public function getIterator(): \\Traversable\n    {\n"
                + "        return new \\ArrayIterator([]);\n    }\n}\n");
    }

    public void testAnArrayIsQuiet() {
        assertEquals(List.of(), reportsOn("array"));
        assertEquals(List.of(), reportsOn("iterable"));
        assertEquals(List.of(), reportsOn("App\\Model\\Plain[]"));
    }

    public void testAnObjectIsQuietWhateverItIs() {
        assertEquals(List.of(), reportsOn("object"));
        assertEquals(List.of(), reportsOn("App\\Model\\Bag"));
        assertEquals(List.of(), reportsOn("App\\Model\\Plain"));
        assertEquals(List.of(), reportsOn("Traversable"));
        assertEquals(List.of(), reportsOn("Generator"));
        assertEquals(List.of(), reportsOn("ArrayObject"));
    }

    public void testAClassNobodyCanFindIsQuiet() {
        assertEquals(List.of(), reportsOn("App\\Model\\NotInTheIndex"));
    }

    /** A union is answered by its most permissive arm: one of them may well be walkable. */
    public void testAUnionThatCouldBeWalkedIsQuiet() {
        assertEquals(List.of(), reportsOn("App\\Model\\Bag|null"));
        assertEquals(List.of(), reportsOn("array|null"));
        assertEquals(List.of(), reportsOn("string|array"));
    }

    /**
     * The counterweight. Silence must not be reachable by an inspection that reports nothing at
     * all, so what genuinely cannot be walked has to go on being reported.
     */
    public void testWhatCannotBeWalkedIsStillReported() {
        for (String type : new String[]{"string", "int", "float", "bool", "null", "string|int"}) {
            assertFalse(type + " cannot be walked and has to be reported",
                reportsOn(type).isEmpty());
        }
    }

    /** And a type nobody could work out says nothing, as everywhere else. */
    public void testAnUnknownTypeIsQuiet() {
        assertEquals(List.of(), reportsOn("mixed"));
    }

    private List<String> reportsOn(String type) {
        myFixture.enableInspections(new LatteIterableTypeInspection());
        myFixture.configureByText("probe.latte",
            "{varType " + type + " $x}\n{foreach $x as $y}{$y}{/foreach}\n");
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null && info.getDescription().contains("foreach")) {
                problems.add(info.getDescription());
            }
        }
        return problems;
    }
}
