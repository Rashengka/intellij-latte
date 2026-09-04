Design proposal: version-aware behaviour
=======================================

Not implemented. This is input to a separate decision.

The plugin currently has no concept of a Latte version. `LatteDefaultConfiguration`
is a single static table that is the **union** of Latte 2 and Latte 3, so the
plugin simultaneously accepts `{includeblock}` (removed in 3) and `{exitIf}`
(added in 3.0.5). `LatteAnnotator.VALID_SYNTAX_MODES` is the same union and
accepts `latte`, `single`, `double` and `off` together, even though no single
Latte release accepts all four. Every one of those is a wrong answer for some
project.

This document proposes how the plugin should determine the version, how a manual
override interacts with detection, what to do when nothing can be determined,
and — the part worth the most — exactly which existing code would have to branch.


1. Reading the version from a project
-------------------------------------

### 1.1 `composer.lock` is the only exact source

`composer.lock` records the resolved version of every installed package,
including transitive ones. It is the file that says what the project is actually
running.

    {
      "packages": [
        { "name": "latte/latte", "version": "v3.1.6", ... }
      ],
      "packages-dev": [ ... ]
    }

Rules:

- Search `packages` first, then `packages-dev`. A Latte that is only in
  `packages-dev` is still the Latte that the IDE-visible templates compile
  against in tests, and it is better than no answer; record which array it came
  from so the UI can say so.
- Strip a leading `v`. Accept `dev-*` and `*-dev` strings by falling back to the
  branch alias if `extra.branch-alias` is present, and treating anything still
  unparseable as "undetermined" rather than guessing.
- **A transitive dependency is a perfectly good answer.** Most Nette projects do
  not require `latte/latte` directly; they require `nette/application`, which
  requires Latte. `composer.lock` flattens that, so no special handling is
  needed — the entry is there either way. Do not restrict the lookup to the
  packages named in `composer.json`.

### 1.2 `composer.json` is a constraint, not a version

`composer.json` gives `"latte/latte": "^3.0"`, which is a *range*. It cannot
produce an exact version and must never be reported as one. Use it only as a
fallback, and only to pick a minor line:

- Parse the constraint far enough to get its lower bound.
- Map the lower bound to the nearest supported line: `2.11`, `3.0` or `3.1`.
- Mark the result as "inferred from a constraint" so the UI can distinguish it
  from a locked version, and so an inspection can choose to stay silent on
  anything that depends on a patch-level boundary (`{syntax single}`, `n:else`,
  `|localDate`).
- Consider `require-dev` as well as `require`, same as for the lock file.

A second, weaker signal lives in the same file: the `php` requirement. Latte 3.1
requires PHP 8.2 or newer and Latte 3.0.26 requires 8.0; a project pinned to PHP
7.x cannot be on Latte 3 at all. Use it only to *rule out* a line, never to pick
one.

### 1.3 Several `composer.json` files

Monorepos are normal. The rule should be nearest-ancestor, not project-root:

1. From the `.latte` file being analysed, walk up the directory tree.
2. The first directory containing a `composer.lock` wins.
3. If none is found before the content root, the first directory containing a
   `composer.json` wins.
4. If none is found at all, fall through to section 3.

Walking up from the file rather than down from the project root is what makes a
monorepo with one Latte 2 package and one Latte 3 package behave correctly, and
it costs nothing in the single-package case. Cache the resolution per directory,
not per file.

`vendor/latte/latte/src/Latte/Engine.php` also carries the exact version in
`Engine::Version` / `Engine::VERSION`, and reading it would be authoritative even
when the lock file is stale. It is a reasonable tie-breaker but a poor primary
source: `vendor/` is often excluded from the IDE's index, and parsing a PHP
constant out of a source file is more fragile than reading JSON. Propose it only
as an optional confirmation step, off by default.

### 1.4 Invalidation

All of the above must be recomputed when the source file changes. A
`VirtualFileListener` (or a `PsiModificationTracker` scoped to the composer
files) that bumps a modification count is enough. See section 4.6 for why this
matters more than it looks.


