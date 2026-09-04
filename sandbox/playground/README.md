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

The PHP under `app/` exists so the type-aware features have something to
resolve against: `{varType}` declarations in the templates point at
`App\Model\Article`, `{control articleList}` at `createComponentArticleList()`,
and `{link Article:detail}` at `ArticlePresenter::renderDetail()`.

`composer.json` declares `latte/latte ^2.11.7`, which is the version this fork
targets and the one the templates are written for.
