Latte 2 to Latte 3
==================

The largest discontinuity in the supported range. The plugin has to tolerate both
sides at once, because a project's templates and its installed engine can be on
different sides during a migration, and because a developer may be writing for a
version that is not installed yet.

Two things worth stating before the list, because they shape how much of this
matters:

1. **The engine was rewritten; the language was not.** The lexer, parser, AST and
   extension API of Latte 3 share no code with Latte 2. But a Latte 2.11 template
   that emits no deprecation warnings compiles on Latte 3 with only a handful of
   exceptions. That is by design — 2.11 exists to deprecate exactly what 3.0
   rejects.
2. **The reliable direction is "2.11 warns, 3.0 errors".** Nearly every entry in
   the 2.11 deprecation list (`latte-2.11.md`) reappears here as a hard error.
   The practical consequence for the plugin: a construct that is deprecated in
   2.11 should never be reported as an error under a 2.11 project, and should be
   reported as an error under a 3.x project only when the plugin is confident
   about the version.


Removed outright
----------------

Nothing on this list has a Latte 3 equivalent under the same spelling.

| Latte 2.11 | Status in Latte 3 | Replacement |
|---|---|---|
| `{includeblock 'file'}` | removed | `{include 'file' with blocks}` or `{import 'file'}` |
| `$iterations` | removed | `$iterator->getCounter()` |
| `{?expr}` | already removed in 2.11.0 | `{do expr}` |
| `{syntax latte}` | invalid from 3.0.2 | `{syntax single}` from 3.0.24; nothing between 3.0.2 and 3.0.23 |
| `n:inner-snippet` | removed | `n:snippet` |
| `{_}` ... `{/}` as a paired tag | removed | `{translate}` ... `{/translate}` |
| Auto-empty paired tags, e.g. bare `{label}` from `nette/forms` | removed | `{label /}` |
| `content type xhtml` as a distinct type | folded into `html` | `{contentType html}` |
| `{foreach $a as $i\|noiterator}` | removed from 3.0.7, not from 3.0.0 | drop the modifier |

The auto-empty row is about `Macro::AUTO_EMPTY`, which no core 2.11 macro sets —
`{label}` from `nette/forms` is the one that occurs in practice. An unclosed
`{block foo}` is *not* an instance of it and compiles unchanged on both lines;
see `latte-2.11.md`.


Moved out of the core engine
----------------------------

These tags still exist, but a bare Latte 3 install does not have them. This is
the trap for an inspection that reports unknown tags: whether the tag exists
depends on which extensions the *project* registers, which the plugin cannot see
from the template.

| Tag | Latte 2.11 | Latte 3 |
|---|---|---|
| `{sandbox}` | `CoreMacros` | `Latte\Sandbox\SandboxExtension`, registered by default — always available |
| `{_}`, `{translate}` | `CoreMacros` | `Latte\Essential\TranslatorExtension`, **not** registered by default; `nette/application` registers it |
| `\|translate` | never in the core registry — see below | `Latte\Essential\TranslatorExtension`, **not** registered by default |
| `{php}` with arbitrary PHP | `CoreMacros` | `Latte\Essential\RawPhpExtension`, **not** registered by default. Core `{php}` is an alias of `{do}` and accepts one expression only |
| `{snippet}`, `{snippetArea}` | `BlockMacros` | `nette/application`'s Latte bridge |
| `{cache}` | `nette/caching` bridge | `nette/caching` bridge (unchanged) |

`|translate` is the odd one: it is not a Latte 2 core filter either.
`Runtime\Defaults::getFilters()` at v2.11.7 has no `translate` entry — the
`{_}` / `{translate}` macros emit a runtime `filterContent("translate", …)` call
against whatever the project registered. So on **both** lines the filter exists
only if something registers it, and on both lines an unregistered `|translate`
compiles fine and fails at render time. Nothing about it is version-dependent.

The safe plugin behaviour is to keep all of these known, in both modes, and never
report them as unknown.


Renamed in the registry, unchanged in templates
-----------------------------------------------

Four attribute-only tags were registered under a bare name in Latte 2 and under
an `n:`-prefixed name in Latte 3. Template syntax is identical on both sides;
only the plugin's internal table needs the distinction.

| Latte 2 registration | Latte 3 registration | Template spelling in both |
|---|---|---|
| `class` (attr callback only) | `n:class` | `n:class` |
| `attr` (attr callback only) | `n:attr` | `n:attr` |
| `ifcontent` (attr callback only) | `n:ifcontent` | `n:ifcontent` |
| `tag` (rejects non-attribute use) | `n:tag` | `n:tag` |


Promoted from tag to inner tag
------------------------------

In Latte 2 these were independently registered macros; in Latte 3 they are inner
tags produced by their parent's parser generator, and outside that parent they
are "Unknown tag".

| Tag | Valid only inside (Latte 3) |
|---|---|
| `{else}` | `{if}`, `{ifset}`, `{try}` |
| `{elseif}` | `{if}` |
| `{elseifset}` | `{ifset}` |
| `{case}` | `{switch}` |
| `{default}` (the switch clause) | `{switch}` — note that `{default $a = 1}` remains a top-level variable tag |

`{if}` in Latte 3 also rejects `{else if ...}` explicitly with "Arguments are not
allowed in {else}, did you mean {elseif}?" (`IfNode::create()`).


Filter names became case-sensitive
----------------------------------

The single most consequential change for the plugin's filter inspection.

- **Latte 2.11**: `FilterExecutor::add()` calls `strtolower($name)` before storing
  and `__get()` looks up the lowercased form. `|escapeUrl`, `|escapeurl` and
  `|ESCAPEURL` are the same filter. The two mis-spelled forms compile and run,
  each emitting `Case mismatch on filter name |…, correct name is |escapeUrl.` at
  `E_USER_WARNING` (`Compiler/PhpWriter.php:870`).
