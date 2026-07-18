import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

plugins {
    id("net.neoforged.moddev") version "2.0.142"
}

val minecraftVersion = stonecutter.current.version

// Jar filename label: the exact Minecraft version. Unlike the fabric side (whose 1.21.10 build also covers
// 1.21.9), stable NeoForge only exists for 1.21.10 and 1.21.11 (never for 1.21.9), so each neoforge jar claims
// exactly the version it was built for.
val jarMcLabel = minecraftVersion

// Per-version pins, keyed on the active Minecraft version: [NeoForge version, NeoForge dependency range].
// NeoForge versions on the 1.21.x line embed the Minecraft minor/patch as their first two components
// (21.11.42 = MC 1.21.11 build 42). Both pins are the latest stable of their line.
val (neoForgeVersion, neoForgeDep) = when (minecraftVersion) {
    "1.21.11" -> listOf("21.11.42", "[21.11,21.12)")
    "1.21.10" -> listOf("21.10.64", "[21.10,21.11)")
    else -> error("Unconfigured Minecraft version: $minecraftVersion")
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set("builders-shulkers")
}

// Mirror the fabric build's client/common source-set split (Stonecutter versions sources per CONVENTIONAL
// source-set directory, so a real "client" set is what makes it pick up src/client). Loader-specific code lives
// in loader subpackages of the shared tree; the fabric packages are excluded at the source-set level.
sourceSets.main {
    java.exclude("**/shulkerinventory/fabric/**")
}
val clientSourceSet = sourceSets.create("client") {
    java.exclude("**/shulkerinventory/client/fabric/**")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

neoForge {
    version = neoForgeVersion
    validateAccessTransformers = true
    // The access transformer file name is version-keyed (the game-rule internals it opens moved between 1.21.10
    // and 1.21.11), so the convention path does not apply; point MDG at the matching variant explicitly. The jar
    // still ships it as META-INF/accesstransformer.cfg (see processResources).
    accessTransformers.from(rootProject.file(
            "src/main/resources/META-INF/accesstransformer_" + minecraftVersion.replace(".", "_") + ".cfg"))

    mods {
        // NeoForge mod ids must match [a-z][a-z0-9_]{1,63}: no hyphens, so the id is builders_shulkers here
        // (the fabric id keeps builders-shulkers). Identifier NAMESPACES do allow hyphens, so every network
        // payload and game rule keeps the builders-shulkers namespace and stays wire-compatible across loaders.
        register("builders_shulkers") {
            sourceSet(sourceSets.main.get())
            sourceSet(clientSourceSet)
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

// The client source set compiles against the same modding dependencies (NeoForge + patched Minecraft) as main.
neoForge.addModdingDependenciesTo(clientSourceSet)

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
    // The access transformer differs per version (the game-rule internals it opens moved between 1.21.10 and
    // 1.21.11), so the matching variant is renamed into place and the other dropped.
    rename("accesstransformer_" + minecraftVersion.replace(".", "_") + ".cfg", "accesstransformer.cfg")
    exclude(if (minecraftVersion == "1.21.11") "META-INF/accesstransformer_1_21_10.cfg" else "META-INF/accesstransformer_1_21_11.cfg")
}

// MC 1.21.x runs on Java 21; NeoForge's toolchain for this line expects 21 as well.
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

tasks.jar {
    archiveClassifier.set("neoforge-$jarMcLabel")
    from(clientSourceSet.output)
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