2. The manual override
----------------------

The override must win over detection, unconditionally. The reason is in the
brief for this whole document: a developer may be writing templates that target a
newer Latte than the one installed, because the running application still needs
the old one. Detection describes what is installed; the override describes what
the developer is writing for. When they disagree, the developer is right.

Proposed shape, as a new field on `LatteSettings`
(`src/main/java/dev/noctud/latte/settings/LatteSettings.java`, which is already
a project-level `PersistentStateComponent` writing `latte.xml`):

    public String latteVersionOverride = "";   // "" = auto-detect

with a combo box in `LatteSettingsForm`
(`src/main/java/dev/noctud/latte/ui/LatteSettingsForm.java:17`) offering:

| Entry | Meaning |
|---|---|
| `Auto-detect (currently: 3.1.6 from composer.lock)` | default; the parenthetical is live text so the user can see what detection found |
| `Latte 2.11` | force |
| `Latte 3.0` | force |
| `Latte 3.1` | force |

Three deliberate choices:

- **Minor granularity in the UI, patch granularity internally.** The user picks a
  line; detection may yield a patch. A forced `3.0` should be interpreted as "the
  newest 3.0 the plugin knows about" so that a developer targeting 3.0 is not
  told that `|firstLower` is unknown. Being permissive in the forced case is
  correct, because the forced case is explicitly about writing code for a version
  that is not installed.
- **No "Latte 3 (any)" entry.** It reads as helpful and is not: it would have to
  resolve to a union again, which is the current broken behaviour under a new
  name.
- **Store the override as a string, not an enum ordinal.** The set of supported
  lines will grow; ordinals in a persisted `latte.xml` will not survive that.

The existing `enableNette` / `enableNetteForms` checkboxes stay as they are —
they answer a different question (which *packages* are present) and combine with
the version rather than replacing it.


3. The default when nothing can be determined
---------------------------------------------

**Propose: the newest supported line, currently 3.1, and treat "undetermined" as
a distinct internal state that suppresses version-specific diagnostics rather
than as a synonym for the default.**

The argument, since this is the kind of decision that should not be asserted:

