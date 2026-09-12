package dev.noctud.latte.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.psi.LatteTypes;
import dev.noctud.latte.settings.LatteTagSettings;
import dev.noctud.latte.utils.LatteHtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

/**
 * External rules for LatteParser.
 */
public class LatteParserUtil extends GeneratedParserUtilBase {
    /**
     * Looks for a classic macro a returns true if it finds the macro a and it is pair or unpaired (based on pair parameter).
     */
    public static boolean checkPairMacro(PsiBuilder builder, int level, Parser parser) {
        if (builder.getTokenType() != LatteTypes.T_MACRO_OPEN_TAG_OPEN) return false;

        PsiBuilder.Marker marker = builder.mark();
        String macroName = getMacroName(builder);

        boolean pair = parser == LatteParser.TRUE_parser_;
        boolean result;

        LatteTagSettings tag = getTag(builder);
        if (tag == null || tag.getType() == LatteTagSettings.Type.AUTO_EMPTY) {
            result = pair == isPair(macroName, builder);
        } else if (macroName.equals("_")) {
            // hard coded rule for macro _ because of dg's poor design decision
            // macro _ is pair only if it has empty arguments, otherwise it is unpaired
            // see https://github.com/nette/nette/blob/v2.1.2/Nette/Latte/Macros/CoreMacros.php#L193
            boolean emptyArgs = true;
            builder.advanceLexer();
            while (emptyArgs && nextTokenIsFast(builder, LatteTypes.T_MACRO_ARGS, LatteTypes.T_MACRO_ARGS_NUMBER, LatteTypes.T_MACRO_ARGS_STRING, LatteTypes.T_MACRO_ARGS_VAR, LatteTypes.T_PHP_METHOD)) {
                emptyArgs = (builder.getTokenText().trim().length() == 0);
                builder.advanceLexer();
            }
            result = (emptyArgs == pair);

            // all other macros which respect rules
        } else {
            result = (tag != null ? (pair ? (LatteTagSettings.Type.PAIR == tag.getType()) : LatteTagSettings.Type.unpairedSet.contains(tag.getType())) : !pair);
        }

        marker.rollbackTo();
        return result;
    }

    public static boolean checkPairHtmlTag(PsiBuilder builder, int level, Parser parser) {
        boolean pair = parser == LatteParser.TRUE_parser_;
        if (builder.getTokenType() != LatteTypes.T_HTML_OPEN_TAG_OPEN) return false;

        PsiBuilder.Marker marker = builder.mark();
        String tagName = getHtmlTagName(builder);

        boolean isVoidTag = LatteHtmlUtil.isVoidTag(tagName);
        boolean result = (!isVoidTag && pair) || (isVoidTag && !pair);

        marker.rollbackTo();
        return result;
    }

    /**
     * Stands in for the quote that ends a string when it is missing: the string is reported as Latte
     * reports it, rather than as the list of every token that could have come next.
     */
    public static boolean unterminatedString(PsiBuilder builder, int level) {
        builder.error("Unterminated string");
        return true;
    }

    public static boolean checkEmptyMacro(PsiBuilder builder, int level) {
        PsiBuilder.Marker marker = builder.mark();
        boolean result = false;
        while (true) {
            IElementType token = builder.getTokenType();
            if (token == null) {
                break;
            } else if (token == LatteTypes.T_MACRO_TAG_CLOSE) {
                break;
            } else if (token == LatteTypes.T_MACRO_TAG_CLOSE_EMPTY) {
                result = true;
                break;
            }
            builder.advanceLexer();
        }
        marker.rollbackTo();

        return result;
    }

