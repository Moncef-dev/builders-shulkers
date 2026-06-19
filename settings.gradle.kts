pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"
    shared {
        // First entry is the vcsVersion (the form committed / reset to before a commit). The source is kept in
        // its 26.2 form, so 26.2 is first.
        version("26.2", "26.2")
        version("26.1.2", "26.1.2")
    }
    create(rootProject)
}

rootProject.name = "builders-shulkers"
