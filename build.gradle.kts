import org.gradle.kotlin.dsl.register
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun cfg(key: String) = providers.gradleProperty(key)
fun env(key: String) = providers.environmentVariable(key)

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.changelog") version "2.4.0"
    id("org.jetbrains.qodana") version "0.1.13"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
}

group = cfg("pluginGroup").get()
version = cfg("pluginVersion").get()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

java {
    // PhpStorm 2026.2 ships class file version 69, so javac has to be a JDK 25 -
    // an older one cannot read the platform jars at all. The JetBrains Runtime
    // bundled with PhpStorm 2026.2 is one, so no separate JDK is needed locally.
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    // Compile against JDK 25 but emit bytecode the plugin's minimum IDE can load.
    options.release = 21

    // On by default javac reports "some input files use a deprecated API" and
    // names no file, so a new one hides among the ones already there. Not
    // -Werror: the platform deprecates things on its own schedule and CI going
    // red for that would teach everyone to ignore it.
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

// The Latte parser tests run on a mock application (ParsingTestCase) while the tests that
// exercise settings, highlighting and completion need a real one (BasePlatformTestCase). The two
// cannot share a JVM: whichever kind runs second sees the extension registrations the first kind
// left in the static per-language caches, and a Latte file then has a different number of PSI roots
// than the fixtures were written for. A JVM per class is the blunt fix, but it is the one that
// cannot be got wrong later by adding a test class of the other kind.
tasks.test {
    forkEvery = 1
}

sourceSets {
    main {
        java.srcDirs("src/main/gen")
    }
    test {
        java.srcDirs("src/main/gen")
    }
}

dependencies {
    intellijPlatform {
        cfg("intellij.localPath").orNull?.let { local(it) } ?: create(cfg("platformType"), cfg("platformVersion"))
        bundledPlugins(cfg("platformBundledPlugins").map { it.split(',') })
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

// Without a .idea directory the sandbox IDE opens the playground as a
// "non-directory based project" and logs that it cannot find the project config
// directory, once per start. The skeleton is generated rather than committed:
// it is IDE state, not source, and .gitignore already excludes it.
val preparePlaygroundProjectDir = tasks.register("preparePlaygroundProjectDir") {
    val ideaDir = layout.projectDirectory.dir("sandbox/playground/.idea").asFile
    // The custom tags, filters, functions and variables templates/custom-definitions.latte
    // uses. They live with the tests because that is where they are checked - see
    // LatteSettingsPersistenceTest - and are copied here so the playground opens with them
    // in place instead of asking for four settings pages to be filled in by hand.
    val playgroundSettings = layout.projectDirectory
        .file("src/test/resources/data/settings/PlaygroundLatteSettings.xml").asFile
    inputs.file(playgroundSettings)
    outputs.dir(ideaDir)
    doLast {
        ideaDir.mkdirs()
        // Never overwrite: once the IDE has been opened on the playground this
        // directory holds real settings, and the task runs before every start.
        mapOf(
            "misc.xml" to "<project version=\"4\" />\n",
            ".name" to "Latte playground\n",
            "latte.xml" to playgroundSettings.readText(),
        ).forEach { (name, content) ->
            val file = File(ideaDir, name)
            if (!file.exists()) {
                file.writeText(content)
            }
        }
    }
}

// Starts a sandbox IDE with sandbox/playground already open, so the plugin can be
// exercised by hand without setting a project up first. Separate from runIde, which
// keeps starting on an empty IDE. See that directory's README for what to look at.
intellijPlatformTesting {
    runIde {
        register("runIdeWithPlayground") {
            task {
                dependsOn(preparePlaygroundProjectDir)
                args(layout.projectDirectory.dir("sandbox/playground").asFile.absolutePath)
            }
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        name = cfg("pluginName")
        version = cfg("pluginVersion")

        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        changeNotes = provider {
            changelog.getAll().values.joinToString("\n") { changelog.renderItem(it, Changelog.OutputType.HTML) }
        }

        ideaVersion {
            sinceBuild = cfg("pluginSinceBuild")
        }
    }

    pluginVerification {
        ides {
            // Verify what the plugin actually declares support for. pluginSinceBuild
            // is 262 and there is no untilBuild, so the supported set is 2026.2 and
            // whatever ships after it - add new releases here as they appear.
            create(IntelliJPlatformType.PhpStorm, "2026.2")
        }
    }

    autoReload = true
}

grammarKit {
    jflexRelease = cfg("jflexRelease")
    grammarKitRelease = cfg("grammarKitRelease")
    // The IntelliJ core the generator itself runs on. Left unset it follows
    // platformVersion, and Grammar-Kit does not start on a recent platform
    // (NoClassDefFoundError: com.intellij.openapi.util.KeyedExtensionCollector).
    // Pinning it keeps code generation independent of the platform the plugin
    // is built against - the generated sources are plain Java either way.
    intellijRelease = cfg("grammarKitIntelliJRelease")
}

// The pinned core drags in transitive artifacts that are no longer published.
// Grammar-Kit never loads them - the plugin filters its own classpath down to a
// fixed list of platform jars - but Gradle still has to resolve the graph, so
// they are cut here. Scoped to the generator configuration; the plugin build
// resolves against platformVersion and is untouched.
configurations.named("grammarKitClassPath") {
    exclude(mapOf("group" to "ai.grazie.model"))
    exclude(mapOf("group" to "ai.grazie.spell"))
    exclude(mapOf("group" to "ai.grazie.nlp"))
    exclude(mapOf("group" to "ai.grazie.utils"))
    exclude(mapOf("group" to "com.jetbrains.infra"))
    exclude(mapOf("group" to "com.jetbrains.intellij.remoteDev"))
    exclude(mapOf("group" to "com.jetbrains.intellij.spellchecker"))
}

changelog {
    version = cfg("pluginVersion")
    repositoryUrl = cfg("pluginRepositoryUrl")
    path = file("CHANGELOG.md").canonicalPath
}

qodana {
    cachePath = provider { file(".qodana").canonicalPath }
    reportPath = provider { file("build/reports/inspections").canonicalPath }
    saveReport = true
    showReport = env("QODANA_SHOW_REPORT").map { it.toBoolean() }.getOrElse(false)
}

// Grammar-Kit filters its own classpath down to a fixed list of platform jar
// names, which drops kotlinx-coroutines - and the 2022.3 platform classes it
// loads reference it. Added back explicitly.
val grammarKitExtraClasspath = configurations.detachedConfiguration(
    dependencies.create("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4")
)

val generateLatteParser = tasks.register<GenerateParserTask>("generateLatteParser") {
    classpath(grammarKitExtraClasspath)
    sourceFile = File("src/main/java/dev/noctud/latte/parser/LatteParser.bnf")
    targetRootOutputDir = File("src/main/gen")
    pathToParser = "/dev/noctud/latte/parser/LatteParser.java"
    pathToPsiRoot = "/dev/noctud/latte/psi"
    purgeOldFiles = false
}

val generateLatteMacroContentLexer = tasks.register<GenerateLexerTask>("generateLatteMacroContentLexer") {
    sourceFile = File("src/main/java/dev/noctud/latte/lexer/grammars/LatteMacroContentLexer.flex")
    targetOutputDir = File("src/main/gen/dev/noctud/latte/lexer")
    purgeOldFiles = false
}

val generateLatteMacroLexer = tasks.register<GenerateLexerTask>("generateLatteMacroLexer") {
    sourceFile = File("src/main/java/dev/noctud/latte/lexer/grammars/LatteMacroLexer.flex")
    targetOutputDir = File("src/main/gen/dev/noctud/latte/lexer")
    purgeOldFiles = false
}

val generateLatteTopLexer = tasks.register<GenerateLexerTask>("generateLatteTopLexer") {
    sourceFile = File("src/main/java/dev/noctud/latte/lexer/grammars/LatteTopLexer.flex")
    targetOutputDir = File("src/main/gen/dev/noctud/latte/lexer")
    purgeOldFiles = false
}

val generateLattePhpLexer = tasks.register<GenerateLexerTask>("generateLattePhpLexer") {
    sourceFile = File("src/main/java/dev/noctud/latte/lexer/grammars/LattePhpLexer.flex")
    targetOutputDir = File("src/main/gen/dev/noctud/latte/lexer")
    purgeOldFiles = false
}

tasks {
    generateLexer.configure { enabled = false }
    generateParser.configure { enabled = false }

    // The corpus lives outside this repository and is selected by environment
    // variables. Gradle does not track those, so declare them as inputs -
    // otherwise a run over a different corpus silently reuses cached results.
    // Every variable the corpus tests read belongs here, not only the directory:
    // a measurement asked for under a different Latte line, over a different
    // number of files, or into a different report is a different measurement,
    // and one that does not run looks exactly like one that changed nothing.
    withType<Test> {
        inputs.property("latteCorpusDir", providers.environmentVariable("LATTE_CORPUS_DIR").orElse(""))
        inputs.property("latteCorpusReport", providers.environmentVariable("LATTE_CORPUS_REPORT").orElse(""))
        inputs.property("latteCorpusLimit", providers.environmentVariable("LATTE_CORPUS_LIMIT").orElse(""))
        inputs.property("latteCorpusVersion", providers.environmentVariable("LATTE_CORPUS_VERSION").orElse(""))
        inputs.property(
            "latteCorpusInspectionReport",
            providers.environmentVariable("LATTE_CORPUS_INSPECTION_REPORT").orElse("")
        )

        // Three tests read the playground out of the working tree instead of out of test
        // resources: the templates and the PHP they resolve against. Gradle sees neither, so an
        // edited template reused the cached green result of the run before it - the failure mode
        // these tests exist to prevent. Declaring them makes an edit re-run the suite.
        inputs.dir(layout.projectDirectory.dir("sandbox/playground/templates"))
            .withPropertyName("playgroundTemplates")
        inputs.dir(layout.projectDirectory.dir("sandbox/playground/app"))
            .withPropertyName("playgroundApp")
    }

    // The version-stamped reference tables under docs/ are the closest thing to the Latte
    // language this repository holds: they are read out of the engine's own registration code at
    // each tag. The plugin needs the same facts at runtime, and the one thing that must not happen
    // is a second copy of them maintained by hand - two lists of what exists in which version
    // drift, and the drift is invisible until somebody is told a filter does not exist.
    //
    // So the tables themselves are what ships. Nothing is transcribed, nothing is generated into
    // source: the file the documentation shows is the file the plugin reads.
    processResources {
        from(layout.projectDirectory.dir("docs/latte")) {
            include("reference-tags.md", "reference-filters.md", "reference-functions.md")
            into("latte-reference")
        }
    }

    // The sources these produce land in src/main/gen, which is a source directory
    // of both source sets, so generation has to happen before anything compiles.
    withType<JavaCompile> {
        dependsOn(
            generateLatteMacroContentLexer,
            generateLatteMacroLexer,
            generateLatteTopLexer,
            generateLattePhpLexer,
            generateLatteParser
        )
    }

    wrapper {
        gradleVersion = cfg("gradleVersion").get()
    }


    publishPlugin {
        token = env("PLUGIN_PUBLISH_TOKEN")
    }
}
