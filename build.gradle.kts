import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.cursoragent"
version = providers.gradleProperty("pluginVersion").get()
require(version.toString().matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?"))) {
    "pluginVersion must be a numeric major.minor.patch with an optional qualifier"
}

// Self-installing safety net: point this repo's git hooks at the versioned
// .githooks/ directory (unversioned hooks under .git/hooks/ never survive a
// fresh clone) so the pre-push test gate is active for every contributor/agent
// without a manual setup step to forget. Runs on every Gradle invocation but is
// a single fast, idempotent `git config` call.
if (file(".githooks").exists()) {
    try {
        providers.exec {
            commandLine("git", "config", "core.hooksPath", ".githooks")
        }.result.get()
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
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.commonmark:commonmark:0.30.0")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    intellijPlatform {
        // Local use must be explicitly enabled; a user-wide platformPath cannot
        // silently change the SDK used by the default build.
        if (providers.gradleProperty("useLocalPlatform").orNull == "true") {
            local(providers.gradleProperty("platformPath"))
        } else {
            androidStudio("2026.1.1.8")
        }
        bundledPlugins("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Bundled) // Exercise the resolved SDK's native Document/Undo behavior.
        pluginVerifier("1.410")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        languageVersion.set(KotlinVersion.KOTLIN_2_3)
        apiVersion.set(KotlinVersion.KOTLIN_2_3)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Cursor in Android Studio"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "261.23567.138"
            untilBuild = "261.*"
        }
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}

// Validate the resolved SDK, including explicit local overrides, before producing
// any classes/resources or assembling a distribution. There is no release bypass.
val sdkBuild = providers.provider {
    val info = intellijPlatform.productInfo
    "${info.productCode}-${info.buildNumber.removePrefix("${info.productCode}-")}"
}
val verifyBuildSdk = tasks.register("verifyBuildSdk") {
    group = "verification"
    description = "Verify the resolved oldest supported Android Studio SDK."
    inputs.property("sdkBuild", sdkBuild)
    doLast {
        check(sdkBuild.get() == "AI-261.23567.138.2611.15503007") {
            "Compile SDK must be Quail 1 2026.1.1.8 (AI-261.23567.138.2611.15503007); resolved ${sdkBuild.get()}"
        }
        logger.lifecycle("Compile SDK verified: ${sdkBuild.get()}")
    }
}
tasks.matching { it.name in setOf("compileKotlin", "compileJava", "processResources", "prepareSandbox", "buildPlugin") }.configureEach {
    dependsOn(verifyBuildSdk)
}

// Build identity belongs to the packaged binary, never the IDE's current checkout.
fun buildGitValue(vararg arguments: String): String? = runCatching {
    if (!rootDir.resolve(".git").exists()) return@runCatching null
    val result = providers.exec {
        workingDir(rootDir)
        commandLine("git", *arguments)
        isIgnoreExitValue = true
    }
    if (result.result.get().exitValue == 0) result.standardOutput.asText.get().trim() else null
}.getOrNull()

val generateBuildIdentity = tasks.register<WriteProperties>("generateBuildIdentity") {
    destinationFile.set(layout.buildDirectory.file("generated/diagnostics/cursor-agent-build.properties"))
    property("source.commit", providers.provider { buildGitValue("rev-parse", "HEAD").orEmpty() })
    property("source.state", providers.provider {
        when (buildGitValue("status", "--porcelain=v1", "--untracked-files=normal")) {
            null -> "unknown"
            "" -> "clean"
            else -> "dirty"
        }
    })
    property("plugin.version", project.version.toString())
    property("sdk.build", sdkBuild)
    property("jvm.target", "21")
}

tasks.processResources {
    from(generateBuildIdentity)
}

// Verification consumes an existing, sealed archive. No buildPlugin dependency:
// scripts/workflow/plugin_compatibility.py binds the ZIP, SDK and bundled runtime.
tasks.verifyPlugin {
    archiveFile.set(layout.file(providers.gradleProperty("verificationArchive").map { file(it) }))
    ides.setFrom(providers.gradleProperty("verificationIdePath").map { file(it) })
    runtimeDirectory.set(layout.dir(providers.gradleProperty("verificationRuntime").map { file(it) }))
    useBundledRuntime.set(false) // Explicit per-job bundled JBR; never a JAVA_HOME fallback.
    offline.set(true) // Resolve the target distribution, not mutable Marketplace dependencies.
    doFirst {
        systemProperty("plugin.verifier.home.dir", verificationReportsDirectory.get().asFile.resolveSibling("verifier-cache"))
    }
    verificationReportsDirectory.set(layout.dir(providers.gradleProperty("verificationReports").map { file(it) }))
    failureLevel.set(listOf(
        FailureLevel.COMPATIBILITY_PROBLEMS,
        FailureLevel.COMPATIBILITY_WARNINGS,
        FailureLevel.MISSING_DEPENDENCIES,
        FailureLevel.INVALID_PLUGIN,
        FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
        FailureLevel.OVERRIDE_ONLY_API_USAGES,
        FailureLevel.NON_EXTENDABLE_API_USAGES,
    ))
}