    /**
     * Whether an {@code as} is written in what is left of this tag.
     *
     * phpForeach parses a whole expression before it looks for the {@code as} that
     * would have told it apart from any other expression, so on a tag without one it
     * parses the expression, fails, and rolls it back - and the alternative that was
     * going to match parses the identical text again. The outer rule is a list, so
     * that happened once per item and the cost of one tag grew with the square of the
     * number of items in it: an argument list, a chain of filters or a body of
     * statements cost 4x for every doubling of its length.
     *
     * Looking for the one token first costs a walk over the tag and no marker, and it
     * is the same shape of fix as the one phpArrayItem carries: find out cheaply
     * whether the expensive alternative can match before parsing anything.
     *
     * The walk stops at everything that ends a tag or an n: attribute, so it never
     * leaves the tag it was asked about - left to run to the end of the file it would
     * have replaced one quadratic cost with another.
     */
    public static boolean hasAsBeforeTheTagCloses(PsiBuilder builder, int level) {
        PsiBuilder.Marker marker = builder.mark();
        boolean result = false;
        while (true) {
            IElementType token = builder.getTokenType();
            if (token == null
                || token == LatteTypes.T_MACRO_TAG_CLOSE
                || token == LatteTypes.T_MACRO_TAG_CLOSE_EMPTY
                || token == LatteTypes.T_HTML_TAG_ATTR_SQ
                || token == LatteTypes.T_HTML_TAG_ATTR_DQ
                || token == LatteTypes.T_HTML_TAG_ATTR_CURLY_RIGHT
                || token == LatteTypes.T_HTML_TAG_CLOSE) {
                break;
            }
            if (token == LatteTypes.T_PHP_AS) {
                result = true;
                break;
            }
            builder.advanceLexer();
        }
        marker.rollbackTo();

        return result;
    }

    /**
     * Whether two semicolons are written in what is left of this tag.
     *
     * phpFor is the same shape of cost as phpForeach above and needs the same kind of
     * guard: it parses expressions up to a semicolon, twice over, before it can find
     * out that the tag holds no for-header at all. Two semicolons are the cheapest
     * thing that has to be there for it to match, so they are what is looked for.
     */
    public static boolean hasTwoSemicolonsBeforeTheTagCloses(PsiBuilder builder, int level) {
        PsiBuilder.Marker marker = builder.mark();
        int semicolons = 0;
        while (semicolons < 2) {
            IElementType token = builder.getTokenType();
            if (token == null
                || token == LatteTypes.T_MACRO_TAG_CLOSE
                || token == LatteTypes.T_MACRO_TAG_CLOSE_EMPTY
                || token == LatteTypes.T_HTML_TAG_ATTR_SQ
                || token == LatteTypes.T_HTML_TAG_ATTR_DQ
                || token == LatteTypes.T_HTML_TAG_ATTR_CURLY_RIGHT
                || token == LatteTypes.T_HTML_TAG_CLOSE) {
                break;
            }
            if (";".equals(builder.getTokenText())) {
                semicolons++;
            }
            builder.advanceLexer();
        }
        marker.rollbackTo();

        return semicolons >= 2;
    }

    /**
     * Whether the bracket at the current position has its own closing one still inside this tag.
     *
     * phpArray parses a whole array before it can find out that the brackets never close, and it
     * recurses back into itself, so a run of unclosed brackets cost the square of its length: 512
     * of them took 623 ms and an editor freezes on far less. Looking for the closing bracket first
     * costs a walk over the tag and no marker.
     *
     * A pin on the rule would be cheaper still and is the usual answer to an alternative that
     * parses a subtree and backs out - but it was tried and it turned two correct templates red,
     * because a bracket that opens no array then reports an error instead of letting the next
     * alternative have it. In this plugin a false report is the one thing that must not happen, so
     * the more expensive guard is the right one.
     *
     * Anything that is not a bracket is let through: the rule's other form is {@code array(...)},
     * and deciding for it is not this guard's business.
     */
    public static boolean hasMatchingBracketBeforeTagCloses(PsiBuilder builder, int level) {
        if (builder.getTokenType() != LatteTypes.T_PHP_LEFT_BRACKET) {
            return true;
        }
        PsiBuilder.Marker marker = builder.mark();
        int depth = 0;
        boolean closed = false;
        while (true) {
            IElementType token = builder.getTokenType();
            if (token == null
                || token == LatteTypes.T_MACRO_TAG_CLOSE
                || token == LatteTypes.T_MACRO_TAG_CLOSE_EMPTY
                || token == LatteTypes.T_HTML_TAG_ATTR_SQ
                || token == LatteTypes.T_HTML_TAG_ATTR_DQ
                || token == LatteTypes.T_HTML_TAG_ATTR_CURLY_RIGHT
                || token == LatteTypes.T_HTML_TAG_CLOSE) {
                break;
            }
            if (token == LatteTypes.T_PHP_LEFT_BRACKET) {
                depth++;
            } else if (token == LatteTypes.T_PHP_RIGHT_BRACKET) {
                depth--;
                if (depth == 0) {
                    closed = true;
                    break;
                }
            }
            builder.advanceLexer();
        }
        marker.rollbackTo();

        return closed;
    }

