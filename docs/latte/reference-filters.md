Filter reference
================

Version-stamped, one item per row. Sources: `src/Latte/Runtime/Defaults.php`
(`getFilters()`) at `v2.11.0` through `v2.11.7`, and
`src/Latte/Essential/CoreExtension.php` (`getFilters()`) at every `v3.*` tag.

Two facts govern every row:

- **Latte 2.11 matches filter names case-insensitively**; Latte 3 matches them
  exactly. A name that differs only in case from a row below resolves in 2.11 and
  is undefined in 3.x.
- The 3.0.0 filter set is **identical** to the 2.11.7 set. Every difference
  between the lines is an addition made inside 3.0 or 3.1, never a removal.

Columns: `yes` means present for the whole minor line; a version means present
from that patch onward; `no` means absent.


Registered filters
------------------

| Filter | 2.11 | 3.0 | 3.1 | Notes |
|---|---|---|---|---|
| `batch` | yes | yes | yes | |
| `breakLines` | yes | yes | yes | |
| `breaklines` | 2.11.1 | yes | yes | lowercase alias added in 2.11.1 for forward compatibility |
| `bytes` | yes | yes | yes | |
| `capitalize` | yes | yes | yes | requires ext-mbstring |
| `ceil` | yes | yes | yes | |
| `checkUrl` | yes | yes | yes | 2.11 maps to `Filters::safeUrl`, 3.x to `Filters::checkUrl` |
| `clamp` | yes | yes | yes | also a function |
| `column` | no | no | 3.1.3 | |
| `commas` | no | no | 3.1.3 | |
| `dataStream` | yes | yes | yes | |
| `datastream` | 2.11.1 | yes | yes | lowercase alias |
| `date` | yes | yes | yes | 2.11 accepts `strftime`-style `%` formats with a deprecation; 3.x does not |
| `escape` | no | 3.0.5 | yes | no-op marker (`Runtime\Helpers::nop`) |
| `escapeCss` | yes | yes | yes | |
| `escapeHtml` | yes | yes | yes | |
| `escapeHtmlComment` | yes | yes | yes | |
| `escapeICal` | yes | yes | yes | |
| `escapeJs` | yes | yes | yes | |
| `escapeUrl` | yes | yes | yes | `rawurlencode` in both lines |
| `escapeXml` | yes | yes | yes | |
| `explode` | yes | yes | yes | |
| `filter` | no | 3.0.20 | yes | |
| `first` | yes | yes | yes | also a function |
| `firstLower` | no | 3.0.22 | yes | requires ext-mbstring |
| `firstUpper` | yes | yes | yes | requires ext-mbstring |
| `floor` | yes | yes | yes | |
| `group` | no | 3.0.16 | yes | also a function |
| `implode` | yes | yes | yes | |
| `indent` | yes | yes | yes | |
| `join` | yes | yes | yes | alias of `implode` |
| `last` | yes | yes | yes | also a function |
| `length` | yes | yes | yes | |
| `limit` | no | no | 3.1.3 | `slice` with `preserveKeys: true` |
| `localDate` | no | 3.0.18 | yes | locale-aware |
| `lower` | yes | yes | yes | requires ext-mbstring |
| `map` | no | no | 3.1.6 | newest filter in the range |
| `number` | yes | yes | yes | 2.11 is `number_format` directly; 3.x is `Filters::number` |
| `padLeft` | yes | yes | yes | accepts `int\|float` from 3.1.3 |
| `padRight` | yes | yes | yes | accepts `int\|float` from 3.1.3 |
| `query` | yes | yes | yes | |
| `random` | yes | yes | yes | |
| `repeat` | yes | yes | yes | |
| `replace` | yes | yes | yes | |
| `replaceRE` | 2.11.1 | yes | yes | alias of `replaceRe` |
| `replaceRe` | yes | yes | yes | |
| `reverse` | yes | yes | yes | |
| `round` | yes | yes | yes | |
| `slice` | yes | yes | yes | also a function; gains iterator support in 3.1.3 |
| `sort` | yes | yes | yes | |
| `spaceless` | yes | yes | yes | |
| `split` | yes | yes | yes | alias of `explode` |
| `strip` | yes | yes | yes | alias of `spaceless`; marked obsolete in the 3.x source |
| `stripHtml` | yes | yes | yes | |
| `striphtml` | 2.11.1 | yes | yes | lowercase alias |
| `stripTags` | yes | yes | yes | |
| `striptags` | 2.11.1 | yes | yes | lowercase alias |
| `substr` | yes | yes | yes | |
| `trim` | yes | yes | yes | |
| `truncate` | yes | yes | yes | |
| `upper` | yes | yes | yes | requires ext-mbstring |
| `webalize` | yes | yes | yes | requires nette/utils |

