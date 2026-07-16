plugins {
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
}

val minecraftVersion = stonecutter.current.version

// Jar filename label: the 26.1.2 build covers the whole 26.1.x family, so its jar is named fabric-26.1.x to make that
// clear to users on any 26.1 patch; every other build is named by its exact version. Edit when a build's family changes.
val jarMcLabel = when (minecraftVersion) {
    "26.1.2" -> "26.1.x"
    else -> minecraftVersion
}

// Per-version pins, keyed on the active Minecraft version (one entry per build target):
// [Fabric Loader, Fabric API, Litematica (Modrinth version), malilib (Modrinth version), mod dependency range].
// The range is normally the patch-family of the active version (e.g. ~26.2 = >=26.2.0 <26.3.0). The 26.1.2 build
// widens to ~26.1 (>=26.1.0 <26.2.0) because it also runs on 26.1 and 26.1.1: those patches change no class the mod
// touches (verified by bytecode diff - only DetectedVersion/SharedConstants and an unrelated method-body change in
// ServerGamePacketListenerImpl, whose handleContainerClose signature is unchanged), so one jar covers the whole 26.1.x
// family. Each build still COMPILES against its exact minecraftVersion; the range only widens the runtime metadata.
val (loaderVersion, fabricApiVersion, litematicaVersion, malilibVersion, minecraftDep) = when (minecraftVersion) {
    "26.1.2" -> listOf("0.19.2", "0.150.0+26.1.2", "0.27.6", "0.28.6", "~26.1")
    "26.2" -> listOf("0.19.3", "0.152.1+26.2", "0.28.0", "0.29.0", "~26.2")
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

repositories {
    // Litematica + malilib (optional, compile-only interop dependency), pulled from the Modrinth maven. Scoped to the
    // maven.modrinth group so it is never consulted for anything else.
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Litematica interop: OPTIONAL, client-only, compile-only (never bundled nor required at runtime). The compat class
    // is loaded only when Litematica is present (isModLoaded gate + the suggests entry in fabric.mod.json). Compiled
    // against the Litematica/malilib build matching the active Minecraft version; the user provides them at runtime.
    "clientCompileOnly"("maven.modrinth:litematica:$litematicaVersion")
    "clientCompileOnly"("maven.modrinth:malilib:$malilibVersion")
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
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

// MC 26.x ships deobfuscated, so the `jar` task IS the final published jar. The mod VERSION stays clean
// (mod_version); only the classifier carries the loader and the Minecraft family label (jarMcLabel above), for clear
// multi-version naming (-> builders-shulkers-<mod_version>-fabric-<jarMcLabel>.jar, e.g. ...-fabric-26.1.x.jar).
tasks.jar {
    archiveClassifier.set("fabric-$jarMcLabel")
    from("LICENSE") {
        rename { "${it}_${rootProject.name}" }
    }
}