    @NotNull
    private static String getMacroName(PsiBuilder builder) {
        String macroName;

        consumeTokenFast(builder, LatteTypes.T_MACRO_OPEN_TAG_OPEN);
        consumeTokenFast(builder, LatteTypes.T_MACRO_CLOSE_TAG_OPEN);
        consumeTokenFast(builder, LatteTypes.T_MACRO_NOESCAPE);
        if (nextTokenIsFast(builder, LatteTypes.T_MACRO_NAME, LatteTypes.T_MACRO_SHORTNAME)) {
            macroName = builder.getTokenText();
            assert macroName != null;

        } else {
            macroName = "=";
        }
        return macroName;
    }

    @NotNull
    private static String getHtmlTagName(PsiBuilder builder) {
        String macroName = "?";

        consumeTokenFast(builder, LatteTypes.T_HTML_OPEN_TAG_OPEN);
        if (nextTokenIsFast(builder, LatteTypes.T_TEXT)) {
            macroName = builder.getTokenText();
            if (macroName != null && macroName.length() > 0) {
                macroName = macroName.split(" ")[0];

            } else {
                macroName = "?";
            }
        }
        return macroName;
    }

    private static boolean isPair(String macroName, PsiBuilder builder) {
        builder.advanceLexer();
        IElementType type = builder.getTokenType();
        while (type != null) {
            if (type == LatteTypes.T_MACRO_TAG_CLOSE_EMPTY) {
                return true;
            } else if (type == LatteTypes.T_MACRO_TAG_CLOSE) {
                break;
            }
            builder.advanceLexer();
            type = builder.getTokenType();
        }
        int depth = 0;
        while (type != null) {
            if (nextTokenIsFast(builder, LatteTypes.T_MACRO_CLOSE_TAG_OPEN, LatteTypes.T_MACRO_OPEN_TAG_OPEN) && getMacroName(builder).equals(macroName)) {
                if (type == LatteTypes.T_MACRO_CLOSE_TAG_OPEN) {
                    if (depth == 0) {
                        return true;
                    }
                    depth--;
                } else {
                    depth++;
                }
            } else if (type == LatteTypes.T_HTML_TAG_NATTR_NAME && ("n:" + macroName).equals(builder.getTokenText())) {
                builder.advanceLexer();
                type = builder.getTokenType();
                continue;
            }
            builder.advanceLexer();
            type = builder.getTokenType();
        }
        return false;
    }

    private static boolean isEmptyPair(PsiBuilder builder) {
        IElementType type = builder.getTokenType();
        while (type != null) {
            if (type == LatteTypes.T_MACRO_TAG_CLOSE_EMPTY) {
                return true;
            } else if (type == LatteTypes.T_MACRO_TAG_CLOSE) {
                return false;
            }
            builder.advanceLexer();
            type = builder.getTokenType();
        }
        return false;
    }

    public static boolean isNamespace(PsiBuilder builder, int level) {
        PsiBuilder.Marker marker = builder.mark();

        boolean result = false;

        IElementType type = builder.getTokenType();
        IElementType nextToken = builder.lookAhead(1);
        if (type == LatteTypes.T_PHP_NAMESPACE_REFERENCE && nextToken == LatteTypes.T_PHP_NAMESPACE_RESOLUTION) {
            result = true;
        }

        marker.rollbackTo();

        return result;
    }

