plugins {
    // 1.21.x is the last obfuscated Minecraft line. Modern loom ships two plugin variants: "fabric-loom" (for the
    // deobfuscated 26.x line, used on that branch) and "fabric-loom-remap" (obfuscation-aware: it remaps the compiled
    // classes back to obfuscated names and accepts official Mojang mappings). 1.21.x needs the -remap variant; using
    // plain "fabric-loom" fails with "Cannot use Mojang mappings in a non-obfuscated environment". The -remap variant
    // is what matters, NOT the loom version: we use loom 1.16 (same as the 26.x branch) because the 1.21.11 Litematica
    // and malilib builds were themselves built with loom 1.16.3, and loom refuses to depend on a mod built with a
    // newer loom than itself - so loom 1.14 was rejected. (The blog's "use loom 1.14" advice predates loom 1.16.)
    id("net.fabricmc.fabric-loom-remap") version "1.16-SNAPSHOT"
}

val minecraftVersion = stonecutter.current.version

// Jar filename label. The 1.21.10 build is byte-compatible with 1.21.9 (verified by bytecode diff over every class the
// mod touches), so one jar covers both and carries a range label; 1.21.11 is a separate build (its render rework
// diverges). Edit when a build's family changes.
val jarMcLabel = when (minecraftVersion) {
    "1.21.10" -> "1.21.9-1.21.10"
    else -> minecraftVersion
}

// Per-version pins, keyed on the active Minecraft version (one entry per build target):
// [Fabric Loader, Fabric API, mod dependency range]. The range is the patch-family of the active version
// (e.g. ~1.21.11 = >=1.21.11 <1.22.0). Each build COMPILES against its exact minecraftVersion. (Litematica/malilib
// interop is deferred on this branch; it will be re-added with its own dependency pins when re-tested.)
val (loaderVersion, fabricApiVersion, minecraftDep) = when (minecraftVersion) {
    "1.21.11" -> listOf("0.19.3", "0.141.4+1.21.11", "~1.21.11")
    // 1.21.10 build, widened to also cover 1.21.9: every class the mod touches is byte-identical between 1.21.9 and
    // 1.21.10 (verified by bytecode diff), and our fabric-api usage exists in 1.21.9's last build (0.134.1), so the
    // 1.21.10 jar runs on both. Compiles against 1.21.10; the range only widens the runtime metadata.
    "1.21.10" -> listOf("0.19.3", "0.138.4+1.21.10", ">=1.21.9 <1.21.11")
    else -> error("Unconfigured Minecraft version: $minecraftVersion")
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set("builders-shulkers")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("builders-shulkers") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }

    // Loader-specific code lives in subpackages of the SHARED source tree (Stonecutter only versions the
    // conventional source-set directories, so separate per-loader dirs would be silently ignored); each
    // loader's buildscript excludes the other loader's packages at the source-set level.
    sourceSets["main"].java.exclude("**/shulkerinventory/neoforge/**")
    sourceSets["client"].java.exclude("**/shulkerinventory/neoforge/**")

    // Dev convenience: named client runs with fixed usernames for local multiplayer testing against runServer.
    runs {
        create("clientAlice") {
            inherit(getByName("client"))
            programArgs("--username", "Alice")
        }
        create("clientBob") {
            inherit(getByName("client"))
            programArgs("--username", "Bob")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    // 1.21.x is obfuscated: we develop against Mojang's official mappings (Mojmap), and loom remaps the compiled
    // classes back to obfuscated names in remapJar. Our source already uses Mojmap names (shared with the 26.x branch),
    // so this is the matching mapping set. Without this line loom would leave the jar fully obfuscated.
    "mappings"(loom.officialMojangMappings())
    // On obfuscated versions, Fabric Loader / Fabric API ship in intermediary mappings, so they MUST go through the
    // mod* configurations: loom then remaps them to our Mojmap mappings. (The 26.x branch uses plain implementation
    // because those deobfuscated artifacts are already in Mojmap and need no remap.) Using implementation here leaks
    // intermediary types (class_XXXX) into the API signatures and breaks compilation.
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "minecraft_dep" to minecraftDep
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
    // NeoForge-only resources (shared resource tree, same reason as the source-set excludes above).
    exclude("META-INF/neoforge.mods.toml", "META-INF/accesstransformer.cfg")
}

// MC 1.21.11 runs on Java 21 (its classes are Java 21 bytecode), so we target 21. Compiling with --release 21 on
// JDK 25 is fine; runClient needs a Java 21 runtime, located via the toolchain below.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// MC 1.21.x is obfuscated, so loom remaps: remapJar (not jar) produces the final, runnable, published jar. The mod
// VERSION stays clean (mod_version); only the classifier carries the loader and the Minecraft family label (jarMcLabel
// above), for clear multi-version naming (-> builders-shulkers-<mod_version>-fabric-<jarMcLabel>.jar).
tasks.named<org.gradle.api.tasks.bundling.AbstractArchiveTask>("remapJar") {
    archiveClassifier.set("fabric-$jarMcLabel")
}

// LICENSE is added to the dev jar; remapJar carries it through into the final jar.
tasks.jar {
    from("LICENSE") {
        rename { "${it}_${rootProject.name}" }
    }
}
