package dev.noctud.latte.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * A line comment inside a tag, which Latte does not have.
 *
 * <p>Its tag lexer knows one comment and it is <code>/* &#42;/</code>. Everywhere else <code>//</code>
 * is two divisions and <code>#</code> is nothing at all, so Latte answers <code>Unexpected '/'</code>
 * and <code>Unexpected '#'</code> - and this said nothing, because <code>#note</code> parses here as
 * a reference to a block and <code>//</code> as arithmetic that happens to be meaningless.
 *
 * <p>{@code {php}} is the exception, and only halfway. Latte copies its body into the compiled
 * template as PHP, so PHP's own comment applies - but Latte writes
 * {@code ; return get_defined_vars();} directly after that body, and a comment with nothing under it
 * hides that too. The template then compiles and fails when it runs, which is the worst of the
 * three outcomes and the reason this is reported rather than left alone. Measured against Latte
 * 3.1, whose answer is a TypeError from prepare().
 *
 * <p>Three spellings stay legitimate and are asserted here so the report cannot swallow them:
 * <code>//</code> starting a destination means an absolute path, <code>#</code> in a destination is
 * an anchor, and <code>#name</code> where an argument starts is a block.
 */
public class CommentInTagInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new CommentInTagInspection());
    }

    public void testALineCommentInAnOrdinaryTagIsReported() {
        assertEquals(List.of(commentSlash()), problemsIn("{do $a = 1 // note}\n"));
        assertEquals(List.of(commentSlash()), problemsIn("{var $a = 1 // note}\n"));
        assertEquals(List.of(commentSlash()), problemsIn("{if true // note}x{/if}\n"));
    }

    public void testAHashCommentInAnOrdinaryTagIsReported() {
        assertEquals(List.of(commentHash()), problemsIn("{do $a = 1 # note}\n"));
        assertEquals(List.of(commentHash()), problemsIn("{var $a = 1 # note}\n"));
        assertEquals(List.of(commentHash()), problemsIn("{ifset $a #b}x{/ifset}\n"));
    }

    /** The one tag whose body is copied into PHP, where a comment with code under it is fine. */
    public void testACommentInsidePhpIsFineWhenSomethingFollowsIt() {
        assertEquals(List.of(), problemsIn("{php\n// note\n$a = 1;\n}\n"));
        assertEquals(List.of(), problemsIn("{php\n# note\n$a = 1;\n}\n"));
        assertEquals(List.of(), problemsIn("{php $a = 1; /* note */}\n"));
    }

    /** And is not fine when nothing does - on one line or on four, which is the same mistake. */
    public void testACommentEndingPhpIsReported() {
        assertEquals(List.of(commentPhp()), problemsIn("{php $a = 1; // note}\n"));
        assertEquals(List.of(commentPhp()), problemsIn("{php $a = 1; # note}\n"));
        assertEquals(List.of(commentPhp()), problemsIn("{php\n$a = 1;\n// note\n}\n"));
    }

    public void testADestinationBeginningWithTwoSlashesIsNotAComment() {
        assertEquals(List.of(), problemsIn("{link //Homepage:default}\n"));
        assertEquals(List.of(), problemsIn("{plink //Foo:bar}\n"));
        assertEquals(List.of(), problemsIn("<a n:href=\"//Homepage:default\">x</a>\n"));
    }

    public void testAHashInADestinationOrABlockNameIsNotAComment() {
        assertEquals(List.of(), problemsIn("{link Homepage:default#anchor}\n"));
        assertEquals(List.of(), problemsIn("{include #block}\n"));
        assertEquals(List.of(), problemsIn("{ifset #a, #b}x{/ifset}\n"));
    }

    /** Two slashes that are not next to each other are two divisions, and Latte agrees. */
    public void testDivisionIsStillDivision() {
        assertEquals(List.of(), problemsIn("{var $a = 8 / 2 / 2}\n"));
        assertEquals(List.of(), problemsIn("{var $a = '// not a comment'}\n"));
    }

    /**
     * A block comment is spelled with the same character, so the slash that closes one sits near
     * the slash of a division after it. They are still two things and neither is a comment.
     */
    public void testASlashClosingABlockCommentIsNotHalfOfALineComment() {
        assertEquals(List.of(), problemsIn("{var $a = 8 /* c */ / 2}\n"));
    }

    private static String commentSlash() {
        return "ERROR:A line comment is not part of a Latte tag; Latte reports Unexpected '/' here";
    }

    private static String commentHash() {
        return "ERROR:A line comment is not part of a Latte tag; Latte reports Unexpected '#' here";
    }

    private static String commentPhp() {
        return "ERROR:Nothing follows this comment, so it hides what Latte writes after the tag "
            + "and the template stops returning its variables";
    }

    private List<String> problemsIn(String template) {
        myFixture.configureByText("comment.latte", template);
        List<String> problems = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null) {
                problems.add(info.getSeverity().getName() + ":" + info.getDescription());
            }
        }
        return problems;
    }
}
