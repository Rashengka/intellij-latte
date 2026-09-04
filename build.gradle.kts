import org.gradle.kotlin.dsl.register
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun cfg(key: String) = providers.gradleProperty(key)
fun env(key: String) = providers.environmentVariable(key)

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.9.0"
    id("org.jetbrains.changelog") version "2.4.0"
    id("org.jetbrains.qodana") version "0.1.13"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
    kotlin("jvm") version "2.2.10"
}

group = cfg("pluginGroup").get()
version = cfg("pluginVersion").get()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(17)
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
        plugins(cfg("platformPlugins").map { it.split(',') })
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
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
            // The versions the CI workflow verifies, plus the one the plugin is actually run on -
            // crashes get reported from 2026.2, which nothing verified before.
            ide(IntelliJPlatformType.PhpStorm, "2023.1")
            ide(IntelliJPlatformType.PhpStorm, "2024.1")
            ide(IntelliJPlatformType.PhpStorm, "2025.1")
            ide(IntelliJPlatformType.PhpStorm, "2026.2")
        }
    }

    autoReload = true
}

grammarKit {
    jflexRelease = cfg("jflexRelease")
    grammarKitRelease = cfg("grammarKitRelease")
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

val generateLatteParser = tasks.register<GenerateParserTask>("generateLatteParser") {
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
    // of both source sets. Wiring them to JavaCompile as well as KotlinCompile
    // matters: this project has no Kotlin sources, so compileKotlin is NO-SOURCE
    // and a change to a .flex or .bnf file would otherwise never be regenerated -
    // the build stays green while measuring the previously generated lexer.
    val generateSources = listOf(
        generateLatteMacroContentLexer,
        generateLatteMacroLexer,
        generateLatteTopLexer,
        generateLattePhpLexer,
        generateLatteParser
    )

    withType<KotlinCompile> {
        dependsOn(generateSources)
    }

    withType<JavaCompile> {
        dependsOn(generateSources)
    }

    wrapper {
        gradleVersion = cfg("gradleVersion").get()
    }

    publishPlugin {
        token = env("PLUGIN_PUBLISH_TOKEN")
    }
}
