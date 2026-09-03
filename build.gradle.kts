plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "com.cursoragent"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")

    intellijPlatform {
        local(providers.gradleProperty("platformPath"))
    }
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
