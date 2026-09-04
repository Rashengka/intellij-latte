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

| File | What it is for |
|---|---|
| `templates/resolved.latte` | Everything resolves. Nothing should be underlined, and Ctrl-click should navigate into `app/`. |
| `templates/filters.latte` | Every filter Latte defines, including the explicit escapers and the case-insensitive spellings. None may be reported as undefined. |
| `templates/syntax-modes.latte` | `{syntax latte}`, `{syntax double}`, `{syntax off}`. All three are valid in Latte 2.11, and none of them has to be closed. Note that leaving double syntax takes `{{/syntax}}`: while it is on, a single-brace tag is text. |
| `templates/errors-expected.latte` | Deliberate mistakes. Every line here **should** be reported — silence means the plugin stopped noticing real errors. |
| `templates/brace-nesting.latte` | Braces that used to end a tag early: unbalanced ones inside a string literal, and a `{php}` body nested more than one level deep. Nothing here may be underlined. |
| `templates/custom-definitions.latte` | A tag, a filter, a function, a variable and an `n:` attribute that only this project defines. Nothing here may be underlined either — and switching a kind off in the settings has to turn them red without the file being touched. |

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