That table is the **default** registry and nothing else: `Runtime\Defaults` under
2.11, `Essential\CoreExtension` under 3.x. Three more filters can be present and
none of them is ever in it. Read from `nette/latte` v3.1.6 and
`nette/application` v3.3.0:

| Filter | Provider | Note |
|---|---|---|
| `translate` | `Latte\Essential\TranslatorExtension` under Latte 3; nothing under Latte 2 | Ships inside `nette/latte`, but `Engine::addDefaultExtensions()` registers only `CoreExtension` and `SandboxExtension`, so it is off unless the project turns it on — `nette/application` does. Under Latte 2 there is no registration at all: `{_}` emits a runtime `filterContent("translate", …)` (`Macros/CoreMacros.php:351`) against whatever the project registered. On both lines an unregistered `\|translate` compiles and fails at render time |
| `modifyDate` | nette/application (`UIExtension::getFilters()`) | |
| `absoluteUrl` | nette/application (`UIExtension::getFilters()`) | only present when a presenter is attached |


Case sensitivity
----------------

`FilterExecutor::add()` lowercases the name in 2.11 and stores it verbatim in
3.x, so every row above is case-insensitive on 2.11 and exact on 3.x. Two
consequences that look like different bugs but are the same one:

- A case-mismatched name compiles on both lines. Under 2.11 it runs, emitting
  `Case mismatch on filter name |…` at `E_USER_WARNING`
  (`Compiler/PhpWriter.php:870`). Under 3.x it fails at render time with
  `LogicException: Filter '…' is not defined or not allowed here.`
- An **all-uppercase** name is not even a filter lookup under 3.x. Filter names
  are parsed as identifiers, so `{$x|UPPER}` raises `Undefined constant "UPPER"`
  (and, since 3.1.0, the unqualified-constant deprecation before it).


Compiler directives that look like filters
------------------------------------------

None of these is in any filter registry. They are recognised by name during
compilation and removed from the chain. An inspection driven by the registry
alone reports every one of them as an undefined filter.

Latte 2 matches all of them case-insensitively — `Helpers::removeFilter()`
(`Helpers.php:65`) builds a `#Di` pattern — so the 2.11 column below covers every
casing of the name. Latte 3 matches literally and hardcodes exactly two
camelCase aliases.

| Directive | 2.11 | 3.0 | 3.1 | Legal where | Recognised in |
|---|---|---|---|---|---|
| `noescape` | yes (any case) | yes | yes | print tags, `{block}`, `{include}`, `{_}` | `Compiler.php:888` (2.11); `PrintNode.php:35` and friends (3.x) |
| `nocheck` | yes (any case) | yes | yes | URL attributes | `Compiler.php:879` (2.11); `Passes.php:127` (3.x) |
| `noCheck` | yes, via 2.11 case folding | yes | yes | same as `nocheck` | `Passes.php:127`; the only camelCase spelling of it Latte 3 accepts |
| `noiterator` | yes (any case) | 3.0.0-3.0.6 only | **no** | `{foreach}` | `CoreMacros.php:514` (2.11); `ForeachNode.php:55` (3.x, unreachable) |
| `noIterator` | yes, via 2.11 case folding | 3.0.0-3.0.6 only | **no** | `{foreach}` | as above |
| `toggle` | no | no | 3.1.0 | HTML attribute value | `ExpressionAttributeNode.php:41` |
| `accept` | no | no | 3.1.0 | HTML attribute value | `ExpressionAttributeNode.php:55`; silences a migration warning |
| `json` | no | no | 3.1.0 | HTML attribute value | `ExpressionAttributeNode.php:42`; also a real filter in Nette projects |

The `noiterator` rows are the surprise. From Latte 3.0.7 the `{foreach}`
argument grammar rejects the `|` before any modifier is parsed, so
`{foreach $a as $i|noiterator}` — and `{foreach $a as $i|nocheck}`, and the
`n:foreach` spelling of either — is `Unexpected '|'`. `ForeachNode::create()`
still contains the `match` that would handle them. See `latte-3.0.md`.