- *Why not the union of all versions (today's behaviour)?* Because the union
  cannot say no to anything. It cannot report `{includeblock}` as removed under
  Latte 3, cannot report `{exitIf}` as unavailable under 2.11, and cannot report
  `{syntax single}` as unavailable under 2.11 or `{syntax latte}` as unavailable
  under 3.0.2+. Widening a set until nothing is flagged removes the false
  positives and every true positive with them; that is a decision to stop
  checking, not a version model.
- *Why not 2.11, the version the target projects use?* Because the default is a
  fallback for projects where detection failed, and detection succeeds precisely
  in the well-formed Composer projects that this fork's target projects are.
  A project with no `composer.lock` and no `composer.json` is far more likely to
  be a scratch file, a snippet opened outside a project, or a fresh template
  directory than a legacy 2.11 codebase — legacy codebases have lock files.
  Defaulting to the floor would degrade the common case to serve a case that in
  practice never reaches the fallback.
- *Why the newest rather than "most popular"?* Popularity is unmeasurable from
  inside the IDE and changes over time; "newest supported" is a rule that stays
  correct without maintenance. It is also the version a developer writing a new
  template in an empty directory is most likely to mean.
- *Why the extra "undetermined" state?* Because the default has to be a guess,
  and the project rule is that what the plugin cannot prove, it does not report.
  With an explicit "undetermined" flag, the resolver can return the 3.1 tables
  (so completion and documentation still work, and work well) while every
  inspection that would produce a version-specific *error* — unknown tag, unknown
  filter, invalid `{syntax}` argument — downgrades to silence. That gives good
  editing behaviour and zero false positives, which a plain default cannot do.

So the resolution order is:

1. Manual override, if set. Confidence: certain.
2. `composer.lock`, nearest ancestor. Confidence: certain, patch-exact.
3. `composer.json` constraint lower bound, nearest ancestor. Confidence: line
   only; suppress patch-level diagnostics.
4. Nothing. Behave as 3.1 for completion and documentation; suppress all
   version-specific error reporting.


4. Where the existing plugin code would have to branch
------------------------------------------------------

Paths are relative to the repository root. Line numbers are from
`fix/reference-crashes` at HEAD on 2026-09-04 and go stale the moment anything
above them is edited — re-`grep` for the quoted code rather than trusting the
number. Every citation below was re-checked against that HEAD; the `LatteAnnotator`
rows in 4.4 had already moved and are corrected here.

### 4.1 The registry itself — the main one

`src/main/java/dev/noctud/latte/config/LatteDefaultConfiguration.java`

A single 416-line static table, built once in the constructor and cached in a
static field (`instance`, line 14). It is the union described above. Concrete
wrong rows, all verified against the engine source:

Latte 2 only, currently offered to Latte 3 projects:

| Line | Registration | Correct for |
|---|---|---|
| 70 | `_` | 2.11 core; 3.x only with `TranslatorExtension` |
| 71 | `translate` | 2.11.1+ core; 3.x only with `TranslatorExtension` |
| 76 | `case` | 2.11 as a tag; 3.x only as an inner tag of `{switch}` |
| 77 | `catch` | **not a Latte tag in any version in the range** — no `addMacro('catch')` in 2.11 and no `'catch'` in any 3.x `getTags()`. Worth checking separately from the version work |
| 86 | `else` | 2.11 as a tag; 3.x inner tag only |
| 87 | `elseif` | 2.11 as a tag; 3.x inner tag only |
| 88 | `elseifset` | 2.11 as a tag; 3.x inner tag only |
| 97 | `includeblock` | 2.11 only, and deprecated there; no 3.x equivalent |
| 101 | `class` | attribute-only in both; registry name differs per line |
| 102 | `attr` | attribute-only in both |
| 103 | `ifcontent` | attribute-only in both |
| 108 | `snippet` | 2.11 core; 3.x `nette/application` |
| 109 | `snippetArea` | 2.11 core; 3.x `nette/application` |
| 117 | `tag` | attribute-only in both |

Latte 3 only, currently offered to Latte 2.11 projects:

| Line | Registration | Available from |
|---|---|---|
| 120 | `exitIf` | 3.0.5 |
| 184 | `\|column` | 3.1.3 |
| 185 | `\|commas` | 3.1.3 |
| 186 | `\|filter` | 3.0.20 |
| 187 | `\|firstLower` | 3.0.22 |
| 188 | `\|group` | 3.0.16 |
| 189 | `\|limit` | 3.1.3 |
| 190 | `\|localDate` | 3.0.18 |
| 204 | `group()` | 3.0.16 |
| 205 | `hasBlock()` | 3.0.10 |
| 206 | `hasTemplate()` | 3.0.22 |

Version-independent but wrong today, and cheapest to fix while touching this
file:

| Line | Problem |
|---|---|
| 112 | `syntax` argument hint is `"off \| double \| single"` — correct for 3.0.24+, wrong for 2.11 (`latte`) and for 3.0.2-3.0.23 (neither) |
| 135 | `strip` registered without noting it is an alias of `spaceless` |
| 144 | `breaklines` registered, `breakLines` is not — the camelCase spelling is the primary name in both lines |
| 164 | `escapeurl` registered, `escapeUrl` is not |
| 167 | `checkurl` registered, `checkUrl` is not |
| 192 | `toggle` registered as an ordinary filter; it is an attribute-context compiler directive in 3.1 |
| — | `\|accept` (3.1) is not registered at all, and `\|noiterator` is not either — both are compiler directives that `ModifierDefinitionInspection` will report as undefined |
| — | 15 core filters are missing entirely from the registry in both lines: `breakLines`, `checkUrl`, `datastream`, `escapeCss`, `escapeHtml`, `escapeHtmlComment`, `escapeICal`, `escapeJs`, `escapeUrl`, `escapeXml`, `replaceRe`, `striphtml`, `striptags`, plus `escape` (3.0.5+) and `map` (3.1.6+) |

Proposed shape: keep one table but give every entry a version predicate, then
have the accessor filter. A `LatteVersionRange` value object with
`sinceInclusive` / `untilExclusive` and a `matches(LatteVersion)` method is
enough; the registration helpers (`addLatteTag` at line 273, the three `addLatteFilter`
overloads at 277/281/285, `addLatteFunction` at 289) already funnel everything
through a handful of methods, so the predicate can be an extra optional argument with a
"present in all supported versions" default. That keeps the diff proportional to
the number of rows that actually differ, which is about forty out of two hundred.

### 4.2 The per-project accessor

`src/main/java/dev/noctud/latte/config/LatteConfiguration.java`

- Lines 44-58: instances are cached in a `static Map<Project, LatteConfiguration>`
  that is never evicted. This is where the resolved version would naturally live,
  and it is the wrong container for it — a project-level light service with a
  modification tracker is the right one, both because the version changes when
  `composer.lock` changes and because the current map leaks closed projects.
- `getTags()` at line 188, `getFilters()` at 216, `getFunctions()` at 160,
  `getVariables()` at 133 and 138: each merges custom settings over
  `LatteDefaultConfiguration`, filtered only by vendor
  (`settings.isEnabledSourceVendor`). The version filter belongs here, next to
  the vendor filter, so that every consumer gets it for free.
- Line 76 `getFilter(String)` does an exact map lookup. **Under Latte 2.11 filter
  names are case-insensitive**, so this method needs a version-dependent lookup
  (exact for 3.x, case-insensitive for 2.11). This alone fixes the false
  "Undefined latte filter '|escapeUrl'" on 2.11 projects that the missing
  camelCase registrations above cause.

### 4.3 The settings object and its form

`src/main/java/dev/noctud/latte/settings/LatteSettings.java`
`src/main/java/dev/noctud/latte/ui/LatteSettingsForm.java`

The override field and combo box from section 2. Note
`LatteSettings.java:24` — `enableXmlLoading` is a persisted setting that nothing
reads; `src/main/resources/xmlSources/Latte.xml` is referenced nowhere in the
plugin (the only `Latte.xml` in `plugin.xml:60` is the live-templates file). That
XML is a stale design document whose content already disagrees with the Java
(`syntax` arguments `"off | double | latte"` there versus `"off | double |
single"` in the Java, and it lacks `exitIf`, `group`, `hasBlock`,
`hasTemplate`). Whatever is decided about versioning, that file should either be
deleted or made generative — leaving two registries that disagree is how the
current inconsistencies got in.

### 4.4 The annotator — the hard errors

`src/main/java/dev/noctud/latte/annotator/LatteAnnotator.java`

| Line | Code | Why it is version-dependent |
|---|---|---|
| 31 | `VALID_SYNTAX_MODES = Set.of("off", "double", "single", "latte")` | now the **union** of every mode in the range, so no correct template is flagged in any version — but it is also the union problem in miniature: `latte` is rejected by 3.0.2+, `single` by everything below 3.0.24, and neither is reported. Correct behaviour once a version is known is a per-version set, not this one |
| 85 | `n:syntax` value check | same set |
| 164, 170 | `{syntax}` argument check | same set; `:170` emits "Missing syntax mode. Expected: off, double, single, or latte", which is correct from 2.11.0 onward but not for 2.10 |
| 111, 113, 117 | "Can not use n:… as normal tag" / "can not be used as pair tag" / "Unknown tag" | driven by `getTag()`, so they inherit whatever the registry says. With a version-filtered registry this becomes correct automatically — but "Unknown tag" is also the loudest false positive, so it should additionally respect the "undetermined" state from section 3 |
| 66-70 | attribute-tag existence and `UNPAIRED` check | in Latte 3 the `{tag}`-versus-`n:tag` rule is exactly `TemplateParser::addTags()` — attribute-only when the registered name starts with `n:`, `n:`-capable when the parser is a generator. The plugin's `Type` enum already models this; only the per-version data differs |

### 4.5 The lexer

`src/main/java/dev/noctud/latte/lexer/grammars/LatteTopLexer.flex`

| Line | Pattern | Note |
|---|---|---|
| 50 | `SYNTAX_OFF_MACRO = "{syntax" [ \t]+ "off" [ \t]* "}"` | fine in all versions |
| 51 | `SYNTAX_DOUBLE_MACRO = "{syntax" [ \t]+ "double" [ \t]* "}"` | fine in all versions |
| 52, 55 | `{/syntax}` / `{{/syntax}}` | fine |
| 329, 350, 371 | `n:syntax` value handling, compares against `"off"` | fine |

The lexer is the one layer that mostly does not need to branch, because `off`
and `double` are the only modes that change tokenisation and both exist
everywhere in the range. `{syntax latte}` and `{syntax single}` restore the
default delimiters, which is the lexer's normal state, so they need no rule.
That is a good outcome — a version-dependent lexer would force a full reparse of
every open file on a version change.

There is one genuine lexer-level difference the plugin does not model, and it is
worth recording even though it is out of scope here: **Latte 3.1 accepts an
unquoted n:attribute value**, `<div n:if={$cond}>`. Line 250,
`"n:" [^ \t\r\n/>={]+`, excludes `{` from the attribute *name*, which is correct,
but the value side is only handled for quoted values. Verify before assuming it
is broken.

### 4.6 The parser — the reason this is not a pure display change

`src/main/java/dev/noctud/latte/parser/LatteParserUtil.java:183-186`

    private static LatteTagSettings getTag(PsiBuilder builder) {
        return LatteConfiguration.getInstance(builder.getProject()).getTag(getMacroName(builder));
    }

The tag registry participates in **parsing**, not just in inspection. Whether a
tag is treated as paired decides the shape of the PSI tree. So changing the
version changes the PSI, which means:

- The version must be part of whatever the parser's caches key on. Changing the
  override, or editing `composer.lock`, has to invalidate parsed files —
  `PsiManager.dropPsiCaches()` plus a modification-tracker bump, not just a
  repaint.
- `src/main/java/dev/noctud/latte/parser/LatteParser.bnf:226` is a second parser-
  level dependency:

      macroModifier ::= T_MACRO_FILTERS (T_PHP_COLON macroModifierPart)*

  Filter arguments are modelled as colon-separated only. That is Latte 2 syntax.
  In both lines the *first* separator may be `:` or `,`, and in Latte 3
  subsequent separators must be commas — so a 3.x template writing
  `|replace: a, b` is being parsed by a grammar that only knows about colons.
  Whether this currently misparses or merely under-structures needs a test before
  it is changed; either way it is version-relevant and it is in the grammar, so
  changing it regenerates `src/main/gen/`.
- The stub indexes under `src/main/java/dev/noctud/latte/indexes/` are built from
  that PSI. `LatteFilterStubType` in particular stores filter names. If the
  registry becomes version-dependent, the index `getVersion()` values (currently
  `super.getVersion() + 3`, e.g.
  `src/main/java/dev/noctud/latte/indexes/extensions/LattePhpMethodIndex.java:24`)
  need bumping once, and the per-project version must not silently change the
  meaning of already-indexed data. The safe design is for the *index* to stay
  version-agnostic and for filtering to happen at query time — which the
  section 4.2 placement already gives.

### 4.7 Inspections

| File | Line | Version dependence |
|---|---|---|
| `inspections/ModifierDefinitionInspection.java` | 55-56 | "Undefined latte filter" comes straight from `getFilter()`. Needs the case-insensitive lookup for 2.11, the version filter for the twelve version-specific filters, and an allow-list for the compiler directives (`noescape`, `nocheck`, `noCheck`, `noiterator`, `noIterator`, and `toggle`/`accept`/`json` in 3.1), and — under 2.11 only — the same allow-list matched case-insensitively |
| `inspections/DeprecatedTagInspection.java` | 45-52 | Reads `LatteTagSettings.isDeprecated()`, a single boolean. Deprecation is version-scoped: `{includeblock}` is deprecated in 2.11 and *removed* in 3, `{first}`/`{last}`/`{sep}` outside `{foreach}` are deprecated only from 3.1.6. The flag needs to become a version range, and the inspection needs a second severity for "removed in this version" |
| `inspections/ModifierNotAllowedInspection.java` | — | which tags accept filters differs between lines (`{_}` is a filter-taking tag in 2.11 core, an extension tag in 3.x) |
| `inspections/VariablesInspection.java` | — | `{foreach}` variable scoping. Latte 3.1.3 added `Feature::ScopedLoopVariables`, but it is **off by default** and invisible from the template, so the correct behaviour is to keep treating loop variables as leaking in all versions. Recorded here so the flag is not "fixed" into a false positive later |
| `inspections/MacroVarInspection.java`, `MacroVarTypeInspection.java` | — | `{var}` accepted a bare word, `=>` and `;` in 2.11 with deprecations; 3.x rejects all three. Deprecation-severity under 2.11, error under 3.x |

### 4.8 Everything else that reads the registry

These need no logic of their own — they become correct once section 4.2 filters
by version — but they are the visible surface, so they are where a regression
would be noticed:

- `src/main/java/dev/noctud/latte/completion/LatteCompletionContributor.java:51`,
  `:86`, `:105`, `:187` — tag, filter and n:attribute completion.
- `src/main/java/dev/noctud/latte/completion/providers/LattePhpFunctionCompletionProvider.java:69`
  — function completion.
- `src/main/java/dev/noctud/latte/settings/LatteTagSettings.java:162` and `:170`
  (`getAllowedNetteAttributes`, `createNetteAttributes`) — derives `n:x`,
  `n:inner-x`, `n:tag-x` from the tag type. The rule is the same in both lines;
  only the input data changes.
- `src/main/java/dev/noctud/latte/utils/LatteTagsUtil.java:12-43` — a second,
  independent hardcoded tag list (`Type` enum) used for link tags, context tags
  and type tags. It is missing `exitIf`, `translate`, `switch`, `case`,
  `iterateWhile` and `try`, and it lists `snippet`/`snippetArea` as context tags
  without noting they are Latte-2-core / Nette-3. Any version work should
  reconcile this list with the registry rather than leaving two sources.


5. Suggested order of work
--------------------------

Not a plan, just the dependency order, so the decision can be scoped.

1. **Correctness fixes that need no version at all.** The fifteen missing core
   filters, the compiler-directive allow-list, the `catch` tag that exists in no
   version. These remove false positives immediately and are independent of
   everything below.
2. **The version model.** `LatteVersion` value type, detection from
   `composer.lock` / `composer.json`, the settings override, the project service
   with invalidation.
3. **Version predicates on the registry**, and the filter in
   `LatteConfiguration`.
4. **Case-insensitive filter lookup under 2.11.**
5. **The annotator's `{syntax}` mode set.**
6. **Deprecation ranges** and the second severity in `DeprecatedTagInspection`.
7. **The `macroModifier` grammar**, last, because it regenerates `src/main/gen/`
   and needs its own regression tests first.

Step 1 alone removes the false positives this survey found that fire on correct
templates today, all of them from the filter registry: `|escapeUrl` and the
fourteen other missing core filters reported as undefined, `|noiterator` and
`|accept` reported as undefined, and — under 2.11 — every filter whose case does
not match the registration.

`{syntax latte}` was the other one and is already fixed on this branch:
`VALID_SYNTAX_MODES` now contains `latte`. Step 5 no longer removes a false
positive; it turns a permanently-silent check back into a working one.
