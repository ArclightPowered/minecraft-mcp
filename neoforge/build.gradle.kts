import net.neoforged.moddevgradle.dsl.NeoForgeExtension

plugins {
    id("dev.kikugie.stonecutter")
    `java-library`
}

val minecraftVersion = stonecutter.current.version
extra["minecraft_version"] = minecraftVersion
apply(from = rootProject.file("gradle/node.gradle.kts"))
// common's node for this Minecraft version; its sources are compiled into this jar
val commonNode = stonecutter.node.sibling("common")!!.project
evaluationDependsOn(commonNode.path)
apply(plugin = "net.neoforged.moddev")

base {
    archivesName = "${rootProject.property("mod_id")}-neoforge"
}

configure<NeoForgeExtension> {
    version = property("neoforge_version") as String

    runs {
        // One directory per side: both sides write mcp/server.json, and sharing a run dir means
        // whichever started last wins and an agent gets the wrong token.
        register("client") { client(); gameDirectory = file("run/client") }
        register("server") { server(); gameDirectory = file("run/server") }
    }

    mods {
        register(rootProject.property("mod_id") as String) { sourceSet(the<SourceSetContainer>()["main"]) }
    }
}

// The last source dir of common's node is the copy Stonecutter processed for this version.
val commonSources = commonNode.the<SourceSetContainer>()["main"]
val commonGenerate = commonNode.tasks.matching { it.name == "stonecutterGenerate" }
tasks.named<JavaCompile>("compileJava") {
    dependsOn(commonGenerate)
    source(commonSources.java.srcDirs.last())
}

// Collected while configuring: reading the project from inside the task is deprecated (Gradle 10).
val templateValues = mapOf(
    "version" to version.toString(),
    "minecraft_version" to minecraftVersion,
    "neoforge_version_range" to property("neoforge_version_range"),
) + listOf("mod_id", "mod_name", "mod_description", "mod_author", "mod_license").associateWith { rootProject.property(it) }

tasks.named<ProcessResources>("processResources") {
    dependsOn(commonGenerate)
    from(commonSources.resources.srcDirs.last())
    inputs.properties(templateValues)
    filesMatching("META-INF/neoforge.mods.toml") { expand(templateValues) }
}
