# Changelog

## [Unreleased]

### Added

- Syntax inside a tag that Latte refuses to compile is reported: a bracket left open or closing nothing (`{= [1, 2}`, `{= )}`), an operator with no operand after it (`{= $a +}`, `{= $a->}`, `{= $a ?:}`), a filter with no name (`{$a|}`), an expression that starts with `*` or `,`, two operators where the second cannot be a sign, and `{if}`, `{foreach}`, `{=}` and the other tags that need an argument left without one. Only what both Latte 2.11.7 and 3.1.6 refuse is reported - a trailing comma at the end of a tag, an empty filter argument and a `{switch}` without a subject are taken by one of them and left alone. The grammar still reads the content of a tag leniently; the check runs over its tokens
- The path in `{asset 'vite:assets/app.ts'}` is a link to the file it names. The mapper name in front of the path is not part of the link, and a path that names no file of the project is left without a link and without a report — which directory a mapper serves is configuration the plugin does not read
- `{include parent}` is a link to the block of the same name in the template this one extends, whenever the chain of `{extends}` can be followed from the sources
- `{include parent}` and `{include this}` outside any block are reported, as is a parent block no template in the chain defines. The second one only where it can be proven: a template that names no parent of its own is given the presenter's layout while rendering, and its blocks then have a parent nothing in the sources can show
- The tag, filter and function registry answers according to the Latte version the templates are written for, read from `composer.lock` or forced on the settings page. What that changes is narrow: `{includeblock}` is gone under Latte 3, `{exitIf}`, `n:else` and `n:elseif` are absent under 2.11, and the filters and functions that arrived mid-line wait for the patch that brought them. Everything else is unaffected, on purpose — a project whose version could not be established keeps the whole registry, so does a tag the project defined itself, and so does anything the reference tables do not mention
- A tag or filter the project's Latte does not have is reported as such rather than as unknown: "Tag {includeblock} was removed in Latte 3.0", "Filter 'column' does not exist before Latte 3.1.3". "Unknown tag" sends the reader looking for a typo in a name they have spelled correctly; it stays for a name the reference tables never mention, and for a project whose version could not be established
- A notification when the project's Latte is newer than the versions the plugin has reference data for. It behaves as the newest line it knows, because a Latte released since adds and almost never removes, and refusing its constructions would report correct templates; the notification is what keeps that from happening silently. It names both versions and can be switched off
- Tags the Nette packages register and the plugin did not know: `{formContext}` and `{formClassPrint}` from nette/forms, `{linkBase}` from nette/application, `{preload}` and `n:asset?` from nette/assets
- The `asset()` and `tryAsset()` functions from nette/assets
- A type written in a tag is read even when it is not one plain class name. `never`, `false`, `void` and `resource` are the types they name rather than classes of that name; `integer`, `boolean`, `double`, `list` and `noreturn` are read as the types they are older names for, and `numeric` and `scalar` as the several types they stand for; the hyphenated PHPDoc types — `class-string`, `positive-int`, `non-empty-array` and the rest — are read as the type they narrow, with a nullable mark and an array-of written round them kept. A union or an intersection of names is read as all of the names in it, brackets included: `Foo|null`, `Foo|Bar`, `A&B` and `(A&B)|null` all resolve where they used to say nothing
- `self`, `static` and `parent` stay unresolved on purpose. They name the class a type is written inside and a template is not written inside one — Latte throws the type away rather than compiling it, and `{templateType}` says where the parameters come from, not what the template is part of
- The plain words are read in lower case only, as PHPDoc spells them. `Integer`, `Numeric`, `Scalar` and `List` are legal class names and go on naming the class
- `{varType Thing $a}` names the class `Thing`, as `{varType App\Model\Thing $a}` always has. A name with no backslash never became a class reference, so completion, navigation and every report that needs a type were missing for a class named without a namespace
- `array<int, Foo>` names the same type as `Foo[]` and now reads as one, so a `{foreach}` names its value, an index names what it indexes and a nested array gives an array one level shallower — none of which the generic spelling used to reach. `list<Foo>`, `iterable<Foo>` and the non-empty variants go the same way; a container that is a class keeps it, so `Collection<Foo>` is a `\Collection` with a `\Foo` one level under it; and a name that is a type rather than a container narrows to it, so `int<0, 100>` is an `int`. The container is never read as a class — `\array` and `\list` do not exist
- A `{foreach}` names its key where the type wrote one down. `array<string, Foo>` keys by a string and `array<int, array<string, Foo>>` keys the outer level by an int and the inner one by a string. A type that names no key — `Foo[]`, `array<Foo>` — goes on saying `mixed` rather than the `int` PHP would use there
- A line comment inside a tag is reported. Latte's tag lexer knows one comment and it is the block one; everywhere else `//` is two divisions and `#` is nothing at all, so Latte answers `Unexpected '/'` or `Unexpected '#'` and refuses the template. `{php}` is the exception and only halfway: PHP's own comment applies inside it, but Latte writes `; return get_defined_vars();` after the body, so a comment with nothing under it hides that too and the template compiles and then loses the variables the tag defined. Three spellings that look like a comment and are not — `//` beginning a destination, `#` in a destination, `#name` where an argument starts — are left alone

### Fixed