- **Latte 3.x**: `FilterExecutor::add()` stores the name verbatim. Lookup is
  exact. `|escapeurl` compiles and then fails at render time with
  `LogicException: Filter 'escapeurl' is not defined or not allowed here.`
  An all-uppercase spelling fails differently again: `|ESCAPEURL` is lexed as a
  constant fetch and raises `Undefined constant "ESCAPEURL"`.

The same case rule applies to the compiler directives, which are *not* in the
registry: 2.11 matches `noescape` / `nocheck` / `noiterator` case-insensitively
(`Helpers.php:65`, a `#Di` pattern), while Latte 3 accepts only the exact
lowercase spelling plus the two hardcoded aliases `noCheck` and `noIterator`.

Latte 2.11.1 pre-emptively registered all-lowercase aliases for five filters
(`breaklines`, `datastream`, `replaceRE`, `striphtml`, `striptags`), and Latte 3
kept those aliases, so those five specific spellings work on both sides. Every
other filter must be spelled exactly as registered under Latte 3.

Practical rule for a version-aware inspection:

- 2.11 mode: match filter names case-insensitively.
- 3.x mode: match exactly.


Filter argument separators
--------------------------

| Form | 2.11 | 3.x |
|---|---|---|
| `\|filter:arg` (first separator) | valid | valid |
| `\|filter, arg` (first separator) | valid | valid |
| `\|filter:a, b` (later separators) | valid | valid |
| `\|filter:a:b` (later separators) | deprecated, `PhpWriter.php:829` | **rejected** |
| `\|filter:name: value` (named argument) | not supported | valid from 3.1 |

The first separator after the filter name may be `:` or `,` in both lines. Only
separators *between* arguments changed.


Pseudo-strings must be quoted or interpolated
---------------------------------------------

Latte 2 accepted a bare `$var` glued into an unquoted word. Latte 3's
`TagLexer::tokenizeUnquotedString()` accepts `{$var}` interpolation inside an
unquoted string but not a bare `$var`.

| Latte 2.11 | Latte 3 |
|---|---|
| `{block foo-$var}` | `{block "foo-$var"}` or `{block foo-{$var}}` |
| `n:block="foo-$var"` | `n:block="foo-{$var}"` |

Latte 2.11 already deprecates the bare form (`PhpWriter.php:203,205`).


`{do}` and `{php}` take one expression
--------------------------------------

Latte 2 accepted statement sequences separated by `;` in `{do}` and `{php}`, with
a deprecation. Latte 3 core accepts a single expression in both, because `{php}`
is registered as an alias of `{do}`. Arbitrary PHP requires
`Latte\Essential\RawPhpExtension`, which then re-registers `{php}` and wins by
last-registration.


HTML strictness
---------------

| Rule | 2.11 | 3.x |
|---|---|---|
| Unterminated `</script>` / `</style>` | deprecation | error |
| Extra content in a closing tag, e.g. `</div foo>` | tolerated | error from 3.0.9 |
| `n:inner-` / `n:tag-` on a void element | `E_USER_WARNING` | error |
| `n:ifcontent` on a void or empty element | deprecation | error |
| `\|noescape` inside an HTML comment | allowed | error from 3.1.0 |


`{label}` pairing
-----------------

From `nette/forms`, not the engine, but it shows up in the same templates:
`{label}` is always paired in Latte 3, so an unpaired use must be written
`{label /}`.


What did *not* change, despite appearances
------------------------------------------

Worth listing because these are easy to over-correct.

- **`{include}` block-versus-file resolution.** Both lines use the same anchored
  pattern `~[\w-]+$~DA` on the name to decide: a name that is a plain word is a
  block, anything else (a name containing a dot, a slash, an expression) is a
  file. Latte 2 additionally emits an `E_USER_NOTICE` recommending
  `{include file ...}` for clarity, and the migration guide repeats that advice —
  but the resolution rule itself is unchanged. Where the guide reads as though
  the behaviour changed, the code says it did not.
- **`{ifset #block}` and `{ifset block foo}`.** Both spellings work in both lines.
- **`{define}` parameters and the `local` modifier.** Both lines.
- **`{if}` with no arguments** (the capturing form, condition on the closing tag).
  Both lines.
- **The `n:`, `n:inner-`, `n:tag-` prefix system.** Both lines, same meaning.
- **`{contentType}` head restriction and its `<script>` exception.** Both lines.
- **`|noescape` and `|nocheck`** as compiler directives rather than registered
  filters. Both lines. (`|noiterator` is *not* on this list — it is a directive
  on both lines, but from 3.0.7 the `{foreach}` grammar no longer lets you write
  it. See above.)


Detecting which side a template is on
-------------------------------------

No template declares its Latte version, so none of this is decidable from the
file. A few constructs are nevertheless strong one-sided evidence, useful for a
"this template targets a different version than the project" hint but never for
an error:

| Construct | Implies |
|---|---|
| `{includeblock ...}` | Latte 2 only |
| `n:switch` | Latte 2 only — `SwitchNode::create()` rejects it in every 3.x |
| `{foreach …\|noiterator}` | Latte 2, or 3.0.0-3.0.6 |
| `{syntax latte}` | Latte 2, or 3.0.0-3.0.1 |
| `{syntax single}` | Latte 3.0.24 or newer |
| `n:elseif` | Latte 3.1 or newer |
| `n:else` | Latte 3.0.12 or newer |
| `{exitIf}` | Latte 3.0.5 or newer |
| `?\|` nullsafe filter | Latte 3.1 or newer |
| `\|localDate` | Latte 3.0.18 or newer |
| `\|map` | Latte 3.1.6 or newer |