    /**
     * Consumes a written-out type that the structured rule could not read, up to the variable it
     * belongs to.
     *
     * <p>Latte does not parse a type either. {@code TagParser::parseType()} takes a run of tokens
     * from a fixed set and glues their text together, and Latte 2.11 takes everything before the
     * variable without a filter at all - so a plugin that models a type with a grammar is
     * stricter than the language, which is the definition of a false report. Nineteen of the
     * thirty-nine shapes Latte accepts were reported before this.
     *
     * <p>This is the second alternative of {@code phpFirstTypedVariable}, so it is reached only
     * when the structured one has failed over the whole of the type. Everything the structured
     * rule reads today is therefore read the same way, and what reaches here has no shape the
     * plugin can work out - which is why the node it makes carries no type and the answer is
     * {@code mixed}.
     *
     * <p>The run ends at the variable and never crosses what closes a tag or an attribute; a run
     * with no variable after it is not a type and the rule fails, leaving the text to be read as
     * it was.
     */
    public static boolean consumeOpaqueType(PsiBuilder builder, int level) {
        if (!startsTheContentOfATag(builder)) {
            return false;
        }
        PsiBuilder.Marker marker = builder.mark();
        boolean consumedAnything = false;
        int depth = 0;
        while (true) {
            IElementType token = builder.getTokenType();
            if (token == LatteTypes.T_MACRO_ARGS_VAR) {
                break;
            }
            String text = builder.getTokenText();
            if (!canStandInAType(token, text) || !belongsToATypeHere(builder, token, text, depth, consumedAnything)) {
                marker.rollbackTo();
                return false;
            }
            depth += depthChange(text);
            if (depth < 0) {
                marker.rollbackTo();
                return false;
            }
            builder.advanceLexer();
            consumedAnything = true;
        }
        if (consumedAnything && depth == 0) {
            marker.drop();
            return true;
        }
        marker.rollbackTo();
        return false;
    }

    /**
     * Two things a written-out type never does, and both of them are what a tag does instead.
     *
     * <p>Restricting the rule to the front of a tag was not enough: {@code {define input, string
     * $name}} put {@code input, string} in front of the variable and {@code {php list($first,
     * $second) = …}} put {@code list(} there, and both were swallowed whole as one type.
     *
     * <ul>
     *   <li>A comma at the top level separates declarations; inside a type it only ever appears
     *       within {@code <>} or {@code {}}, which is why the depth is counted.
     *   <li>An opening bracket after a name is a call. A type may be wrapped in brackets -
     *       {@code (A&B)|null} - but the bracket then comes first. This is also, on its own, why
     *       {@code callable(int): string} is refused, which is what Latte does with it too.
     * </ul>
     */
    private static boolean belongsToATypeHere(PsiBuilder builder, IElementType token, String text, int depth, boolean anythingBefore) {
        if (text == null) {
            return false;
        }
        if (depth == 0 && text.indexOf(',') >= 0) {
            return false;
        }
        if (anythingBefore && depth == 0 && text.indexOf('(') >= 0) {
            return false;
        }
        // A hyphen belongs to a type only inside a name - class-string, positive-int - never as an
        // operator between two of them. Latte reads the first as one identifier and refuses the
        // second, and what tells them apart is the space, so that is what is asked about.
        return token != LatteTypes.T_PHP_ADDITIVE_OPERATOR || isGluedToItsNeighbours(builder);
    }

    /** Whether the token at the cursor has no whitespace on either side of it. */
    private static boolean isGluedToItsNeighbours(PsiBuilder builder) {
        IElementType before = builder.rawLookup(-1);
        IElementType after = builder.rawLookup(1);
        return before != null
            && after != null
            && !LatteParserDefinition.WHITE_SPACES.contains(before)
            && !LatteParserDefinition.WHITE_SPACES.contains(after);
    }

