import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

plugins {
    id("net.neoforged.moddev") version "2.0.142"
}

val minecraftVersion = stonecutter.current.version

// Jar filename label, mirroring the fabric jar's coverage: the 26.1.2 build is labeled 26.1.x and its dependency
// range accepts the whole 26.1 NeoForge family - stable NeoForge only exists for 26.1.2, but 26.1.0/26.1.1 have
// beta NeoForge builds players do run, and the mod classes were verified byte-compatible across the 26.1 patch
// family (same verification as the fabric jar).
val jarMcLabel = when (minecraftVersion) {
    "26.1.2" -> "26.1.x"
    else -> minecraftVersion
}

// Per-version pins, keyed on the active Minecraft version: [NeoForge version, NeoForge dependency range].
// NeoForge versions embed the Minecraft version as their first three components (26.1.2.80 = MC 26.1.2 build 80).
// 26.1.2.80 is the latest stable; 26.2.0.15-beta is the latest for MC 26.2 (still beta upstream).
val (neoForgeVersion, neoForgeDep) = when (minecraftVersion) {
    "26.1.2" -> listOf("26.1.2.80", "[26.1,26.2)")
    "26.2" -> listOf("26.2.0.15-beta", "[26.2,26.3)")
    else -> error("Unconfigured Minecraft version: $minecraftVersion")
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set("builders-shulkers")
}

// Mirror the fabric build's client/common source-set split (Stonecutter versions sources per CONVENTIONAL
// source-set directory, so a real "client" set is what makes it pick up src/client). Loader-specific code lives
// in loader subpackages of the shared tree; the fabric packages (and the fabric-only Litematica interop under
// client/compat) are excluded at the source-set level.
sourceSets.main {
    java.exclude("**/shulkerinventory/fabric/**")
}
val clientSourceSet = sourceSets.create("client") {
    java.exclude("**/shulkerinventory/client/fabric/**", "**/shulkerinventory/client/compat/**")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

neoForge {
    version = neoForgeVersion
    validateAccessTransformers = true

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
