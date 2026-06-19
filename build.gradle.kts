plugins {
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    `maven-publish`
}

val minecraftVersion = stonecutter.current.version

// Per-version dependency pins, keyed on the active Minecraft version (one entry per supported version):
// [Fabric Loader, Fabric API, Litematica (Modrinth version), malilib (Modrinth version)]. The mod dependency range is
// the patch-family of the active version (e.g. ~26.2 = >=26.2 <26.3).
val minecraftDep = "~$minecraftVersion"
val (loaderVersion, fabricApiVersion, litematicaVersion, malilibVersion) = when (minecraftVersion) {
    "26.1.2" -> listOf("0.19.2", "0.150.0+26.1.2", "0.27.6", "0.28.6")
    "26.2" -> listOf("0.19.3", "0.152.1+26.2", "0.28.0", "0.29.0")
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
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

// MC 26.x ships deobfuscated, so the `jar` task IS the final published jar. The mod VERSION stays clean
// (mod_version); only the classifier carries the loader and MC version, for clear multi-version naming
// (-> builders-shulkers-<mod_version>-fabric-<mc>.jar).
tasks.jar {
    archiveClassifier.set("fabric-$minecraftVersion")
    from("LICENSE") {
        rename { "${it}_${rootProject.name}" }
    }
}
