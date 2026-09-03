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

	// Inline Latte expression: consume up to and including the first '}' as part of content.
	// The braced group may span lines - a {php} body or a closure is routinely written that way,
	// and stopping at the newline leaves its closing '}' to be read as the macro closer below.
	"{" [^}]* "}" {
        return T_PHP_CONTENT;
    }

	// General PHP-ish starters: produce content but do not consume the macro closer '}' or a
	// newline. Allows one level of balanced braces (e.g. closures: function() { ... }), which -
	// unlike the content around it - may run across lines.
	({CLASS_NAME} | "$" | {FUNCTION_CALL} | "\"" | "'" | "(" | "[" | "|") ([^{}\r\n] | {STRING_SQ} | {STRING_DQ} | "{" [^}]* "}")* {
        return T_PHP_CONTENT;
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
