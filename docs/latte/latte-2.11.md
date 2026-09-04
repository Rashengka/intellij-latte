Latte 2.11
==========

The floor of the supported range and the last minor line of Latte 2. Releases
2.11.0 (2022-03-18) through 2.11.7 (2023-10-18); 2.11.7 is final, there will be
no 2.11.8.

2.11 is a transitional release. Almost everything it does is preparation for
Latte 3: it deprecates the constructs the rewritten parser would not accept, and
it adds the spellings 3.0 would require. Reading it as "3.0 with warnings instead
of errors" is close to accurate, and is the right mental model for the plugin —
most of what 2.11 merely warns about, 3.0 rejects.


What 2.11.0 changed relative to 2.10
------------------------------------

These are the changes a template author can see. All were verified in
`git log v2.10.10..v2.11.0`.

### Removed outright

| Construct | Note |
|---|---|
| `{?expr}` | The "silent expression" tag. Its name pattern was dropped from `Parser::parseMacroTag()`. Deprecated back in 2.4; in 2.11 `{?...}` is a syntax error. Use `{do expr}`. |
| `$_l`, `$_g` | Old template accumulators, removed from `Runtime\Template`. Not template syntax as such, but templates that touched them break. |

### Newly rejected

| Construct | Behaviour in 2.11 |
|---|---|
| `{syntax}` with no argument | Compile error. The mode is now mandatory. |
| Template containing control characters | `CompileException` from the parser. |
| Template that is not valid UTF-8 | `CompileException` (was `InvalidArgumentException`). |
| Deprecated optional-chaining spellings | Removed; only the then-current form parses. |

### Behaviour changed

- `text/plain` is now allowed as a `<script>` type.
- `Filters::escapeJS()` replaces invalid UTF-8 with U+FFFD instead of throwing.

### Added

- `{translate}` ... `{/translate}` as the paired translation tag. **Added in
  2.11.1, not 2.11.0** — `CoreMacros::install()` in v2.11.0 registers only `_`.
- Explicit all-lowercase filter aliases so that templates written for the
  case-sensitive Latte 3 keep working: `breaklines`, `datastream`, `replaceRE`,
  `striphtml`, `striptags` were added to `Runtime\Defaults::getFilters()` in
  **2.11.1**.


Deprecated but still accepted in 2.11
-------------------------------------

This is the list the plugin cares about most: everything here compiles and runs
in 2.11, emits `E_USER_DEPRECATED`, and is gone in 3.0. Extracted from
`git grep -n E_USER_DEPRECATED v2.11.7 -- src`.

| Construct | Message source | Replacement |
|---|---|---|
| Colon as argument separator between filter arguments, e.g. `\|replace:a:b` | `Compiler/PhpWriter.php:829,835` | `\|replace: a, b` |
| Auto-empty paired tags, e.g. `{label}` used unpaired | `Compiler/Compiler.php:446` | `{label /}` |
| Unterminated HTML element, e.g. a missing `</script>` | `Compiler/Compiler.php:947` | close the element |
| Bare variable inside a quoted string, e.g. `"text $var"` in a tag argument | `Compiler/PhpWriter.php:203,205` | `"text {$var}"`, or wrap the whole expression in double quotes |
| `$iterations` | `Compiler/PhpWriter.php:270` | `$iterator->getCounter()` |
| `{includeblock 'file'}` | `Macros/BlockMacros.php:201` | `{include 'file' with blocks}` or `{import}` |
| `n:inner-snippet` | `Macros/BlockMacros.php:436` | `n:snippet` |
| `n:ifcontent` on an empty element | `Macros/CoreMacros.php:255` | remove it |
| `{_}` ... `{/}` as a paired translation tag | `Macros/CoreMacros.php:371` | `{translate}` ... `{/translate}` |
| Bare word instead of a variable inside `{var}` / `{default}` | `Macros/CoreMacros.php:752` | prefix with `$` |
| `=>` instead of `=` inside `{var}` / `{default}` | `Macros/CoreMacros.php:772` | `=` |
| `;` instead of `,` inside `{var}` / `{default}` | `Macros/CoreMacros.php:780` | `,` |
| `;` inside `{do}` / `{php}` | `Macros/CoreMacros.php:824` | one expression per tag, or `{php}` with the raw-PHP extension in 3 |
| `\|noescape` not at the end of the filter chain | `Helpers.php:63` | move it last |
| `\|date` with a `strftime`-style `%` format | `Runtime/Filters.php:467` | a `date()` format such as `Y-m-d` |
| `n:tag-` / `n:inner-` on a void element | `Compiler/Compiler.php:760,795` (emitted as `E_USER_WARNING`, not deprecated) | remove |
| Filter name whose case differs from the registration, e.g. `\|escapeurl` | `Compiler/PhpWriter.php:870`, and `:851` for `\|checkurl` specifically (`E_USER_WARNING`) | spell it `\|escapeUrl` |
| Function name whose case differs from the registration, e.g. `{FIRST($a)}` | `Compiler/PhpWriter.php:340` (`E_USER_WARNING`) | spell it as registered |

Note that the last three rows are *not* `E_USER_DEPRECATED`, so
`git grep -n E_USER_DEPRECATED` does not find them. The case-mismatch warnings
matter more than their severity suggests: they are 2.11's advance notice of the
Latte 3 case sensitivity, and the construct they warn about is a hard failure
in 3.x.

