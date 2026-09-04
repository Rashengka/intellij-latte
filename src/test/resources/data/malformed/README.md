Malformed Latte fixtures
========================

Every file in this tree is **invalid on purpose**. None of them is expected to
parse cleanly, and nothing about the resulting PSI is asserted: a
`PsiErrorElement` is a pass, not a failure, and no test here looks at an error
message or at recovery quality.

The only contract is that the plugin **survives** the input:

1. **It does not throw.** Lexing and parsing either complete or fail with a
   `PsiErrorElement`. A thrown `Throwable` of any kind is a bug.
2. **It terminates.** Each file has to finish inside a per-input time budget
   (five seconds, orders of magnitude above what the whole suite needs for all
   of its fixtures together).
3. **The lexer makes progress.** Token offsets never move backwards, and the
   token count cannot exceed the character count by more than a small slack, so
   a lexer that emits a zero-width token forever fails the test instead of
   hanging the build.

Why this matters: a person typing in the editor produces broken input
constantly. Every half-written tag - `{`, `{if`, `{if $a` - is one of these
files. Reporting an error on them is fine and expected; freezing, spinning or
crashing is not.

The fixtures are driven by `dev.noctud.latte.malformed.MalformedInputTest`,
which walks this directory and adds the generated cases described below. It
needs no environment variable and no external file, so it runs on every
`./gradlew test`.


Layout
------

| Directory | What it covers |
|---|---|
| `unterminated/` | A construct that simply stops: `{`, `{if`, `{if $a`, `{="text`, `{* comment`, `n:if="`, `<div n:if=`, an unclosed element carrying an `n:` attribute, an unclosed `<script>`/`<style>`/`<!-- -->`, an unterminated filter chain, block, `{foreach}` header or `{php}` body. |
| `unbalanced/` | Pairs that do not match: a close tag with no open tag, an open tag with no close tag, crossed nesting, a close tag naming a different tag, a doubled close tag, `{else}`/`{elseif}`/`{case}`/`{sep}` outside their parent, and a pair crossed with an `n:` attribute. |
| `delimiters/` | Stray braces: a lone `}`, `{}`, `{{`, `}}`, `{{{`, `}}}`, brace runs in text, braces in `<script>` and `<style>` and in an HTML attribute value, a brace inside tag arguments, and a file that is nothing but braces. |
| `quoting/` | One quote, three quotes, mismatched quote kinds, a quote spanning a line break, an escaped quote or a trailing backslash at end of file, an unterminated quote inside a quoted or curly `n:` attribute value, an unterminated interpolation, and a file that is a single `'`. |
| `php/` | Malformed PHP inside a tag: unbalanced parentheses and brackets in both directions, a dangling operator, `$` alone, `->`, `?->` and `::` with no member, an unterminated closure, a nested unterminated array, an empty filter name, an operator-only expression, an unterminated call, and an incomplete class name in `{varType}`. |
| `n-attributes/` | Unknown prefixes and unknown names, `n:` alone, `n:inner-` and `n:tag-` with nothing after them, those prefixes on a void element, duplicate and conflicting attributes, unquoted values containing spaces or braces, an empty value, a name with no value, a value spanning a line break, nested quotes, and an `n:` attribute on a closing tag. |
| `html/` | Broken markup around Latte: stray and never-opened closing tags, unclosed nested elements, a closing tag with attributes, a tag inside a tag, an unterminated open tag, `</script>` and `</style>` with no opener, an unclosed `<script>`/`<style>` containing a Latte tag, an empty tag name, and a bare `<` at end of file. |
| `encoding/` | An empty file, a whitespace-only file, a BOM (at the start and inside a tag), a lone CR, mixed line endings, NUL and other C0 control characters, a vertical tab and form feed used as separators, zero-width and bidi overrides, a non-breaking space used as a separator, and astral-plane characters in text and in a tag name. |
| `syntax-modes/` | `{syntax off}` never closed, `{syntax}` with no argument, `{syntax}` with a nonsense argument, `{/syntax}` with no opener, `{syntax double}` never closed and closed with the wrong delimiter, nested `{syntax}` tags, `{syntax off}` inside an attribute value, inside an unclosed `<script>` and inside an HTML tag, `n:syntax` with a nonsense, empty or unquoted value, `n:syntax` on a void element, and `{contentType}` with a nonsense value, no argument, or in the middle of the file. |


Generated at runtime
--------------------

Three families are produced by the test rather than checked in, because a few
seeds plus a loop cover far more ground than thousands of near-identical files:

- **Truncation.** Twenty valid constructs, each cut at every character
  position. This is exactly what typing looks like and it is the highest-yield
  generator here. It is deterministic - a fixed seed list and a stride derived
  only from the seed's length - so a name in a failure report always refers to
  the same input.
- **Pathological size and depth.** Hundreds of levels of nesting, hundreds of
  thousands of characters on one line, tens of thousands of tags, and long
  unterminated constructs (a comment, a string, a tag, a `<script>`, an
  attribute value). This is where a quadratic or backtracking lexer shows up as
  a freeze rather than as a wrong token.
- **Unpaired surrogates.** A lone surrogate cannot be stored in a UTF-8 file,
  so those inputs exist only in the test source.


Known robustness gaps
---------------------

Nothing in this directory currently throws, and no fixture stalls the lexer.
Every finding so far is a **generated** case, and all of them are the parser
spending unbounded time on a small input; none of them is in the lexer, which
stays flat on every input here.

- **The PHP-expression parser backtracks exponentially over nested `[` and
  `(`.** `{= ` followed by 14 `[` already exceeds four seconds - fifteen
  characters of input. `{if ` followed by 96 `(` does the same, and the mixed
  form `{= ([([...` needs only eleven pairs. Closing the tag does not help,
  and *balanced* nesting blows up identically: 19 levels of `{= [[[...]]]}`
  and 16 levels of a nested array literal are both valid Latte and both take
  more than four seconds. This is the one gap here that can freeze a real
  editor at a depth a person could reach by holding a bracket key down.
- **A long run of unmatched tag openers is super-linear**, roughly `n^1.3`.
  2 000 of them (4 KB) parse in 0.19 s; 20 000 (40 KB) do not finish inside
  the budget. Milder, and it needs an unrealistic file to hit.

They are listed in `KNOWN_FAILURES` in `MalformedInputTest`, with the measured
growth curves, so the test starts failing again the moment one is fixed.


Conventions
-----------

- File names are PascalCase and end in `.latte`, matching `data/coverage/`.
- One concern per file, so a failure names the construct that caused it.
- All identifiers are generic and invented (`Article`, `$items`, `$a`,
  `partials/item.latte`). No fixture is derived from any real template.
- Everything is in English.


Adding a fixture
----------------

Drop a `.latte` file into the directory that matches its theme; the test picks
it up automatically. If it exposes a robustness bug, **leave the file exactly
as it is** and add its relative path to `KNOWN_FAILURES` in `MalformedInputTest`
with a comment naming the construct and the symptom. Softening the input,
shortening the timeout or wrapping the parse in a `try`/`catch` to get a green
suite defeats the purpose of the directory.
