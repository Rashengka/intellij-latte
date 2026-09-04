Latte plugin playground
=======================

A small PHP project for exercising the plugin by hand in a sandbox IDE. It is
not a test fixture set — the automated fixtures live in
`src/test/resources/data/` and are checked by `./gradlew test`. This is the
thing you open and click around in.

Run it from the repository root:

```sh
./gradlew runIdeWithPlayground
```

That builds the plugin, starts a sandbox PhpStorm with it installed, and opens
this directory as the project. The sandbox keeps its own settings, so it will
not disturb the IDE you normally work in.

What to look at
---------------

Every template says in its own header what it promises, and all but one promise
the same thing: **nothing in this file may be underlined**. That promise is
checked — `SandboxTemplatesAreQuietTest` opens every template in this directory
and fails on any report it did not already know about, so a template added here
is covered the moment it is committed. `errors-expected.latte` is the one that
promises the opposite, and `ExpectedErrorsTest` checks that one line by line.

The templates are grouped by subject rather than by the bug that produced them:
when something is reported wrongly, the file to open is the one named after the
subject.

| File | What it is for |
|---|---|
| `templates/control-flow.latte` | `{if}`, `{ifset}`, `{switch}`, `{foreach}` with `$iterator`, `{for}`, `{while}`, `{iterateWhile}`, `{first}`/`{sep}`/`{last}`, `{breakIf}`/`{continueIf}`/`{skipIf}`, `{ifchanged}`, `{capture}`, `{try}`. |
| `templates/n-attributes.latte` | The attribute form of the paired tags, the `n:inner-` and `n:tag-` prefixes, and the five attributes with no `{tag}` form. |
| `templates/template-inheritance.latte` | `{layout}`, `{block}`, `{define}`, `{include}`, `{include parent}`, `{import}`, `{embed}`, a block name computed at runtime. Its three companions — `inheritance-layout.latte`, `inheritance-blocks.latte`, `inheritance-card.latte` — are the files it leans on, and `inheritance-extends.latte` is the same thing written with `{extends}`. |
| `templates/filters.latte` | Every filter Latte 2.11 registers, once each, with the arguments the documentation gives them — including the explicit escapers and the case-insensitive spellings. |
| `templates/functions.latte` | Every function Latte registers, the two `nette/application` adds, and plain PHP calls the plugin must pass through. |
| `templates/expressions-and-arrays.latte` | Arrays, keys, chains, closures, casts, interpolation, `{php}`, `{l}`/`{r}`. |
| `templates/escaping-contexts.latte` | The same variable printed in HTML text, an attribute, a URL, `<script>`, `<style>` and a comment, plus `{contentType}`, `|noescape` and `{spaceless}`. |
| `templates/forms.latte` | What `nette/forms` adds: `{form}`, `{input}`, `{label}`, `{inputError}`, `{formContainer}`, `n:name`. |
| `templates/links-and-components.latte` | What `nette/application` adds: `{link}`, `n:href`, `{control}`, `{snippet}`, the presenter's variables, `{cache}`. |
| `templates/localization.latte` | `{_}`, `{translate}`, `|translate`. |
| `templates/declared-parameters.latte` | `{parameters}` and `{varType}` — a variable they declare is defined. |
| `templates/template-type.latte` | `{templateType}`: the variables come out of `app/Presenters/ArticleTemplate.php` and nowhere else. |
| `templates/debugging.latte` | `{dump}`, `{debugbreak}`, `{trace}`, `{varPrint}`, `{templatePrint}`, `{sandbox}`. |
| `templates/syntax-modes.latte` | `{syntax latte}`, `{syntax double}`, `{syntax off}`. All three are valid in Latte 2.11, and none of them has to be closed. Note that leaving double syntax takes `{{/syntax}}`: while it is on, a single-brace tag is text. |
| `templates/latte-2-only.latte` | Constructs that exist only in the Latte 2 line: `{includeblock}`, `|noiterator`, `{syntax latte}`, `{snippet}` as a core tag. |
| `templates/latte-3-only.latte` | Constructs that exist only in the Latte 3 line: `{exitIf}`, `n:else`, `n:elseif`, `{syntax single}`, the filters and functions added inside 3.x, `nette/assets`. |
| `templates/resolved.latte` | Everything resolves. Nothing should be underlined, and Ctrl-click should navigate into `app/`. |
| `templates/errors-expected.latte` | Deliberate mistakes. Every line here **should** be reported — silence means the plugin stopped noticing real errors. |
| `templates/brace-nesting.latte` | Braces that used to end a tag early: unbalanced ones inside a string literal, and a `{php}` body nested more than one level deep. Nothing here may be underlined. |
| `templates/custom-definitions.latte` | A tag, a filter, a function, a variable and an `n:` attribute that only this project defines. Nothing here may be underlined either — and switching a kind off in the settings has to turn them red without the file being touched. |

The two version files are kept apart from the rest on purpose. A construct that
belongs to one line only would be red on a project using the other, and a
template that is always red teaches nobody anything. The plugin has no version
switch yet, so today it accepts the union of the supported range and both files
are quiet.

Custom definitions
------------------

The definitions `templates/custom-definitions.latte` uses are written into
`.idea/latte.xml` before the sandbox IDE starts, so there is nothing to set up by
hand. They are on the four pages under
**Settings | Languages & Frameworks | Latte**: one pair tag (`panel`), one
unpaired tag (`icon`), one attribute-only tag (`tooltip`), one filter taking an
argument (`excerpt`), one function (`formatPrice`) and one variable
(`$siteName`).

The file is only ever written when it is not there, so anything changed in those
pages stays changed. To go back to the definitions the build ships, delete
`.idea/latte.xml` and start the sandbox again.

The PHP under `app/` exists so the type-aware features have something to
resolve against: `{varType}` declarations in the templates point at
`App\Model\Article`, `{control articleList}` at `createComponentArticleList()`,
and `{link Article:detail}` at `ArticlePresenter::renderDetail()`.

`composer.json` declares `latte/latte ^2.11.7`, which is the version this fork
targets and the one the templates are written for.
