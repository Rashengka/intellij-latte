# Changelog

## [Unreleased]

### Added

- The path in `{asset 'vite:assets/app.ts'}` is a link to the file it names. The mapper name in front of the path is not part of the link, and a path that names no file of the project is left without a link and without a report — which directory a mapper serves is configuration the plugin does not read
- `{include parent}` is a link to the block of the same name in the template this one extends, whenever the chain of `{extends}` can be followed from the sources
- `{include parent}` and `{include this}` outside any block are reported, as is a parent block no template in the chain defines. The second one only where it can be proven: a template that names no parent of its own is given the presenter's layout while rendering, and its blocks then have a parent nothing in the sources can show
- Tags the Nette packages register and the plugin did not know: `{formContext}` and `{formClassPrint}` from nette/forms, `{linkBase}` from nette/application, `{preload}` and `n:asset?` from nette/assets
- The `asset()` and `tryAsset()` functions from nette/assets

### Fixed

- `{include parent}` and `{include this}` were offered as links to a file of that name. They name the block the tag is written in, and there is no such file

- False "Method 'x' not found for type 'T'" warning whenever the type resolved to no class at all — an unindexed project, or a template typed against a package that is not installed. A type the plugin cannot resolve is the case it cannot decide, not one where the method is missing
- False "Unknown tag" error for `{PHP_EOL}` and false "Undefined class" warning for `{\PHP_EOL}`. Both spellings print a constant and are valid in every supported version. A class name is now reported only where PHP requires one: in front of `::`, after `new` or `instanceof`, in a written-out type, and as the argument of `{templateType}`
- False "Function 'DateTimeImmutable' not found" warning for `{var $d = new DateTimeImmutable('…')}`. An unqualified constructor was looked up among the functions; it is resolved as a class now, and a class that is not there is still reported
- False "Missing required filter parameters (2 required)" warning for `{$items|batch:2}`. The array a filter is applied to is not one of its arguments
- Every `{include}`, `{layout}`, `{import}`, `{embed}` and `{sandbox}` was reported as naming a missing file, and the path in them could not be clicked through, unless the project was on the local disk. The quick fix that creates the file went to the local disk too

- `StubTextInconsistencyException` while searching for usages, caused by class, method, property, constant and static-variable references reading the PHP index in their constructor; the index is now read when the reference resolves
- `ArrayIndexOutOfBoundsException` and lost references when `getReferences()` ran on several threads over the same element, and stale references after an edit to a neighbouring part of a `{link}` destination
- Parse error for an interpolated `{$var}` inside a double-quoted string passed to a macro, e.g. `{plink ":Sign:in:{$action}"}`
- Parse error for a quoted string literal that spans a line break inside macro arguments
- Parse error for a `{php}` block whose body contains nested braces, e.g. a `foreach` loop
- False "probably undefined" warning for a variable used in another `n:` attribute of the tag whose `n:foreach` defines it, e.g. `<li n:foreach="$items as $item" n:class="$item->isActive() ? active">`
- False "Unclosed tag syntax" error for `{syntax}` without a `{/syntax}`; the tag switches the delimiters and closing it is optional
- The `{syntax}` argument list and the `n:syntax` error message both named fewer modes than the plugin accepts
- Editor freeze on a nested array literal, e.g. `{= [1, [1, [1, …]]]}`: an array item parsed its first expression twice to find out whether a `=>` followed it, so the cost doubled with every level of nesting. Sixteen levels took seconds; they now take under a millisecond
- A custom tag made the project's `latte.xml` unreadable, so every custom tag, filter, function and variable was lost on the next IDE start and the project's own names were then reported as undefined
- The custom tag, filter and function settings pages showed the state of the custom variables switch and saved it over their own, so turning custom variables off turned the other three off as well
- The custom filter and function settings pages listed nothing unless a custom tag was defined, and Apply then deleted the filters and functions that were not listed
- A settings checkbox toggled with the keyboard left Apply greyed out and the change was dropped
- Adding or changing a custom definition now reaches the templates that are already open instead of waiting for them to be edited or reopened; a tag added as a quick fix is paired correctly straight away
- The dialog that adds a custom tag, filter, function or variable stayed open after OK, so pressing it again added the same definition twice

## [1.8.0] - 2026-07-11

### Added

- Support for the curly-brace syntax in `n:` attributes introduced in Latte 3.1, e.g. `<a n:href={Home:default}>`
- Latte 3.0/3.1 built-in filters: `clamp`, `column`, `commas`, `filter`, `firstLower`, `group`, `limit`, `localDate`, `stripTags`, `toggle`, `translate`
- Latte built-in functions: `group`, `hasBlock`, `hasTemplate`
- `{exitIf}` and `{translate}` tags, plus the `n:elseif` and `n:translate` attributes

## [1.7.4] - 2026-05-22

### Fixed

- Empty-text search exception for bare render/createComponent methods
- NPE in MissingFileInspection when the file has no containing directory

## [1.7.3] - 2026-05-01

### Fixed

- Lexing of unicode letters in macro identifiers and unquoted strings

## [1.7.2] - 2026-04-21

### Performance

- Faster branch switching and PSI reloads in projects with Latte files, template data language detection no longer rereads the full file on every PSI change
- Faster variable completion in macros, deduplication now uses a hash set instead of a linear scan

### Fixed

