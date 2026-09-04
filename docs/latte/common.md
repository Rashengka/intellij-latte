Constant across 2.11 to 3.1
===========================

Everything on this page behaves the same way in Latte 2.11.7 and in every 3.0.x
and 3.1.x release. The plugin never needs a version branch for any of it. It is
recorded here so that the per-version chapters can stay short and so that a reader
can tell "unchanged" apart from "not yet investigated".


Tag delimiters and the general tag shape
----------------------------------------

A Latte tag is `{name arguments |filters}`. The opening brace is only a tag
opener when it is **not** followed by whitespace, a quote or another brace. Both
lines use the same negative lookahead, spelled `\{(?![\s'"{}])`:

- Latte 2.11: `Latte\Parser::$syntaxes['latte']`
- Latte 3.x: `Latte\Compiler\TemplateLexer::setSyntax()`, the `$left` local

This is why `{ $x }` and `{"json": 1}` are plain text in both lines, and why the
same CSS or JavaScript that survives one version survives the other.

A paired tag closes with `{/name}` or the shorthand `{/}`. An unpaired tag that
wants to be explicitly self-closing writes `{name /}`.

Comments are `{* ... *}` in both lines and are removed at compile time. The
comment opener is the current opening delimiter followed by `*`, so under
`{syntax double}` a comment is `{{* ... *}}`.


Printing
--------

- `{$var}`, `{$obj->prop}`, `{$arr['k']}`, `{expr}` print with context-aware
  escaping.
- `{=expr}` is the explicit print tag. It exists in both lines and is registered
  under the literal tag name `=`.
- `{l}` prints a literal `{`, `{r}` prints a literal `}`.

Escaping is context aware in both lines: the engine tracks whether the print
site is in HTML text, an HTML attribute, inside `<script>`, inside `<style>`, in
a URL attribute, or in an HTML comment, and picks the escaper accordingly. The
details of what happens in each context are not identical across versions —
see `latte-3.1.md` for the HTML attribute changes — but the *existence* of
context-aware escaping and the set of contexts are constant.


Filters
-------

Applied with `|`, chained left to right, and always at the end of a tag:

    {$name|upper|truncate:30}

The first separator between a filter name and its first argument may be `:` or
`,` in both lines. Separators *between* arguments differ; see
`migration-2-to-3.md`.

Two filters are not filters at all — they are compiler directives recognised by
name and stripped before the filter chain is built. They exist in both lines and
are never present in any filter registry:

| Directive | Where it is legal | Effect |
|---|---|---|
| `\|noescape` | any print tag, `{block}`, `{include}`, `{_}` | suppress escaping |
| `\|nocheck` | URL attributes | suppress URL sanitisation |

Because these are compiler-level, an inspection that reports "undefined filter"
must special-case them. Latte 3.1 adds two more of the same kind — see
`latte-3.1.md`.

Two details that are easy to get wrong and are **not** constant:

- **`|noiterator` and `|nocheck` on `{foreach}` are not usable across the whole
  range.** They work in 2.11 and in 3.0.0-3.0.6; from 3.0.7 the tag argument is
  parsed by a dedicated `parseForeach()` grammar that rejects the `|` before
  `ForeachNode::create()` ever gets to call `parseModifier()`, so
  `{foreach $a as $i|noiterator}` is `Unexpected '|'`. The handling code in
  `Essential/Nodes/ForeachNode.php:52-58` is still there and still unreachable.
  Verified by compiling that snippet against every `v3.*` tag.
- **Directive names are matched case-insensitively in 2.11 and case-sensitively
  in 3.x.** Latte 2's `Helpers::removeFilter()` (`Helpers.php:65`) uses a `#Di`
  pattern, so `|noescape`, `|noEscape` and `|NOESCAPE` are all the same
  directive. Latte 3 matches literally and hardcodes exactly two camelCase
  aliases: `noCheck` (`Essential/Passes.php:127`) and `noIterator`
  (`Essential/Nodes/ForeachNode.php:55`). `|noEscape` in Latte 3 is not the
  directive: it compiles, then fails at render time with
  `Filter 'noEscape' is not defined or not allowed here`. Verified by rendering
  `{="<b>"|noEscape}` on both lines — `'<b>'` under 2.11.7, the exception under
  3.1.6.


n:attributes
------------

Any paired tag can also be written as an HTML attribute:

    <li n:foreach="$items as $item">{$item}</li>

Three prefixes exist in both lines and mean the same thing:

| Form | Placement of the generated code |
|---|---|
| `n:name` | wraps the whole element, opening tag included |
| `n:inner-name` | wraps only the element's content |
| `n:tag-name` | wraps only the element's opening and closing tags |

