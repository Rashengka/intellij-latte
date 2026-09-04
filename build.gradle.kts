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

// Starts a sandbox IDE with sandbox/playground already open, so the plugin can be
// exercised by hand without setting a project up first. Separate from runIde, which
// keeps starting on an empty IDE. See that directory's README for what to look at.
intellijPlatformTesting {
    runIde {
        register("runIdeWithPlayground") {
            task {
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
    withType<Test> {
        inputs.property("latteCorpusDir", providers.environmentVariable("LATTE_CORPUS_DIR").orElse(""))
        inputs.property("latteCorpusReport", providers.environmentVariable("LATTE_CORPUS_REPORT").orElse(""))
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