- `IllegalStateException` on EDT when creating PSI for a Latte file

## [1.7.1] - 2026-04-14

### Fixed

- `{first}...{/first}` producing false parser errors inside `{foreach}` loops
- `{first}` and `{last}` width argument incorrectly marked as required

## [1.7.0] - 2026-03-31

### Added

- Support for `{syntax off}...{/syntax}` — disables Latte macro parsing inside the block
- Support for `{syntax double}...{/syntax}` — switches macro delimiters to `{{...}}`
- Support for `n:syntax="off"` and `n:syntax="double"` attributes on HTML elements

### Fixed

- NPE in file path resolution for virtual directories
- IllegalStateException when creating a new Latte file

## [1.6.5] - 2026-03-26

### Fixed

- Exception when viewing the settings form in the IDE

## [1.6.4] - 2026-03-25

### Fixed

- Variable assignment in conditions not detected as definition
- False "multiple definitions" and "probably undefined" warnings for variables defined in all branches of `{if}/{else}`

### Changed

- Removed unused context caching dead code from `LatteFile`

## [1.6.3] - 2026-03-23

### Fixed

- False "probably undefined" variable warning in nested scopes
- False warnings for function and arrow function parameters

## [1.6.2] - 2026-03-22

### Fixed

- Variable resolution now respects scope contexts (foreach, if, block)
- Variables defined in inner scopes are marked as "probably undefined" outside
- Inner variable definitions correctly shadow outer ones
- Type detection for typed variable definitions (e.g. `{define input, float $name}`)
- Lexer handling of PHP closures inside latte tags (`function() { }`)
- Type compatibility check for union types at different depths
- Deeply nested blocks/snippets producing errors on closing tags
- Null pointer errors during variable rename refactoring

### Changed

- CI now runs tests on every push and PR
- Added tests for inspections, parser edge cases, lexer, and utilities

## [1.6.1] - 2025-12-18

### Fixed

- Detection of absolute links
- Warning about LatteCodeStyleSettingsProvider
- Few other deprecations

## [1.6.0] - 2025-08-31

### Added

- References to presenter components via `{control ...}` etc. (bidirectional)
- Autocompletion of presenter components (and their render methods)
- Link references from presenter methods and class `actionSomething` => `{link something}`
- Usage info in unused PHP fields that are used in latte

### Fixed

- Autocompletion of global functions at the start of the macro
- Indentation of HTML content inside tags on new lines

### Improved

- Presenter name resolving, when using `{templateType}`

## [1.5.5] - 2025-08-25

### Fixed

- Autocompletion in `{var}`, `{varType}` and `{templateType}`

## [1.5.4] - 2025-08-23

### Fixed

- IntelliJ freezes while typing in non-closed latte tags

## [1.5.3] - 2025-08-21

### Fixed

- Autocompletion speed
- Autocompletion was not showing when `{$` was typed

## [1.5.2] - 2025-08-10

### Fixed

- Inconsistencies in adding custom filters and functions (@vrana)

## [1.5.1] - 2025-08-01

### Fixed

- Error inspection in multiline file includes (@vrana)

## [1.5.0] - 2025-07-30

### Added

- Enum support

## [1.4.1] - 2025-07-05

### Added

- Support for {asset} and n:asset

## [1.4.0] - 2025-04-07

### Added

- Support for typehints in iterables using generics

## [1.3.2] - 2025-01-22

### Fixed

- Disabled file existence checks in functions inside tags

## [1.3.1] - 2024-12-26

### Added

- File existence checks in tags like `{import}` or `{include}`

## [1.3.0] - 2024-05-22

### Added

- Link autocompletion in `{link}` and `n:href`

## [1.2.1] - 2024-05-05

### Fixed

- Reloading variables when {templateType} is changed
- Cache bugs in link references (e.g. when renaming files / methods) - disabled cache

## [1.2.0] - 2024-05-05

### Added

- Support for nette links - linking presenter, signal, action, etc.

## [1.1.0] - 2024-05-05

### Added

- File and directory linking in {import}, {include} and similar tags
- Auto-completion of directories and latte files in file import tags

## [1.0.6] - 2024-04-28

### Fixed

- Pair tag hover length (caused by previous fix)
- Iterable type detection (warnings only, can't read generics yet)
- End tag auto-completion (double slashes)

## [1.0.5] - 2024-04-27

### Fixed

- IndexOutOfBoundsException when not closing a tag right away
- RangeOverlapException in closed tag references

## [1.0.4] - 2024-04-26

### Changed

- Default link color (blue has better visibility)

## [1.0.3] - 2024-04-25

### Fixed

- Default variable color
- Default link color

## [1.0.2] - 2024-04-22

### Fixed

- Error `Cannot distinguish StubFileElementTypes` (performance issue)

## [1.0.1] - 2024-04-21

### Added

- Null-safe operator support
- Plugin .jars to latest release

## [1.0.0] - 2024-04-21

### Added

- Support for PhpStorm up to 2024.1
- Previously deleted features (code completion etc.)
- Automatic builds on push via GitHub actions

### Fixed

- Build process

### Changed

- Plugin name to Latte Support (fork of https://github.com/nette-intellij/intellij-latte)
- Gradle to version 8.7
- Grammar kit and intellij platform versions to latest

### Removed

- Unused libs, docs, ads, sponsoring info, some readme content
