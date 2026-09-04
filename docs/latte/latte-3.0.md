Latte 3.0
=========

Releases 3.0.0 (2022-05-17) through 3.0.26 (2026-02-25). The 3.0 line is still
maintained alongside 3.1, so **do not order these releases by date** — 3.0.26 is
four months newer than 3.1.0.

Four patch numbers were never tagged: 3.0.11, 3.0.15, 3.0.17, 3.0.19. Do not
treat their absence as a detection failure.

3.0 is a full compiler rewrite. The lexer, the parser, the AST and the extension
API are all new. `src/Latte/Macros/` and `src/Latte/Compiler/Compiler.php` are
gone; tags are now nodes under `src/Latte/Essential/Nodes/` and are registered by
`Latte\Extension` subclasses. For the plugin this matters less than it sounds —
the template *language* changed far less than the implementation — but it is why
every custom tag written for Latte 2 stops working, and why the version boundary
is worth detecting at all.

The 2 to 3 language differences are collected in `migration-2-to-3.md`. This page
covers what 3.0 is on its own terms, and what moved inside the 3.0 line.


How tags are registered in 3.0
------------------------------

`Latte\Compiler\TemplateParser::addTags()` decides, from the registered name and
the parser callable alone, where a tag may be used:

| Registered name | `{tag}` form | `n:tag` form |
|---|---|---|
| starts with `n:` (e.g. `n:class`) | no | yes |
| plain name, parser is a generator (paired tag) | yes | yes |
| plain name, parser is not a generator (unpaired tag) | yes | no |

This is the rule the plugin's `LatteTagSettings.Type` enum approximates with
`ATTR_ONLY` / `PAIR` / `UNPAIRED`. It holds unchanged through 3.1.


Default extensions
------------------

`Engine::addDefaultExtensions()` registers exactly two:

- `Latte\Essential\CoreExtension` — all the tags, filters and functions listed in
  the reference tables as "core".
- `Latte\Sandbox\SandboxExtension` — the `{sandbox}` tag. Always registered; the
  sandbox pass only activates when a policy is set.

Two more extensions ship with the package but are **not** registered by default:

| Extension | Provides | Consequence |
|---|---|---|
| `Latte\Essential\TranslatorExtension` | `{_}`, `{translate}`, `\|translate` | In bare Latte 3 these do not exist. Nette projects normally get them because `nette/application` registers the extension. |
| `Latte\Essential\RawPhpExtension` | `{php}` with arbitrary PHP | Without it, `{php}` is a core alias of `{do}` and accepts only an expression. |

For the plugin, the practical reading is: `{_}`, `{translate}` and `{php}` with a
statement in it are *conditionally* valid in Latte 3. They cannot be reported as
errors, because the plugin cannot see which extensions the project registers.


Tags in 3.0
-----------

The 3.0.0 core tag set, straight out of `CoreExtension::getTags()`:

`=`, `block`, `breakIf`, `capture`, `contentType`, `continueIf`, `debugbreak`,
`default`, `define`, `do`, `dump`, `embed`, `extends`, `first`, `for`, `foreach`,
`if`, `ifchanged`, `ifset`, `import`, `include`, `iterateWhile`, `l`, `last`,
`layout`, `n:attr`, `n:class`, `n:ifcontent`, `n:tag`, `parameters`, `php`, `r`,
`rollback`, `sep`, `skipIf`, `spaceless`, `switch`, `syntax`, `templatePrint`,
`templateType`, `trace`, `try`, `var`, `varPrint`, `varType`, `while`

plus `sandbox` from `SandboxExtension`.

Two entries deserve a note:

- **`syntax` is a real tag now.** In Latte 2 it was special-cased in the parser.
  In 3.x it is `CoreExtension::parseSyntax()`, a generator, so `n:syntax` also
  works.
- **`{else}`, `{elseif}`, `{elseifset}`, `{case}` and the switch `{default}` are
  not registered.** They are inner tags: `IfNode::create()` yields
  `['else', 'elseif', 'elseifset']`, `SwitchNode::create()` yields
  `['case', 'default']`, `TryNode::create()` yields `['else']`. Outside their
  parent they are "Unknown tag". `{default}` is the awkward one — it is *also* a
  top-level tag (the `{default $a = 1}` variable tag), so its meaning depends on
  whether it is directly inside a `{switch}`.


Loop-jump tags in 3.x
---------------------

`JumpNode::create()` validates the enclosing construct at compile time. This is
stricter than Latte 2 and is worth knowing before adding an inspection:

| Tag | Legal inside | Generated code |
|---|---|---|
| `{breakIf}` | `{for}`, `{foreach}`, `{while}` | `break;` |
| `{continueIf}` | `{for}`, `{foreach}`, `{while}` | `continue;` |
| `{skipIf}` | `{foreach}` only | `$iterator->skipRound(); continue;` |
| `{exitIf}` | outside any loop, or directly in `{block}` / `{define}` | `return;` |

Intervening `{if}` and `n:ifcontent` wrappers are skipped when looking for the
enclosing construct, so `{foreach}{if $x}{skipIf $y}{/if}{/foreach}` is valid.


`{syntax}` modes changed twice inside 3.0
-----------------------------------------

