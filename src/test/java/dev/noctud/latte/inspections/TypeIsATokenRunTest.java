package dev.noctud.latte.inspections;

import dev.noctud.latte.BasePsiParsingTestCase;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.inspections.utils.LatteInspectionInfo;
import dev.noctud.latte.settings.LatteSettings;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Whatever Latte takes as a type, the plugin has to take too.
 *
 * <p>Latte does not parse a type. {@code Latte\Compiler\TagParser::parseType()} consumes a run of
 * tokens from a fixed set and glues their text together; Latte 2.11 is looser still and takes
 * everything before the variable. The plugin models a type with a grammar, so it is stricter than
 * the language - and stricter than the language is the definition of a false report.
 *
 * <p>The whole matrix is asserted rather than a chosen few. Choosing is how
 * {@code MacroVarInspectionTest} came to assert a false report for years, and the count is the
 * point here: nineteen of the thirty-nine shapes Latte accepts were reported.
 *
 * <p>The three shapes Latte itself rejects are asserted too. Without them the promise "we accept
 * what Latte accepts" could be kept by reporting nothing at all, which is a guard that can be
 * satisfied without doing its job.
 *
 * <p>Measured against both ends of the supported range, 2.11.7 and 3.1.6:
 * {@code .ai/reports/26-typy-co-latte-prijima.txt}.
 */
public class TypeIsATokenRunTest extends BasePsiParsingTestCase {

    /** Every one of these compiles, lints and renders in Latte 2.11.7 and 3.1.6. */
    private static final String[] LATTE_ACCEPTS = {
        // built-in types
        "string", "int", "bool", "float", "array", "object", "callable", "iterable",
        "mixed", "void", "never", "null", "false",
        // the three that stand for a class without naming one
        "self", "static", "parent",
        // nullable, union, intersection, grouping
        "?string", "?Foo", "string|null", "string|int", "A&B", "(A&B)|null",
        // arrays
        "string[]", "int[][]",
        // generics
        "array<int>", "array<string, mixed>", "array<int, array<string, mixed>>",
        // a qualified name inside a generic: the backslash puts the lexer into a state where the
        // closing angle bracket is a comparison, so this lexes differently from array<int, string>
        "array<int, App\\Model\\Thing>", "array < int , App\\Model\\Thing >",
        "list<int>", "iterable<Foo>", "array<string, mixed>|null",
        // array shapes and ranges
        "array{a: int, b: string}", "int<0, 100>",
        // names
        "Foo", "Foo\\Bar", "\\Foo\\Bar",
        // literal types
        "1|2|3",
        // the hyphenated PHPDoc types, which Latte takes as ordinary identifiers
        "class-string", "positive-int", "non-empty-list<int>",
    };

    /** And these Latte 3.1.6 refuses, so the plugin has to go on refusing them. */
    private static final String[] LATTE_REFUSES = {
        "true",                   // Latte's token set has Php_False but no Php_True
        "callable(int): string",  // (int) lexes as a cast, not as brackets round a name
        "'a'|'b'",                // literal string types
        "@@@",                    // and plain nonsense, which is what proves the guard still guards
        "a - b",                  // a hyphen between names is an operator, not part of one
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LatteConfiguration.getInstance(getProject());
        getProject().registerService(LatteSettings.class);
    }

    @Override
    protected String getTestDataPath() {
        return "";
    }

    @Test
    public void testEveryTypeLatteAcceptsIsQuietInVarType() {
        assertQuiet("{varType ", " $a}", LATTE_ACCEPTS);
    }

    @Test
    public void testEveryTypeLatteAcceptsIsQuietInVar() {
        assertQuiet("{var ", " $a = null}", LATTE_ACCEPTS);
    }

    @Test
    public void testEveryTypeLatteAcceptsIsQuietInAVarDeclarationWithoutAValue() {
        assertQuiet("{var ", " $a}", LATTE_ACCEPTS);
    }

    @Test
    public void testEveryTypeLatteAcceptsIsQuietInParameters() {
        assertQuiet("{parameters ", " $a}", LATTE_ACCEPTS);
    }

    /**
     * The nullable written on the wrong side of the variable. Latte refuses it too
     * ("Unexpected '|', expecting end of tag"), so the report is right and stays.
     */
    @Test
    public void testANullableWrittenAfterTheVariableIsStillReported() {
        Assert.assertFalse(
            "Latte refuses {varType array $a|null}, so the plugin has to as well",
            problemsIn("{varType array $a|null}").isEmpty()
        );
    }

    @Test
    public void testWhatLatteRefusesIsStillReported() {
        List<String> quiet = new ArrayList<>();
        for (String type : LATTE_REFUSES) {
            if (problemsIn("{varType " + type + " $a}").isEmpty()) {
                quiet.add(type);
            }
        }
        Assert.assertEquals(
            "Latte 3.1.6 refuses these and the plugin has to as well - a guard that accepts"
                + " everything is one that has stopped guarding",
            List.of(),
            quiet
        );
    }

    private void assertQuiet(String before, String after, String[] types) {
        List<String> reported = new ArrayList<>();
        for (String type : types) {
            List<String> problems = problemsIn(before + type + after);
            if (!problems.isEmpty()) {
                reported.add(type + " -> " + problems);
            }
        }
        Assert.assertEquals(
            "Latte accepts every one of these, so none of them may be reported",
            List.of(),
            reported
        );
    }

    private List<String> problemsIn(String template) {
        List<String> descriptions = new ArrayList<>();
        for (LatteInspectionInfo problem : new MacroVarTypeInspection().checkFile(createPsiFile("a", template))) {
            descriptions.add(problem.getDescription());
        }
        for (LatteInspectionInfo problem : new MacroVarInspection().checkFile(createPsiFile("b", template))) {
            descriptions.add(problem.getDescription());
        }
        return descriptions;
    }
}
