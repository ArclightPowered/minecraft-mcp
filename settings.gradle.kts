pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForge" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.8"
}

rootProject.name = "minecraft-mcp"

// One source tree for every supported Minecraft version. common/, fabric/ and neoforge/ are Stonecutter
// branches; each version is a node (e.g. :neoforge:1.21.8) built from Stonecutter's processed copy of the
// branch sources. The sources in git are written for vcsVersion. Per-version dependency versions live in
// versions/<minecraft>.properties.
stonecutter {
    create(rootProject) {
        versions("1.21.1", "1.21.4", "1.21.8", "1.21.11", "26.1.2", "26.2", "26.3")
        vcsVersion = "1.21.11"
        branch("common")
        branch("fabric")
        branch("neoforge")
    }
}
