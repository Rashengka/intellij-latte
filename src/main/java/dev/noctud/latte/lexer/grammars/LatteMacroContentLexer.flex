package dev.noctud.latte.lexer;

import com.intellij.psi.tree.IElementType;
import static dev.noctud.latte.psi.LatteTypes.*;

%%

%class LatteMacroContentLexer
%extends LatteBaseFlexLexer
%function advance
%type IElementType
%unicode
%ignorecase

%state PHP_BODY
%state PHP_BRACES

%{
	private int phpNestingDepth = 0;

	/**
	 * Depth at which a braced group is closed where it stands instead of being followed further.
	 * Real code nests a handful of levels inside one tag, so this is never reached by a template;
	 * see the same guard in LatteTopLexer for what it keeps out.
	 */
	private static final int MAX_PHP_NESTING_DEPTH = 16;
%}

WHITE_SPACE=[ \t\r\n]+
SYMBOL = [_\p{L}][_0-9\p{L}]*(-[_0-9\p{L}]+)*
FUNCTION_CALL=[a-zA-Z_][a-zA-Z0-9_]* "("
CLASS_NAME=\\?[a-zA-Z_][a-zA-Z0-9_]*\\[a-zA-Z_][a-zA-Z0-9_\\]* | \\[a-zA-Z_][a-zA-Z0-9_]*
CONTENT_TYPE=[a-zA-Z\-][a-zA-Z0-9\-]*\/[a-zA-Z\-][a-zA-Z0-9\-\.]*
FILE_IMPORT=[\w\-.@()#$%\^&*()!\/]+ ".latte"
SIGNAL=[a-zA-Z\-\:]+ "!"
// A quoted literal is content as a whole, newlines included - PHP allows them and cutting the
// content at the newline left the closing quote to be read as an opening one.
STRING_SQ = "'" ("\\" [^] | [^'\\])* "'"
STRING_DQ = "\"" ("\\" [^] | [^\"\\])* "\""

%%


<YYINITIAL> {

	// Inline Latte expression: the braced group is content. It may span lines - a {php} body or a
	// closure is routinely written that way, and stopping at the newline leaves its closing '}' to
	// be read as the macro closer below. PHP_BRACES reads it and returns the token.
	"{" {
        phpNestingDepth = 1;
        pushState(PHP_BRACES);
    }

	// General PHP-ish starters: produce content but do not consume the macro closer '}' or a
	// newline. The run continues in PHP_BODY, which lets a braced group of any depth through.
	// A complete literal starts the match as a whole so that an unbalanced brace inside it is
	// content rather than the macro closer. The bare quotes stay in the alternation as well: while
	// the literal is being typed it has no closing quote yet, and the editor lexes that state on
	// every keystroke.
	({CLASS_NAME} | "$" | {FUNCTION_CALL} | {STRING_SQ} | {STRING_DQ} | "\"" | "'" | "(" | "[" | "|") ([^{}\r\n] | {STRING_SQ} | {STRING_DQ})* {
        pushState(PHP_BODY);
    }

    {CONTENT_TYPE} {
        return T_PHP_CONTENT;
    }

	([0-9]+ | {SYMBOL} | {CLASS_NAME}) {
		return T_PHP_CONTENT;
	}

    {WHITE_SPACE} {
        return T_WHITESPACE;
    }

    {FILE_IMPORT} {
        return T_FILE_PATH;
    }

    {SIGNAL} {
        return T_LINK;
    }

    // Explicitly expose macro close to the parser
    "}" {
        return T_MACRO_TAG_CLOSE;
    }

	[^] {
		return T_MACRO_ARGS;
	}
}

// Rest of a PHP-ish run started in YYINITIAL. Nothing is returned until the run ends, so the
// T_PHP_CONTENT that closes it spans the starter and everything read here.
<PHP_BODY> {
	"{" {
		phpNestingDepth = 1;
		pushState(PHP_BRACES);
	}

	([^{}\r\n] | {STRING_SQ} | {STRING_DQ})+ {
	}

	// The macro closer or a line break ends the run; both are left for YYINITIAL to read.
	[^] {
		rollbackMatch();
		popState();
		return T_PHP_CONTENT;
	}

	<<EOF>> {
		popState();
		return T_PHP_CONTENT;
	}
}

// A braced group, counted rather than matched by a pattern so that a block nested to any depth -
// a {php} body, a closure inside a closure - stays inside the group. A quoted literal is taken
// whole, which keeps a brace inside it from being counted at all.
<PHP_BRACES> {
	{STRING_SQ} | {STRING_DQ} {
	}

	"{" {
		phpNestingDepth++;
		if (phpNestingDepth > MAX_PHP_NESTING_DEPTH) {
			popState();
			if (yystate() == PHP_BODY) {
				popState();
			}
			return T_PHP_CONTENT;
		}
	}

	"}" {
		phpNestingDepth--;
		if (phpNestingDepth == 0) {
			popState();
			if (yystate() == YYINITIAL) {
				return T_PHP_CONTENT;
			}
		}
	}

	[^{}]+ {
	}

	// An unclosed group ends with the content - the editor sees that on every keystroke. Both
	// states are left before returning so that the end of input is reported once.
	<<EOF>> {
		popState();
		if (yystate() == PHP_BODY) {
			popState();
		}
		return T_PHP_CONTENT;
	}
}