`n:inner-` and `n:tag-` are only valid on a paired tag, and neither is valid on a
void element. Latte 2 reports the void-element case as `E_USER_WARNING`
(`Compiler::writeAttrsMacro()`); Latte 3 reports it as a compile error.

Some tags exist **only** as attributes and have no `{tag}` form. The set differs
between the lines, but the concept is the same. In Latte 2 the check is in each
macro (`CoreMacros::macroTag()`, `macroIfContent()`); in Latte 3 the tag is
registered under a name that already starts with `n:`
(`TemplateParser::addTags()`).


Blocks and inheritance
----------------------

The whole inheritance model is unchanged:

- `{block name}` defines and immediately prints a block; a child template
  overrides it by defining a block of the same name.
- `{define name}` defines a block without printing it. It takes parameters in
  both lines, and both accept the `local` modifier (`{define local foo}`).
- `{include name}` / `{include file "x.latte"}` render a block or a file. Five
  further spellings exist in both lines and a reference resolver has to know all
  of them: `{include block name}` and `{include #name}` force the block reading,
  `{include file "x"}` forces the file reading, `{include name from "x.latte"}`
  renders a block defined in another file, and `{include "x.latte" with blocks}`
  renders a file with the current template's blocks in scope (this is what
  replaced `{includeblock}`). `{include parent}` and `{include this}` name the
  enclosing `{block}`/`{define}` and are a compile error outside one.
  (Latte 2 `Macros/BlockMacros.php:141-190` and `Macros/CoreMacros.php`
  `macroInclude()`; Latte 3 `Essential/Nodes/IncludeBlockNode.php` and
  `IncludeFileNode.php`.)
- `{extends}` and its alias `{layout}` name the parent template.
- `{import}` pulls blocks in from another template.
- `{embed}` renders another template while letting the caller override its
  blocks.
- Block names may be dynamic: `{block $name}` and `{include $name}`.
- `{ifset #blockName}` and `{ifset block blockName}` test whether a block exists.
  Both spellings work in both lines (Latte 2 `BlockMacros::macroIfset()`, Latte 3
  `Essential\Nodes\IfNode::buildCondition()`).

`{define}` in both lines lets a union type reach the parser through the modifier
position, so `{define foo, string|int $a}` parses.


Control flow
------------

The following tags exist in both lines with the same meaning:

`{if}`, `{elseif}`, `{else}`, `{ifset}`, `{elseifset}`, `{ifchanged}`,
`{switch}` with `{case}` and `{default}`, `{foreach}`, `{for}`, `{while}`,
`{iterateWhile}`, `{first}`, `{last}`, `{sep}`, `{continueIf}`, `{breakIf}`,
`{skipIf}`, `{try}` with `{else}`, `{rollback}`.

`{if}` with no arguments is the capturing form: the body is buffered and the
condition is read from the matching `{/if}`. This works in both lines (Latte 2
`CoreMacros::macroIf()`, Latte 3 `IfNode::$capture`).

`$iterator` is available inside `{foreach}` in both lines and is an instance of
the engine's caching iterator.


Variables and types
-------------------

- `{var $a = 1, $b = 2}` and `{default $a = 1}`.
- `{parameters ...}`, `{varType}`, `{templateType}`, `{varPrint}`,
  `{templatePrint}` — all five exist in both lines and all five are restricted to
  the template head.
- `{capture $var}...{/capture}`.
- `{contentType ...}` is restricted to the template head, with one exception in
  both lines: it is also allowed inside a `<script>` element when the argument
  contains `html`.


Other tags present in both lines
--------------------------------

`{do}`, `{php}`, `{dump}`, `{debugbreak}`, `{trace}`, `{spaceless}`,
`{sandbox}`, `{syntax}`.

`{sandbox}` is a core tag in Latte 2 (`CoreMacros`) and lives in
`Latte\Sandbox\SandboxExtension` in Latte 3, but `Engine::addDefaultExtensions()`
registers that extension unconditionally, so from a template's point of view the
tag is always available in both lines. The sandbox *pass* only becomes active
when a policy is set; the tag itself is always parseable.


Syntax switching
----------------

`{syntax mode}` ... `{/syntax}` and `n:syntax="mode"` exist in both lines and
change the tag delimiters for the enclosed region. `off` and `double` are
accepted everywhere in the range. The name of the *default* mode is not stable —
see `latte-2.11.md` and `latte-3.0.md`.


Content types
-------------

`html`, `xml`, `javascript` (also written `js`), `css`, `ical` and `text` are
recognised in both lines. Matching is by substring in both
(`CoreMacros::macroContentType()` in 2.11, `ContentTypeNode::create()` in 3.x),
so `{contentType application/xml}` selects XML in both.
