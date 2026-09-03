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
        // CI has no local Android Studio install, so it downloads one instead of
        // using the machine-specific platformPath every local dev setup relies on.
        // GitHub Actions sets CI=true by convention.
        if (System.getenv("CI") == "true") {
            androidStudio(providers.gradleProperty("ciAndroidStudioVersion").getOrElse("2026.1.1.1"))
        } else {
            local(providers.gradleProperty("platformPath"))
        }
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
