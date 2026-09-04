Latte language reference, 2.11 to 3.1
=====================================

A differential reference of the Latte template language, written for one purpose:
to be the input to a version switch inside this plugin. It is not a general
introduction to Latte. Every page here tries to answer a single question — **what
does the plugin have to do differently depending on which Latte version a project
has installed?**

The user-facing manual lives at <https://latte.nette.org/en/guide>. Where this
document and the manual disagree, this document follows the engine source code and
says so.


Version range covered
---------------------

| Boundary | Version | Why |
|---|---|---|
| Floor | 2.11.7 | The version used by the real projects this plugin is being fixed for. 2.11.7 is also the last release on the 2.x line (checked 2026-09-04). Nothing older is documented except where it explains why something in 2.11 exists. |
| Ceiling | 3.1.6 | Newest tag in `nette/latte` as of 2026-09-04, released 2026-07-27. |

Organised by minor line: **2.11**, **3.0**, **3.1**. A patch version is named only
where behaviour the plugin can observe actually changed in it — a tag, filter or
function added or removed, syntax newly accepted or newly rejected, a deprecation
introduced. There are 40-odd releases in the range and most of them change nothing
the plugin sees.

One thing to keep in mind while reading the patch-level notes: **3.0.x is still
maintained in parallel with 3.1.x**, so release dates do not follow version order.
v3.1.0 is dated 2025-11-26 and v3.0.26 is dated 2026-02-25. Order by version
number, never by date.


How the material is organised
-----------------------------

| File | Contents |
|---|---|
| `common.md` | Syntax and semantics that are identical across the whole 2.11-3.1 range. Background; nothing here needs a version branch. |
| `latte-2.11.md` | The floor. What 2.11 added over 2.10, what it deprecated but still accepts, and what only exists here. |
| `latte-3.0.md` | The rewrite. What the new compiler changed, and the patch-level additions inside the 3.0 line. |
| `latte-3.1.md` | What 3.1 adds, deprecates and changes in meaning relative to 3.0. |
| `migration-2-to-3.md` | The 2 to 3 discontinuity in one place. The largest break in the range; the plugin has to tolerate both sides at once. |
| `reference-tags.md` | Version-stamped table of every tag and n:attribute. |
| `reference-filters.md` | Version-stamped table of every filter. |
| `reference-functions.md` | Version-stamped table of every function. |
| `version-detection.md` | Design proposal: how the plugin should determine the Latte version, what the settings override looks like, and which existing plugin code has to branch. |

The three `reference-*.md` tables are the part the plugin will actually be driven
by. They use consistent columns, one item per row, and no prose inside cells that
would need parsing.


Sources of truth
----------------

In this order, and the earlier one wins:

1. **The engine source code.** For Latte 3 the authoritative registration lives in
   `src/Latte/Essential/CoreExtension.php` (`getTags()`, `getFilters()`,
   `getFunctions()`) plus the other `Extension` classes. For Latte 2 it is
   `src/Latte/Macros/CoreMacros.php` and `src/Latte/Macros/BlockMacros.php`
   (`install()`) plus `src/Latte/Runtime/Defaults.php`.
2. <https://latte.nette.org/en/guide> and the rest of the English documentation.
3. <https://github.com/nette/latte> issues and release notes.

Where a claim could not be established from the source, it is marked
**unverified** in the text along with what was checked. An honest gap is useful;
a confident wrong version boundary is not.


How to regenerate or verify the claims
--------------------------------------

Clone the engine once. Everything must live under `.latte-src/`, which is listed
in `.gitignore` and must never be committed:

    git clone https://github.com/nette/latte.git .latte-src/latte

The tags that provide the tags, filters and functions listed under "other
packages" live in separate repositories. Clone them beside the engine, same
directory, same rule about never committing them:

    git clone https://github.com/nette/application.git .latte-src/application
    git clone https://github.com/nette/forms.git       .latte-src/forms
    git clone https://github.com/nette/caching.git     .latte-src/caching
    git clone https://github.com/nette/assets.git      .latte-src/assets

Confirm the ceiling is still 3.1.6. The `--refs` and the `sed` are both load
bearing — without them `sort -V` sorts on the leading SHA and returns five
arbitrary tags:

    git ls-remote --tags --refs https://github.com/nette/latte.git \
      | sed 's#.*refs/tags/##' | sort -V | tail -5

Read a registration list out of a tag without checking anything out:

    git -C .latte-src/latte show v3.1.6:src/Latte/Essential/CoreExtension.php
    git -C .latte-src/latte show v2.11.7:src/Latte/Macros/CoreMacros.php
    git -C .latte-src/latte show v2.11.7:src/Latte/Runtime/Defaults.php

Compare two versions of one file:

    git -C .latte-src/latte diff v3.0.0 v3.1.6 -- src/Latte/Essential/CoreExtension.php

Find the first release that contains a commit. The `grep '^v'` is load bearing:
`nette/latte` carries one stray tag named `3.1.4` with no `v`, and `sort -V`
puts it in front of every `v2.*`, so a bare `head -1` answers `3.1.4` no matter
which commit was asked about:

    git -C .latte-src/latte tag --contains <sha> | grep '^v' | sort -V | head -1

Find the deprecations a version emits at compile time. Note that this misses
the compile-time diagnostics Latte 2 raises at `E_USER_WARNING` (filter-name
case mismatch, `n:inner-`/`n:tag-` on a void element) and `E_USER_NOTICE`:

    git -C .latte-src/latte grep -n E_USER_DEPRECATED v2.11.7 -- src

Compile a snippet against a specific tag, which settles behaviour questions that
reading registration code cannot. Latte has no runtime dependencies, so
extracting one tag and autoloading `src/` by hand is enough:

    git -C .latte-src/latte archive v3.1.6 src | tar -x -C /tmp/latte-v3.1.6

A note on shells: `zsh` applies history modifiers to `$var:path`, so
`git show $tag:src/...` silently mangles the path. Use `${tag}:src/...`, or run
the loop under `bash`.
