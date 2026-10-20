import net.fabricmc.loom.api.LoomGradleExtensionAPI

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
// 26.1 dropped obfuscation: no mappings and no remapping from there on.
val unobfuscated = stonecutter.eval(minecraftVersion, ">=26.1")
apply(plugin = if (unobfuscated) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap")
val loom = the<LoomGradleExtensionAPI>()

base {
    archivesName = "${rootProject.property("mod_id")}-fabric"
}

loom.runs {
    // One directory per side: both sides write mcp/server.json, and sharing a run dir means
    // whichever started last wins and an agent gets the wrong token.
    named("client") { client(); runDir("run/client") }
    named("server") { server(); runDir("run/server") }
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    if (unobfuscated) {
        // 26.x: Fabric API ships its own permission API (fabric-permission-api-v1)
        implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
        implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    } else {
        "mappings"(loom.officialMojangMappings())
        "include"("modImplementation"("me.lucko:fabric-permissions-api:${property("fabric_permissions_api_version")}")!!)
        "modImplementation"("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
        "modImplementation"("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
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
    "fabric_loader_version" to property("fabric_loader_version"),
    "java_version" to property("java_version"),
) + listOf("mod_id", "mod_name", "mod_description", "mod_author", "mod_license").associateWith { rootProject.property(it) }

tasks.named<ProcessResources>("processResources") {
    dependsOn(commonGenerate)
    from(commonSources.resources.srcDirs.last())
    inputs.properties(templateValues)
    filesMatching("fabric.mod.json") { expand(templateValues) }
}
