plugins {
    eclipse
    idea
    `maven-publish`
    id("net.minecraftforge.gradle") version "6.0.+"
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
}

import java.time.Instant

val mod_version: String by project
val mod_group_id: String by project
val mod_id: String by project
val mod_license: String by project
val mod_authors: String by project
val minecraft_version: String by project
val minecraft_version_range: String by project
val forge_version: String by project
val forge_version_range: String by project
val loader_version_range: String by project
val mapping_channel: String by project
val mapping_version: String by project
val kotlin_for_forge_version: String by project
val kubejs_version: String by project
val cloth_config_version: String by project
val puzzleslib_version: String by project

// NOTE: Keep Chinese strings hardcoded here instead of reading from gradle.properties.
// Reason: java.util.Properties spec requires gradle.properties to be read as ISO-8859-1,
// which corrupts Chinese characters. This is the root cause of the mojibake (乱码) in
// mods.toml (e.g. "ä¸é¶åµåµå°¸æ«æ¥çå­mod" instead of "七零喵僵尸末日生存mod").
val mod_name = "七零喵僵尸末日生存mod"
val mod_description = "七零喵僵尸末日生存mod (Kotlin+Java+KubeJS重构版) - 基于开源模组整合的末日生存mod"

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

minecraft {
    mappings(mapOf("channel" to mapping_channel, "version" to mapping_version))
    copyIdeResources = true

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            property("file.encoding", "UTF-8")
            property("sun.stdout.encoding", "UTF-8")
            property("sun.stderr.encoding", "UTF-8")
            mods {
                create(mod_id) {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDir("src/main/java")
            srcDir("src/main/kotlin")
        }
        resources {
            srcDir("src/generated/resources")
            srcDir("src/main/resources")
            srcDir("src/main/kubejs")
        }
    }
}

repositories {
    mavenCentral()
    flatDir {
        dirs("src/main/libs")
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:${minecraft_version}-${forge_version}")

    // All mod JARs from libs directory
    // KotlinForForge and KubeJS are runtime-only (not referenced directly in code)
    implementation(fileTree(mapOf("dir" to "src/main/libs", "include" to listOf("*.jar"))))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjvm-default=all", "-Xskip-metadata-version-check")
    }
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filteringCharset = "UTF-8"
    val replaceProperties = mapOf(
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "forge_version" to forge_version,
        "forge_version_range" to forge_version_range,
        "loader_version_range" to loader_version_range,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_license" to mod_license,
        "mod_version" to mod_version,
        "mod_authors" to mod_authors,
        "mod_description" to mod_description,
    )
    inputs.properties(replaceProperties)

    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(replaceProperties + mapOf("project" to project))
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(mapOf(
            "Specification-Title" to mod_id,
            "Specification-Vendor" to mod_authors,
            "Specification-Version" to "1",
            "Implementation-Title" to project.name,
            "Implementation-Version" to archiveVersion,
            "Implementation-Vendor" to mod_authors,
            "Implementation-Timestamp" to Instant.now().toString(),
            "FMLAT" to "qlmzombie.mixins.json",
        ))
    }

    // Embed dependency JARs inside our jar. Forge's JarInJarDependencyLocator
    // will discover them during mod scanning (before dependency checks), so
    // kotlinforforge/kubejs/cloth-config are available from the very first launch.
    // IMPORTANT: We do NOT extract kotlin class files into the jar root, because
    // that would cause a ResolutionException when kotlinforforge is also loaded
    // (both modules would export the same kotlinx.coroutines.* packages).
    // Instead, kotlinforforge is kept as a full nested JAR and provides kotlin-stdlib.
    from("src/main/libs") {
        into("libs")
        include("*.jar")
        exclude("README.txt")
        exclude("qlmzombie*.jar")
        exclude("serveradmin*")
        exclude("player2-*")
        exclude("vanilla_server*")
        exclude("*jython*")
        exclude("*graal*")
        exclude("*polyglot*")
        exclude("*[Python]*")
    }

    finalizedBy("reobfJar")
}