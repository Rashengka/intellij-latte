Latte for PhpStorm and IntelliJ IDEA
=========================================
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/24218-latte-support.svg?label=marketplace)](https://plugins.jetbrains.com/plugin/24218-latte-support)
[![Build](https://img.shields.io/github/actions/workflow/status/noctud/intellij-latte/build.yaml?branch=main)](https://github.com/noctud/intellij-latte/actions)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
[![Discord](https://img.shields.io/badge/discord-join-5865F2?logo=discord&logoColor=white)](https://discord.noctud.dev)

<!-- Plugin description -->
Provides comprehensive support for the [Latte](https://latte.nette.org) templating engine in PhpStorm and IntelliJ IDEA. Includes syntax highlighting, code completion, type-aware references via [{templateType}](https://latte.nette.org/type-system#toc-templatetype), navigation from `{link}` to presenter actions and `{control}` to components, live inspections, refactoring across Latte and PHP files, and configurable custom tags, filters, and functions per project.

If you have any problems with the plugin, [create an issue](https://github.com/noctud/intellij-latte/issues/new/choose) or join the [Noctud Discord](https://discord.noctud.dev).
<!-- Plugin description end -->

![Example](.github/showcase.gif)


Notice
------------
This plugin is a fork of the [original free plugin](https://github.com/nette-intellij/intellij-latte). This fork has been created as another free plugin, since the code completion feature was removed from the original free plugin.


Installation
------------
Settings → Plugins → Browse repositories → Find "Latte Support" → Install Plugin → Apply


Installation from .jar file
------------
Download the `instrumented.jar` file from the [latest release](https://github.com/noctud/intellij-latte/releases) or the latest successful [GitHub Actions build](https://github.com/noctud/intellij-latte/actions)


Supported Features
------------------

* Syntax highlighting and code completion for PHP in Latte files
* Type-aware references to classes, methods, properties, and constants (via [{templateType}](https://latte.nette.org/type-system#toc-templatetype))
* Link references between `{link}` macros and presenter action methods
* Control component references from `{control}` tags to registered components
* File path references and missing file detection in `{include}` and `{import}` macros
* Refactoring support (rename classes, methods, variables across Latte and PHP)
* Live inspections (undefined variables, unknown classes/methods, deprecated tags, modifier issues, iterable types)
* Code folding, brace matching, and structure view
* Configurable custom tags, filters, functions, and variables per project
* Live templates for common Latte constructs
* Support for `{syntax off}`, `{syntax double}`, and `n:syntax` attributes


Building
------------

```sh
./gradlew build
```

The build needs a JetBrains Runtime 21 as the Gradle JVM; a plain JDK 17 does not
build the platform dependencies.

The lexers and the parser are generated from `src/main/java/dev/noctud/latte/lexer/grammars/*.flex`
and `src/main/java/dev/noctud/latte/parser/LatteParser.bnf` into `src/main/gen`, which
is not checked in. The generator tasks run as part of `compileJava`, so a change to a
grammar file takes effect on the next build without a separate step.


Testing
------------

```sh
./gradlew test
```

`CorpusParseTest` parses every `.latte` file in a directory of real-world templates and
reports the files the parser rejects. The corpus is not part of this repository; the test
is skipped unless `LATTE_CORPUS_DIR` points at one. `LATTE_CORPUS_REPORT` chooses where
the report is written. Both are declared as task inputs, so a run over a different corpus
is not served from the build cache.

```sh
LATTE_CORPUS_DIR=/path/to/templates ./gradlew test --tests '*CorpusParseTest*'
```

Compatibility is checked with the IntelliJ Plugin Verifier:

```sh
./gradlew verifyPlugin
```


Testing in sandbox IDE
------------

```sh
./gradlew runIde
```
