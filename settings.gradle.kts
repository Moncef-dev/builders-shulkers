pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
}

stonecutter {
    kotlinController = true
    create(rootProject) {
        // 1.21.x family branch. Kept on its own branch from the 26.x family because 1.21.x is the last obfuscated
        // line: its fabric build needs the loom -remap plugin variant + official Mojang mappings (see
        // build.fabric.gradle.kts), which the deobfuscated 26.x family does not use.
        //
        // One build node per (Minecraft version, loader), each applying its loader's own buildscript
        // (build.<loader>.gradle.kts). The node id "<mc>-<loader>" is MAPPED to its logical Minecraft version:
        // without the mapping, the "-<loader>" suffix would parse as a semver pre-release and skew version
        // comparisons in the controller's replacements.
        fun target(mc: String, vararg loaders: String) {
            for (loader in loaders) version("$mc-$loader", mc).buildscript("build.$loader.gradle.kts")
        }
        target("1.21.11", "fabric", "neoforge")
        target("1.21.10", "fabric", "neoforge")
        // The source is kept in its 1.21.11 form (the newest target); this is the node the tree is reset to
        // before a commit.
        vcsVersion = "1.21.11-fabric"
    }
}

rootProject.name = "builders-shulkers"
