Latte coverage fixtures
=======================

A combinatorial base of `.latte` files whose only job is to be parsed. Between
them they exercise every tag, `n:` attribute, filter, function and syntax form
the plugin is expected to handle, plus the combinations that occur in real
templates and the ones that stress a lexer even when they are rare.

The base is driven by `dev.noctud.latte.coverage.CoverageParseTest`, which walks
this directory, parses every `.latte` file and asserts that the resulting PSI
contains no `PsiErrorElement`. It needs no environment variable and no external
file, so it runs on every `./gradlew test`.

Scope: Latte **2.11.7** plus everything the plugin's own registry
(`dev.noctud.latte.config.LatteDefaultConfiguration`) accepts, which includes a
handful of tags introduced later in the 3.x line. The version-stamped surface
these fixtures were written against is documented in `docs/latte/`.


Layout
------

| Directory | What it covers |
|---|---|
| `tags/` | One file per tag or closely related group: control flow, blocks, includes, variables, `{php}`, comments, literals, debugging tags, and the tags contributed by `nette/application`, `nette/forms` and `nette/caching`. |
| `n-attributes/` | Every `n:` attribute form, including the `n:inner-` and `n:tag-` prefixes, quoted, curly and unquoted values, and several attributes on one element. |
| `filters/` | Every filter in the 2.11 registry, filters with arguments, chained filters, filters applied to tags, the compiler directives that look like filters (`noescape`, `nocheck`, `noiterator`), and unregistered project filters. |
| `functions/` | The core Latte functions, the two functions from `nette/application`, and ordinary PHP calls used as Latte functions. |
| `expressions/` | The PHP expression shapes that appear inside tag arguments: operators, ternaries, null coalescing, array literals and access, method chains, static access, closures, casts, string forms and interpolation, numeric literals, nullsafe access, named arguments, spread, and arguments spanning several lines. |
| `nesting/` | How constructs combine: control flow inside loops, `n:` attributes on elements that also contain tags, tags inside HTML attribute values, tags inside quoted strings, `{block}`/`{define}`/`{include}` interplay, `{capture}` around `{switch}`, forms, snippets, and two deliberately dense files. |
| `syntax/` | `{syntax}` and `n:syntax` in every mode the 2.11 line accepts, and `{contentType}` for the content types that change how the rest of the file is read. |
| `edge-cases/` | The awkward shapes: nested braces inside `{php}` and inside expressions, braces in `<script>` and `<style>`, empty tag bodies, unusual whitespace, comments containing brace-like text, unquoted attributes, void and self-closing elements, plain text that looks like markup, long chains, and deep nesting. |

Every file in this tree is valid Latte 2.11 that the plugin is expected to parse
without error. Deliberately invalid input does not belong here; if it is ever
needed it goes in an `invalid/` subdirectory, which the test skips.


Conventions
-----------

- File names are PascalCase, matching `data/parser/`.
- Files are small and focused, so a failure names the construct that caused it.
  The exceptions are `nesting/DenseCombination.latte` and
  `nesting/DeepNesting.latte`, which exist precisely to catch interaction bugs.
- All identifiers are generic and invented: `Article`, `Product`, `$items`,
  `$user`, `App\Model\ArticleFacade`, `Admin:Dashboard:default`,
  `partials/item.latte`. No fixture is derived from any real template.
- Everything is in English, including comments inside the fixtures.


Known parser gaps
-----------------

Five fixtures are valid Latte 2.11 that the plugin currently rejects. They are
listed in `KNOWN_FAILURES` in the test rather than removed, so the gaps stay
visible and the test starts failing again the moment one of them is fixed:

- An **unbalanced curly brace inside a quoted string literal** ends the tag.
  `{= 'a } brace'}`, `{= "a } brace"}` and `{= 'an { brace'}` are all cut short
  at the brace, and the remainder of the line becomes stray text. Latte's own
  tokenizer skips over string literals when it looks for the closing brace. A
  *balanced* pair inside a string is fine, which is why `{= '{tagLooking}'}`
  parses.
- A **`{php}` body accepts only one level of nested braces**. A single block -
  a `foreach` body, a closure body - parses; a block inside a block does not,
  on one line or spread over several.


Adding a fixture
----------------

Drop a `.latte` file into the directory that matches its theme. The test picks
it up automatically; there is no list to update. If the new file exposes a
parser bug, leave it in place and add its relative path to the `KNOWN_FAILURES`
list in `CoverageParseTest` with a comment naming the construct, so the gap
stays visible instead of disappearing.