- A string left open in a tag is reported as "Unterminated string", and a tag left open until the end of the file only as "Malformed tag. Missing closing }". Both used to come with a list of about fifty internal token names ending in "expected"
- `{syntax}` is told which arguments the project's Latte takes rather than every argument any of them takes. `latte` is accepted through 2.11 and 3.0.1 and reported from 3.0.2; `single` is reported before 3.0.24 and accepted after. A project whose Latte could not be established, or one known only to a line the change happened inside, is told nothing — which is what "3.0" is, since the change is in the middle of it
- False "Closing tag matches nothing" warning on every template that closes an element its layout opened. The IDE checks the HTML view of a template as a whole document, and a template that declares a parent with `{layout}` or `{extends}` is not one — the layout opens the element and the block closes it, so each file on its own is unbalanced and meant to be. A template that names no parent, or names `{extends none}`, may well be a whole document and is checked as before, and everything else the check says about broken markup is untouched
- False "Term expected" and "Newline or semicolon expected" errors from CSS and JavaScript where a Latte tag stood: `style="background-color: #{$colour}"` left CSS a hash with no colour after it, and `<script>var {$name} = 1;</script>` left JavaScript `var  = 1`, where it read the brace that opened the tag as a binding of its own. A tag is taken out of the text those languages are given, and the hole is now filled wherever it would otherwise cut a token in half. Everything else the layer reports is untouched, including "Term expected" where the CSS really is wrong
- Editing `{contentType}` in an open template — writing one, changing it or deleting it — could leave the file unparsed with "refused to parse text with Language: XML" in the log, and nothing in the template highlighted from then on. A copy of the view provider made while the document was being committed worked the data language out from its own file, which halfway through an edit holds different text from the original's
- False "Invalid argument supplied to 'foreach'" warning for every `{foreach}` over a variable typed as a class, `\Traversable`, `\Generator` and `\ArrayObject` among them, and for the bare word `object` that the message itself allows. `foreach` takes an array, anything traversable and any object at all, so what is reported now is a value that is definitely none of those — a string, a number, a bool, a null
- False "A line comment is not part of a Latte tag" error for the anchor after a signal, `n:href="add!#tab"`. A destination is `[//] [[[module:]presenter:]action | signal! | this] [#fragment]`, so the hash there ends the link rather than starting a comment; one written away from the destination is still reported, because that shape has no room for a space
- `{varType void $a}` resolved to a class called `\void`, and so did `integer`, `list`, `resource`, `boolean`, `double`, `scalar`, `numeric`, `noreturn` and `empty`. Nothing is ever called any of those, so nothing was reported — but the type shown was wrong and the settings table called it an undefined class
- Changing anything on the Latte settings page left the templates already open reporting what the old settings said, until they were edited. It applies to which vendors the registry draws from as well as to the Latte version
- `{include parent}` and `{include this}` were offered as links to a file of that name. They name the block the tag is written in, and there is no such file
- Nineteen of the thirty-nine type shapes Latte accepts were reported as errors: `never`, `false`, `self`, `A&B`, `?Foo`, `array<string, mixed>`, `array{a: int}`, `int<0, 100>`, `1|2|3`, `class-string` and a class named without a namespace among them. Latte does not parse a type — it consumes a run of tokens and glues their text together, and 2.11 takes everything before the variable without a filter at all — so a plugin that models a type with a grammar is stricter than the language, which is what a false report is. A type the plugin cannot work out is now kept as written and answered as `mixed`
- `{var $a = $a}` followed by reading a member off `$a` ended the whole inspection pass with a `StackOverflowError`, so nothing in that template was reported at all. It is what a template writes when it narrows or re-declares a name it already has, and it was found on a real one. A type defined in terms of itself is answered as `mixed`
- `{var $a}`, `{var $a, $b}` and `{var App\Model\Thing $x}` were reported as missing an assignment. All three declare the name as null, in 2.11.7 and in 3.1.6 alike. `{var $a++}` and `{var $a--}` are still reported, because Latte 3 refuses them outright and Latte 2 compiles them into PHP that does not parse. The names a `{var}` declares now count as defined, which they did not before — those "undefined variable" warnings had been hidden underneath the error covering the whole tag
- `{var $a = 1 /* note */}` was reported as an error, and so was the same comment in `{do}`, `{=}`, `{php}` and `{if}`. The comment was never the problem; its end was. The tag lexer read a character at a time, so the `*/` before the closing brace left a `/` and a `}` side by side, which is how a tag says it closes itself — the tag ended in the middle of its own comment
- `Foo::make()` said nothing where `App\Model\Foo::make()` reported an undefined class, and `{if Foo::BAR}` was not read as PHP at all. An unqualified name is read as a class in the three places PHP settles on its own — in front of `::`, after `new`, after `instanceof` — and a tag whose PHP begins with one is read as PHP. Everywhere else a bare name stays a constant fetch, which is what `{PHP_EOL}` is
- A `{php}` block nested deeper than sixteen braces was lexed as if the tag had ended early. Seventeen levels is unusual PHP but it is correct PHP; the limit is 128 now, which no template and no hand-written block reaches. It stays, rather than going away, because without one four thousand braces take twelve seconds to parse
- A function call written inside another call — `{do $a = outer(inner($v))}` — was never looked at, so a name that does not exist went unreported there. A missing report rather than a false one, which is why nobody noticed
- False "Missing required filter parameters (1 required)" warning for `|date` written without a format. Latte's own `Filters::date($time, ?string $format = null)` defaults it, and the code after it tests for exactly that

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
