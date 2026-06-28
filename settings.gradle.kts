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
        // 1.21.x family branch. Kept on its own branch from the 26.x family because 1.21.x is the last obfuscated
        // line: it needs loom 1.14 + official Mojang mappings (see build.gradle.kts), which cannot share a loom
        // version with the deobfuscated 26.x family (loom 1.16). First entry is the vcsVersion (the form committed /
        // reset to before a commit). The source is kept in its 1.21.11 form.
        version("1.21.11", "1.21.11")
    }
    create(rootProject)
}

rootProject.name = "builders-shulkers"
