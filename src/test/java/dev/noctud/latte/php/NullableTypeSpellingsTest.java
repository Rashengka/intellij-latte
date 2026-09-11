package dev.noctud.latte.php;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * The three ways of writing a nullable type mean one thing, so they have to give one answer.
 *
 * <p>They did not. Of {@code ?X}, {@code null|X} and {@code X|null} only the last was read
 * properly; the first threw and the second dropped a character:
 *
 * <pre>
 *   create("?int")        StringIndexOutOfBoundsException: Range [0, -1) out of bounds
 *   create("?Foo")        StringIndexOutOfBoundsException
 *   create("?string")     null|string   - by accident, through a different path
 * </pre>
 *
 * <p>One line did all three: it decided how much to cut off with a single ternary that knew about
 * {@code null|} and about {@code |null}, and applied the second of those to {@code ?X} as well -
 * so a type shorter than six characters asked for a negative length. {@code ?string} survived
 * because it is seven, computed nonsense, and then fell through to a path that happened to read
 * the leading question mark correctly.
 *
 * <p>Nothing reached it from a template: the grammar takes the {@code ?} as a token of its own and
 * never hands the mark on in the string. It is on the way in the moment anything reads a written
 * type as text rather than as a tree, which is what {@code .ai/plans/28-rozeznat-vic-typu.md}
 * sets out to do - hence this first.
 */
public class NullableTypeSpellingsTest extends TestCase {

    public void testAllThreeSpellingsOfANullableNativeTypeAgree() {
        assertSpellingsAgree("int");
        assertSpellingsAgree("string");
        assertSpellingsAgree("bool");
        assertSpellingsAgree("float");
    }

    public void testAllThreeSpellingsOfANullableClassAgree() {
        assertSpellingsAgree("Foo");
        assertSpellingsAgree("App\\Model\\Thing");
        assertSpellingsAgree("\\App\\Model\\Thing");
    }

    /** The lengths that used to decide whether it threw. */
    public void testAShortNameDoesNotThrow() {
        assertNotNull(NettePhpType.create("?A").toString());
        assertNotNull(NettePhpType.create("?ab").toString());
        assertNotNull(NettePhpType.create("?abc").toString());
        assertNotNull(NettePhpType.create("?int").toString());
    }

    /**
     * The three spellings agreeing is not enough on its own: they would agree just as well if all
     * three came out as {@code mixed}. So one of them is held to what it names.
     */
    public void testANullableTypeNamesTheTypeAndNull() {
        assertEquals(List.of("int", "null"), partsOf("?int"));
        assertEquals(List.of("\\Foo", "null"), partsOf("?Foo"));
    }

    /** A type with no nullable marker at all is untouched by any of this. */
    public void testAPlainTypeIsUnchanged() {
        assertEquals("int", NettePhpType.create("int").toString());
        assertEquals("string", NettePhpType.create("string").toString());
        assertEquals("\\Foo", NettePhpType.create("Foo").toString());
    }

    /**
     * Compared as a set of parts, not as a string: which side of the union the {@code null} is
     * printed on is a separate, older inconsistency - {@code Foo|null} prints the class first and
     * {@code null|Foo} prints null first - and it is cosmetic. What has to hold is that the three
     * spellings name the same types, which is what was broken.
     */
    private void assertSpellingsAgree(String type) {
        List<String> questionMark = partsOf("?" + type);
        assertEquals("?" + type + " and null|" + type + " name the same types",
            partsOf("null|" + type), questionMark);
        assertEquals("?" + type + " and " + type + "|null name the same types",
            partsOf(type + "|null"), questionMark);
    }

    private List<String> partsOf(String type) {
        return List.copyOf(new TreeSet<>(Arrays.asList(NettePhpType.create(type).toString().split("\\|"))));
    }
}
