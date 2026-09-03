plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "com.cursoragent"
version = "0.1.0-SNAPSHOT"

// Self-installing safety net: point this repo's git hooks at the versioned
// .githooks/ directory (unversioned hooks under .git/hooks/ never survive a
// fresh clone) so the pre-push test gate is active for every contributor/agent
// without a manual setup step to forget. Runs on every Gradle invocation but is
// a single fast, idempotent `git config` call.
if (file(".githooks").exists()) {
    try {
        exec {
            commandLine("git", "config", "core.hooksPath", ".githooks")
        }
    } catch (e: Exception) {
        logger.warn("Could not configure git core.hooksPath: ${e.message}")
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.commonmark:commonmark:0.30.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")

    intellijPlatform {
        // Always local(): the intellijPlatform Gradle plugin's own androidStudio()
        // dependency resolution (v2.10.5) constructs a broken download URL --
        // verified directly, the artifact exists and downloads fine by hand, but
        // Gradle's own resolution 404s on it. CI works around this by downloading
        // Android Studio itself (see .github/workflows/ci.yml) and passing the
        // extracted path as -PplatformPath, reusing this exact same code path
        // instead of maintaining a second, broken resolution mechanism.
        local(providers.gradleProperty("platformPath"))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = "Cursor Agent"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "261"
        }
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}