This is the single most confusing boundary in the range and the plugin gets it
wrong today. All four states verified in
`Latte\Compiler\TemplateLexer::setSyntax()`.

| Versions | Accepted modes | `{syntax latte}` | `{syntax single}` |
|---|---|---|---|
| 2.11.x | `latte`, `double`, `off` | valid | error |
| 3.0.0 - 3.0.1 | `latte`, `double`, `off` | valid | error |
| 3.0.2 - 3.0.23 | `double`, `off` | **error** | **error** |
| 3.0.24 - 3.1.6 | `single`, `double`, `off` | error | valid |

In 3.0.2 the `$syntaxes` lookup array was replaced by a `match` expression whose
`null` arm handles the engine default but which has no name for it; the
`default` arm throws `InvalidArgumentException`. From 3.0.2 to 3.0.23 there was
therefore no way to write "switch back to normal braces" at all. 3.0.24 added
`'single'` to the `null` arm.

Practical consequence for the plugin: the set of valid `{syntax}` arguments is
`{latte, double, off}` below 3.0.2, `{double, off}` from 3.0.2 to 3.0.23, and
`{single, double, off}` from 3.0.24 up. Reporting anything outside the union
`{latte, single, double, off}` as an error is safe; reporting anything inside it
as an error is not, unless the version is known.


Additions inside the 3.0 line
-----------------------------

Produced by extracting the keys of `CoreExtension::getTags()`,
`getFilters()` and `getFunctions()` at every `v3.0.*` tag and diffing
consecutive results. Nothing was removed anywhere in the 3.0 line.

| Version | Added |
|---|---|
| 3.0.2 | `{syntax latte}` stops working (see above) |
| 3.0.5 | tag `{exitIf}`; filter `\|escape` |
| 3.0.7 | `{foreach $a as $i\|noiterator}` stops working (see below) |
| 3.0.9 | extra content in a closing tag, e.g. `</div foo>`, is rejected |
| 3.0.10 | function `hasBlock()` |
| 3.0.12 | attribute `n:else` |
| 3.0.16 | filter `\|group`; function `group()` |
| 3.0.18 | filter `\|localDate` |
| 3.0.20 | filter `\|filter` |
| 3.0.22 | filter `\|firstLower`; function `hasTemplate()` |
| 3.0.24 | `{syntax single}` starts working |

3.0.3, 3.0.4, 3.0.6, 3.0.7, 3.0.8, 3.0.13, 3.0.14, 3.0.21, 3.0.23, 3.0.25 and
3.0.26 add and remove nothing the plugin can see.

`n:elseif` is **not** in this list. It arrived in 3.1.0 even though its sibling
`n:else` arrived in 3.0.12. A template using `n:else` without `n:elseif` is
therefore consistent with 3.0.12 or newer; one using `n:elseif` requires 3.1.

"Nothing was removed" above means nothing left `getTags()`, `getFilters()` or
`getFunctions()`. One piece of *syntax* was nevertheless lost inside the line.


`{foreach |noiterator}` stopped parsing in 3.0.7
------------------------------------------------

3.0.7 gave `{foreach}` a dedicated argument grammar
(`TagParser::parseForeach()`, added by commit "ForeachNode, TagParser: added
native parser for foreach-variables"). That grammar ends after the loop
variable and rejects a following `|`, so the `parseModifier()` call in
`ForeachNode::create()` is never reached:

| Version | `{foreach $a as $i\|noiterator}` |
|---|---|
| 2.11.x | valid |
| 3.0.0 - 3.0.6 | valid |
| 3.0.7 - 3.1.6 | `Unexpected '\|'` at the `\|` |

The same applies to `|nocheck` on `{foreach}` and to the `n:foreach` spelling.
Verified by compiling the snippet against every `v3.*` tag. The dispatch code in
`Essential/Nodes/ForeachNode.php:52-58` still names `noiterator`, `noIterator`,
`nocheck` and `noCheck` and is dead in every release from 3.0.7 on — read the
registration, not that `match`.

For the plugin this is a genuine boundary rather than a curiosity: 2.11
templates that use `|noiterator` are common, and the construct is a hard
compile error on any current Latte 3.


Feature flags in 3.0
--------------------

`Latte\Feature` appears in the 3.0 line in exactly one release, **3.0.26**, as a
`final class` of string constants with two members. `src/Latte/Feature.php` does
not exist at 3.0.25 or earlier. The switches themselves are older and reachable
as plain setters: `Engine::setStrictTypes()` since 3.0.0, `setStrictParsing()`
since **3.0.8**. The two members:

- `Feature::StrictTypes` — add `declare(strict_types=1)` to compiled templates.
  Off by default in 3.0.
- `Feature::StrictParsing` — enforce strict HTML parsing.

Neither changes what syntax is accepted in a way the plugin can detect from the
template alone, since both are engine configuration. 3.1 turns this into an
`enum` and adds three more members; see `latte-3.1.md`.


PHP requirement
---------------

| Version | `composer.json` requirement |
|---|---|
| 3.0.0 | `>=8.0 <8.2` |
| 3.0.26 | `8.0 - 8.5` |

A project on PHP 8.0 or 8.1 can run 3.0 but not 3.1, which requires 8.2. This is
a usable secondary signal when `composer.lock` is missing.
