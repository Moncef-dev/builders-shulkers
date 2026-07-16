import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

plugins {
    id("net.neoforged.moddev") version "2.0.142"
}

val minecraftVersion = stonecutter.current.version

// Jar filename label. Unlike the fabric jar (whose 26.1.2 build is labeled 26.1.x because it was verified against
// the whole 26.1 patch family), the neoforge jar is labeled with its exact Minecraft version: stable NeoForge only
// exists for 26.1.2 (26.1.0/26.1.1 never left beta), so a wider claim would be untested.
val jarMcLabel = minecraftVersion

// Per-version pins, keyed on the active Minecraft version: [NeoForge version, NeoForge dependency range].
// NeoForge versions embed the Minecraft version as their first three components (26.1.2.80 = MC 26.1.2 build 80).
// 26.1.2.80 is the latest stable; 26.2.0.15-beta is the latest for MC 26.2 (still beta upstream).
val (neoForgeVersion, neoForgeDep) = when (minecraftVersion) {
    "26.1.2" -> listOf("26.1.2.80", "[26.1.2,26.2)")
    "26.2" -> listOf("26.2.0.15-beta", "[26.2,26.3)")
    else -> error("Unconfigured Minecraft version: $minecraftVersion")
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set("builders-shulkers")
}

// The fabric buildscript keeps loom's client/common source-set split, which enforces at compile time that common
// code never references client code. This buildscript folds both into the single main source set instead: the
// enforcement already happens on every fabric build of the same source, and one source set keeps the ModDevGradle
// wiring simple. src/neoforge/resources carries the NeoForge-only files (neoforge.mods.toml).
sourceSets {
    main {
        java {
            srcDir("src/client/java")
        }
        resources {
            srcDir("src/client/resources")
            srcDir("src/neoforge/resources")
        }
    }
}

neoForge {
    version = neoForgeVersion

    mods {
        // NeoForge mod ids must match [a-z][a-z0-9_]{1,63}: no hyphens, so the id is builders_shulkers here
        // (the fabric id keeps builders-shulkers). Identifier NAMESPACES do allow hyphens, so every network
        // payload and game rule keeps the builders-shulkers namespace and stays wire-compatible across loaders.
        register("builders_shulkers") {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
        }
        // Dev convenience: named client runs with fixed usernames for local multiplayer testing.
        register("clientAlice") {
            client()
            programArguments.addAll("--username", "Alice")
        }
        register("clientBob") {
            client()
            programArguments.addAll("--username", "Bob")
        }
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "neoforge_dep" to neoForgeDep
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
    // fabric.mod.json lives in the shared resources; it must never ship in the neoforge jar.
    exclude("fabric.mod.json")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    archiveClassifier.set("neoforge-$jarMcLabel")
    from("LICENSE") {
        rename { "${it}_${rootProject.name}" }
    }
}

// Stonecutter generates the version-transformed sources that MDG's artifact task consumes.
tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
}

// Serialize createMinecraftArtifacts across the neoforge nodes: each run recompiles Minecraft, and letting the
// nodes do it in parallel overwhelms the machine.
interface NeoForgeMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", NeoForgeMutex::class.java) {
    maxParallelUsages.set(1)
}

tasks.named { it == "createMinecraftArtifacts" }.configureEach {
    usesService(mutex)
}