    /** How much of a bracket run opens or closes; ">>" closes two generics at once. */
    private static int depthChange(String text) {
        int change = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '<' || character == '{' || character == '[' || character == '(') {
                change++;
            } else if (character == '>' || character == '}' || character == ']' || character == ')') {
                change--;
            }
        }
        return change;
    }

    /**
     * Whether the parser stands at the first thing a tag holds.
     *
     * <p>Without this the rule was reached wherever a name is followed by a variable, which in a
     * template is most of PHP: {@code catch (Exception $e)}, a closure's parameters,
     * {@code outer(inner($v))}. All of it turned into one opaque type and seventeen tests said so.
     *
     * <p>A written-out type that this rule is for stands at the front of the tag -
     * {@code {varType T $a}}, {@code {var T $a}}, {@code {parameters T $a}} - so that position is
     * the whole of the licence. It costs one look back rather than a walk to the tag's name, which
     * matters in a rule tried once per item of a list; and where it says no, the text is read the
     * way it was read before, so being narrow here withholds a report rather than inventing one.
     */
    private static boolean startsTheContentOfATag(PsiBuilder builder) {
        for (int steps = -1; steps >= -LOOK_BEHIND_LIMIT; steps--) {
            IElementType previous = builder.rawLookup(steps);
            if (previous == null) {
                return false;
            }
            if (LatteParserDefinition.WHITE_SPACES.contains(previous) || LatteParserDefinition.COMMENTS.contains(previous)) {
                continue;
            }
            return previous == LatteTypes.T_MACRO_NAME
                || previous == LatteTypes.T_MACRO_SHORTNAME
                || previous == LatteTypes.T_HTML_TAG_NATTR_NAME;
        }
        return false;
    }

    /**
     * Whether one token may stand in a written-out type, which is the same question Latte asks.
     *
     * <p>Copying the 2.11 rule instead - everything up to the variable - would be simpler and is
     * wrong: it accepts what Latte 3 refuses, and a guard that accepts everything has stopped
     * guarding. The list is the one measured against Latte 3.1.6, which is the stricter of the
     * two ends and therefore the boundary worth having.
     */
    private static boolean canStandInAType(IElementType token, String text) {
        if (token == null || !STANDS_IN_A_TYPE.contains(token)) {
            return false;
        }
        // Latte's own token set carries false and has no entry for true, so true is not a type to
        // it. Both reach this lexer as the same keyword token, which is why the text decides.
        if ("true".equalsIgnoreCase(text)) {
            return false;
        }
        // Everything the lexer could not place lands in one token, so its text is what says
        // whether it is punctuation a type is written with - or something like '@' that is not.
        return !WRITTEN_AS_PUNCTUATION.contains(token) || isAllPunctuation(text);
    }

    /**
     * A run of punctuation arrives as one token, so every character of it has to be punctuation a
     * type is written with. Length is not the test: the ">>" that closes two nested generics is a
     * single token here, and Latte lists it as one too - the comment beside it in
     * {@code parseType()} says "in nested generics like array&lt;int, array&lt;string, mixed&gt;&gt;".
     */
    private static boolean isAllPunctuation(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (PUNCTUATION.indexOf(text.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /** The punctuation Latte lists in {@code parseType()}, plus the brackets its lexer splits off. */
    private static final String PUNCTUATION = "()<>[]|&{}:,=?";

    /**
     * Tokens whose text is what decides, because the lexer put more than one thing in them.
     *
     * <p>The catch-all carries the punctuation a type is written with and also everything else it
     * could not place. The relational operator is here because a backslash inside a generic puts
     * the lexer into a state where the closing angle bracket is a comparison -
     * {@code array<int, App\Model\Thing>} lexes its '>' differently from
     * {@code array<int, string>}, which is why the first was reported and the second was not.
     */
    private static final TokenSet WRITTEN_AS_PUNCTUATION = TokenSet.create(
        LatteTypes.T_MACRO_ARGS,
        LatteTypes.T_PHP_RELATIONAL_OPERATOR
    );

    /**
     * Token types measured to appear in the types Latte accepts. A type ends at the variable it
     * belongs to; anything else here is a run that is not a type, and the rule then fails and
     * leaves the text to be read as it was.
     */
    private static final TokenSet STANDS_IN_A_TYPE = TokenSet.create(
        LatteTypes.T_MACRO_ARGS,
        LatteTypes.T_MACRO_ARGS_NUMBER,
        LatteTypes.T_PHP_ADDITIVE_OPERATOR,
        LatteTypes.T_PHP_COLON,
        LatteTypes.T_PHP_IDENTIFIER,
        LatteTypes.T_PHP_KEYWORD,
        LatteTypes.T_PHP_LEFT_BRACKET,
        LatteTypes.T_PHP_LEFT_CURLY_BRACE,
        LatteTypes.T_PHP_LEFT_NORMAL_BRACE,
        LatteTypes.T_PHP_MIXED,
        LatteTypes.T_PHP_NAMESPACE_REFERENCE,
        LatteTypes.T_PHP_NAMESPACE_RESOLUTION,
        LatteTypes.T_PHP_NULL,
        LatteTypes.T_PHP_NULL_MARK,
        LatteTypes.T_PHP_OR_INCLUSIVE,
        LatteTypes.T_PHP_REFERENCE_OPERATOR,
        LatteTypes.T_PHP_RELATIONAL_OPERATOR,
        LatteTypes.T_PHP_RIGHT_BRACKET,
        LatteTypes.T_PHP_RIGHT_CURLY_BRACE,
        LatteTypes.T_PHP_RIGHT_NORMAL_BRACE,
        LatteTypes.T_PHP_TYPE
    );

    /**
     * The three names that stand where a class name stands and are not one. The lexer has no rule
     * for them, so each arrives as an ordinary identifier - without this, {@code self::render()}
     * would be reported as a class {@code \self} that does not exist.
     */
    private static final Set<String> NOT_A_CLASS_NAME = Set.of("self", "static", "parent");

    /**
     * How far back the search for {@code new} or {@code instanceof} looks. Only whitespace and
     * comments are stepped over and the lexer gives whitespace in whole runs, so anything past
     * this is comments written between the keyword and the name. Going no further makes the look
     * behind cost the same on every name rather than growing with the tag - the shape that made
     * this parser quadratic once already - and the answer it gives up on is "not a class", which
     * is the silent one.
     */
    private static final int LOOK_BEHIND_LIMIT = 8;

    /**
     * Whether a name written without a namespace is a class name.
     *
     * <p>{@code phpClassUsage} otherwise begins at a backslash, so {@code App\Model\Foo::make()}
     * was a class reference and {@code Foo::make()} was a loose identifier that no inspection had
     * an element for. The two spellings name the same class and are now read the same way.
     *
     * <p>Only the places PHP settles on its own are read that way: a {@code ::} after the name, a
     * {@code new} or an {@code instanceof} in front of it. A bare name anywhere else is a constant
     * fetch - {@code {=PHP_EOL}} prints one - and the parser cannot tell those two apart, so it does
     * not try.
     */
    public static boolean isUnqualifiedClassName(PsiBuilder builder, int level) {
        if (builder.getTokenType() != LatteTypes.T_PHP_IDENTIFIER) {
            return false;
        }

        String name = builder.getTokenText();
        if (name == null || NOT_A_CLASS_NAME.contains(name.toLowerCase(Locale.ROOT))) {
            return false;
        }

        if (builder.lookAhead(1) == LatteTypes.T_PHP_DOUBLE_COLON) {
            return true;
        }

        return followsNewOrInstanceof(builder);
    }

    private static boolean followsNewOrInstanceof(PsiBuilder builder) {
        for (int steps = -1; steps >= -LOOK_BEHIND_LIMIT; steps--) {
            IElementType type = builder.rawLookup(steps);
            if (type == null) {
                return false;
            }
            if (LatteParserDefinition.WHITE_SPACES.contains(type) || LatteParserDefinition.COMMENTS.contains(type)) {
                continue;
            }
            if (type == LatteTypes.T_PHP_NEW) {
                return true;
            }
            // instanceof shares its token with every other keyword, so the text is what tells it
            // apart - "case Foo" and "use Foo" reach here as the same token type.
            return type == LatteTypes.T_PHP_KEYWORD && "instanceof".equalsIgnoreCase(rawTextAt(builder, steps));
        }
        return false;
    }

    private static String rawTextAt(PsiBuilder builder, int steps) {
        CharSequence text = builder.getOriginalText();
        int start = builder.rawTokenTypeStart(steps);
        int end = builder.rawTokenTypeStart(steps + 1);
        if (start < 0 || end > text.length() || start >= end) {
            return "";
        }
        return text.subSequence(start, end).toString();
    }

    private static LatteTagSettings getTag(PsiBuilder builder) {
        return LatteConfiguration.getInstance(builder.getProject()).getTag(getMacroName(builder));
    }

}
