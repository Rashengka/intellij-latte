Latte 3.1
=========

Releases 3.1.0 (2025-11-26) through 3.1.6 (2026-07-27), the current ceiling.

3.1 is not a rewrite. The compiler from 3.0 is intact and almost every 3.0
template compiles unchanged. What 3.1 changes is **how HTML attributes are
rendered**, and that is the part templates notice. It also starts a formal
deprecation track towards 4.0, and ships a migration-warning mode to surface it.


New syntax in 3.1
-----------------

| Construct | Since | Note |
|---|---|---|
| `n:elseif="$cond"` | 3.1.0 | Completes the pair started by `n:else` in 3.0.12. Paired with the preceding element's `n:if` by the `nElse` compiler pass. |
| `n:foo={...}` — unquoted attribute value | 3.1.0 | `<div n:if={$cond}>` instead of `<div n:if="$cond">`. |
| `?\|` — nullsafe filter | 3.1.0 | `{$var?\|upper}` skips the chain when the value is null. Chains combine: `{$a?\|upper\|truncate?\|trim}`. |
| Named filter arguments | 3.1.0 | `{$x\|mod:a: 1, b: 2}`. Verified in `tests/common/TagParser.parseModifier().phpt`. |
| Array values for `class` and `style` attributes | 3.1.0 | `<div class="{$classes}">` where `$classes` is an array. |
| `\|toggle` | 3.1.0 | Attribute-context directive, not a registered filter. See below. |
| `\|accept` | 3.1.0 | Attribute-context directive that silences a migration warning. See below. |

`\|toggle` and `\|accept` are recognised by name in
`Compiler\Nodes\Html\ExpressionAttributeNode::print()` and stripped before the
filter chain is built, exactly like `\|noescape` and `\|nocheck`. **They are not
in any filter registry**, so an inspection that reports "undefined filter" from
the registry alone will produce a false positive on both. `\|json` gets similar
special treatment in the same method, but `json` is also a real filter in Nette
projects, so it is less likely to be mis-reported.


Changed meaning in 3.1
----------------------

All of these are BC breaks in the 3.1.0 release. They change rendered output,
not what parses, so the plugin mostly needs them for documentation hovers rather
than for inspections — with the exception of the deprecations further down.

| Change | Effect |
|---|---|
| `null` in an HTML attribute | The attribute is omitted entirely; 3.0 rendered `foo=""`. |
| boolean or non-scalar in a common HTML attribute | The attribute is omitted and a warning is emitted. |
| `data-` attribute with a boolean value | Renders `"true"` / `"false"`; 3.0 rendered `"1"` / `""`. |
| `onclick="{$foo}"`, `style="{$foo}"` | No longer specially formatted. |
| `n:attr` | Validates attribute names, and now behaves consistently with regular HTML attributes. |
| Escaping in HTML comments | Now mandatory; `\|noescape` inside an HTML comment is a compile error (`Compiler/Escaper.php:226,231`). |
| `declare(strict_types=1)` in compiled templates | On by default. 3.0 had it off. Loosely typed expressions that used to coerce now raise `TypeError`. |
| `\|noescape` position | Processed before the modifier node is rendered, so its placement in the chain matters more strictly than in 3.0. |
| `{spaceless}` | Since 3.1.5 a streaming whitespace minifier rather than a buffered regex pass. Output differs in edge cases; syntax is unchanged. |


Deprecated but still accepted in 3.1
------------------------------------

Everything here compiles and runs, emits `E_USER_DEPRECATED`, and is on track to
be removed. Same rule as the 2.11 deprecation list: candidates for a deprecation
severity, never for an error.

| Construct | Since | Message source | Replacement |
|---|---|---|---|
| Unqualified global constant, e.g. `{PHP_VERSION}` | 3.1.0 | `Compiler/Nodes/Php/NameNode.php:41` | `{\PHP_VERSION}` |
| `??->` undefined-safe operator | 3.1.0 | `Compiler/TagParserData.php:590,596` | `?->` |
| `$this` in a template | 3.1.0 | `Essential/Passes.php:73` | pass an explicit parameter |
| Variables named `$__*` | 3.1.0 | `Essential/Passes.php:73` | rename |
| `{first}` / `{last}` / `{sep}` outside `{foreach}` | **3.1.6**, not 3.1.0 | `Essential/Nodes/FirstLastSepNode.php:89` | move inside a `{foreach}` |
| `Engine::addFilterLoader()` | 3.1.0 | `Engine.php:307` | `addFilter()` — engine API, not template syntax |