The plugin's rule "what it cannot prove, it does not report" applies here: a
2.11 project may legitimately contain every one of these. They are candidates
for a *deprecation* severity, never for an error.

One deprecation on the list has no core trigger. `Macro::AUTO_EMPTY` is set by
no macro in `CoreMacros::install()` or `BlockMacros::install()` at v2.11.7
(`git grep -n AUTO_EMPTY v2.11.7 -- src` returns only the constant and the one
site that reads it), so the "Auto-empty behaviour is deprecated" message can
only come from a third-party macro. The one that occurs in practice is
`{label}` from `nette/forms`, registered with `self::AUTO_EMPTY` in
`Bridges/FormsLatte/FormMacros.php:37`. **`{block}` is not an example of this**:
it is registered with `AUTO_CLOSE`, and a `{block foo}` left unclosed at the end
of a template compiles without a warning under 2.11.7 *and* under 3.1.6
(verified by compiling `{block foo}text` against both).


Tags that exist only in the 2.x line
------------------------------------

Registered by `CoreMacros::install()` or `BlockMacros::install()` in v2.11.7 and
absent from `Essential\CoreExtension` in every 3.x release:

`_`, `attr`, `case`, `class`, `else`, `elseif`, `elseifset`, `ifcontent`,
`includeblock`, `sandbox`, `snippet`, `snippetArea`, `tag`, `translate`

Read that list carefully — it is a raw registration diff and four entries need a
caveat:

- `sandbox` moved to `Latte\Sandbox\SandboxExtension`, which the Latte 3 engine
  registers by default. The tag is available in both lines.
- `_` and `translate` moved to `Latte\Essential\TranslatorExtension`, which the
  Latte 3 engine does **not** register by default. In a plain Latte 3 project
  they do not exist; in a Nette project they normally do, because
  `nette/application` registers the extension.
- `snippet` and `snippetArea` moved to `nette/application`'s Latte bridge. They
  exist in a Latte-3-plus-Nette project and do not exist in a bare Latte 3
  project.
- `class`, `attr`, `ifcontent` and `tag` were attribute-only in 2.11 already
  (`{class}` is a compile error, only `n:class` works). In Latte 3 they are
  registered under the names `n:class`, `n:attr`, `n:ifcontent`, `n:tag`. The
  spelling in the registry changed; the template syntax did not.
- `case`, `else`, `elseif` and `elseifset` still work in Latte 3, but they are no
  longer independently registered tags — they are inner tags yielded by
  `{if}`/`{ifset}`/`{switch}`/`{try}`. See `migration-2-to-3.md`.

That leaves exactly one tag with no Latte 3 equivalent at all: `{includeblock}`.


`{syntax}` modes in 2.11
------------------------

`Latte\Parser::$syntaxes` in v2.11.7 defines exactly three:

| Mode | Delimiters |
|---|---|
| `latte` | `{...}` |
| `double` | `{{...}}` |
| `off` | tags disabled until `{/syntax}` |

`latte` is the default and is also the name you write to switch back:
`{syntax latte}`. **This name does not exist in Latte 3** — see
`latte-3.0.md`. It is the clearest single-token version marker in the whole
range.


Filters and functions in 2.11
-----------------------------

Filter names are **case-insensitive**. `FilterExecutor::add()` lowercases the
name before storing it and `__get()` looks up the lowercased form, so
`|escapeUrl`, `|escapeurl` and `|ESCAPEURL` are the same filter — each of the
last two additionally emitting `Case mismatch on filter name |…, correct name
is |escapeUrl.` at `E_USER_WARNING`. Latte 3 removed this; see
`migration-2-to-3.md`.

`|translate` is **not** in the 2.11 registry. `Runtime\Defaults::getFilters()`
does not contain it; `{_}` and `{translate}` compile to a runtime
`filterContent("translate", …)` call (`Macros/CoreMacros.php:351`) against a
filter the *project* registers, which in a Nette project `nette/application`
does. A template using `|translate` therefore compiles on a bare Latte 2.11 and
fails at render time. Treat it exactly like the Latte 3 case: known, never
reported.

The complete registries are in `Runtime\Defaults`. Full version-stamped tables
are in `reference-filters.md` and `reference-functions.md`. The filter set is
stable across 2.11.1 to 2.11.7 — the last change was the lowercase aliases in
2.11.1 — and the function set is stable across the whole of 2.11
(`clamp`, `divisibleBy`, `even`, `first`, `last`, `odd`, `slice`).


Patch-level notes
-----------------

Only two patch releases in 2.11 change anything the plugin can observe.

| Version | Change |
|---|---|
| 2.11.1 | `{translate}` registered. Lowercase filter aliases `breaklines`, `datastream`, `replaceRE`, `striphtml`, `striptags` added. `{_}` as a paired tag deprecated. `n:ifcontent` on an empty element deprecated. |
| 2.11.7 | Nothing template-visible; the last 2.x release. Kept in the table only because it is the pinned floor. |

2.11.2 through 2.11.6 change no tag, filter, function or accepted syntax.


PHP requirement
---------------

`composer.json` in v2.11.7 requires `php: 7.1 - 8.3`. Relevant to the plugin only
as a sanity check: a project on PHP 7 cannot be running Latte 3.
