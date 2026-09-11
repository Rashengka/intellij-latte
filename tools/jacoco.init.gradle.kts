// Line coverage for reviewing the tests.
//
// Applied from outside the build on purpose: coverage is an input to a review of the tests - the
// classes and methods no test reaches at all - and not a gate, so build.gradle.kts does not carry it
// and CI does not run it. Only a zero means anything here; a high number proves nothing.
//
//   ./gradlew --no-daemon --no-build-cache --rerun-tasks --no-configuration-cache \
//     --init-script tools/jacoco.init.gradle.kts test jacocoTestReport
//
// The report lands in build/reports/jacoco/test/ (html, xml, csv). It can be rebuilt from the
// existing build/jacoco/test.exec without running the suite again: add -x test.
allprojects {
    apply(plugin = "jacoco")

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.14"
    }

    tasks.withType<Test>().configureEach {
        extensions.configure<JacocoTaskExtension> {
            // Only the plugin's classes - the platform jars are not ours to measure.
            includes = listOf("dev.noctud.latte.*")
            // The platform test framework loads the plugin through its own class loaders, which
            // define classes without a CodeSource, and JaCoCo skips those by default. Without this
            // the exec file holds a session header per fork and not a single class, and the report
            // shows the whole plugin at zero - which reads exactly like "nothing is tested".
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
            // forkEvery = 1: every fork appends to the same file (Gradle 9 always appends).
        }
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            csv.required.set(true)
            html.required.set(true)
        }
    }
}

// The test JVM loads the classes the IntelliJ plugin instrumented (@NotNull checks, GUI forms), not
// javac's output. JaCoCo matches classes by a checksum of their bytes, and with both directories in
// the report it stops at the first form class: "Can't add different class with same name". Set
// here, after every plugin has configured the task - set in configureEach it was put back to javac's
// output by something that ran later.
gradle.projectsEvaluated {
    allprojects {
        tasks.withType<JacocoReport>().configureEach {
            classDirectories.setFrom(layout.buildDirectory.dir("instrumented/instrumentCode"))
            doFirst { println("[coverage] classDirectories=" + classDirectories.files) }
        }
    }
}