The unqualified-constant deprecation is the one most likely to appear in real
templates and the one the plugin is most likely to get wrong: `{FOO}` parses as
a constant fetch, and both `{FOO}` and `{\FOO}` are valid in every version in
the range.

The `{first}` / `{last}` / `{sep}` row is the one whose boundary is easiest to
get wrong, because the deprecation is not registered where the tags are. It is a
compiler pass, `'firstLastSep' => Nodes\FirstLastSepNode::outsideForeachPass(...)`
in `CoreExtension::getPasses()`, and that entry is absent from 3.1.0 through
3.1.5 and present only in 3.1.6. Verified by compiling `{first}x{/first}` at
every `v3.1.*` tag: the notice fires at 3.1.6 and nowhere earlier.

Note also that the unqualified-constant rule reaches **filter names**, because a
filter name is parsed as an identifier. `{$x|UPPER}` on Latte 3 raises
`Undefined constant "UPPER"` rather than an unknown-filter error, so an
all-uppercase filter spelling that works under 2.11 fails in an unrelated-looking
way under 3.x.


Feature flags
-------------

`Latte\Feature` moved three times inside the 3.1 line. The flags themselves are
engine configuration and cannot be read out of a template, but two of them change
semantics the plugin models.

| Version | Shape | Members |
|---|---|---|
| 3.1.0 - 3.1.1 | no `Feature` type; plain boolean setters on `Engine` | `setStrictTypes()`, `setStrictParsing()`, `setMigrationWarnings()` |
| 3.1.2 | `enum Latte\Feature` in `src/Latte/Feature.php`, plus `Engine::hasFeature()` | `StrictTypes`, `StrictParsing`, `MigrationWarnings` |
| 3.1.3 | same file, same shape | adds `ScopedLoopVariables`, `Dedent` |
| 3.1.4 - 3.1.6 | same `enum`, moved to `src/Latte/enums.php` | unchanged set of five |

(`Feature` also exists in 3.0.26, and only there in the 3.0 line, but it is a
different shape: a `final class` of two string constants, `StrictTypes` and
`StrictParsing`. The `enum` is 3.1-only.)

Two are worth the plugin's attention:

- **`ScopedLoopVariables`** (3.1.3+, **off by default**). When on, a variable
  introduced by `{foreach}` does not survive past `{/foreach}`. When off, the
  engine instead runs `ForeachNode::overwrittenVariablesPass()` and warns about
  overwritten variables — the Latte 2 behaviour. Because it is opt-in and
  invisible from the template, the plugin should keep treating loop variables as
  leaking. Doing otherwise would produce "undefined variable" false positives on
  every project that has not enabled the flag, which is nearly all of them.
- **`Dedent`** (3.1.3+, off by default). Removes structural indentation from
  paired-tag content. Whitespace only; relevant to the formatter, not to
  resolution.

`MigrationWarnings` is what emits the "output of this attribute changed" notices
that `\|accept` silences.


Additions inside the 3.1 line
-----------------------------

Same extraction method as for 3.0: keys of `CoreExtension::getTags()`,
`getFilters()` and `getFunctions()` at every `v3.1.*` tag, diffed pairwise.
Nothing was removed.

| Version | Added |
|---|---|
| 3.1.0 | tag `n:elseif`; `n:foo={...}` syntax; nullsafe filters; named filter arguments; `\|toggle`, `\|accept` |
| 3.1.3 | filters `\|column`, `\|commas`, `\|limit`; features `ScopedLoopVariables`, `Dedent`; `\|slice` gains iterator support |
| 3.1.5 | `{spaceless}` reimplemented as a streaming minifier (`Essential/WhitespaceMinifier.php`, new in this release) |
| 3.1.6 | filter `\|map`; the `firstLastSep` pass that deprecates `{first}` / `{last}` / `{sep}` outside `{foreach}` |

3.1.1, 3.1.2 and 3.1.4 add and remove nothing the plugin can see. (3.1.2 and
3.1.4 do move `Latte\Feature` around, but that is engine API, not template
syntax.)

No function was added anywhere in the 3.1 line; the function set is the 3.0.22
set (`clamp`, `divisibleBy`, `even`, `first`, `group`, `hasBlock`, `hasTemplate`,
`last`, `odd`, `slice`).


PHP requirement
---------------

`php: 8.2 - 8.5` for every 3.1.x release. A project pinned below PHP 8.2 cannot
be on 3.1.
