// Applied by every Stonecutter node (common/fabric/neoforge x Minecraft version) after it set
// extra["minecraft_version"]. Loads versions/<minecraft>.properties into the node's extra properties.
import java.util.Properties

val minecraftVersion = extra["minecraft_version"] as String
Properties().apply { rootProject.file("versions/$minecraftVersion.properties").inputStream().use(::load) }
    .forEach { (key, value) -> extra[key as String] = value }
val javaVersion = (extra["java_version"] as String).toInt()

group = rootProject.group
version = "${rootProject.version}+$minecraftVersion"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net") { name = "Fabric" }
    maven("https://maven.neoforged.net/releases") { name = "NeoForge" }
}

configure<JavaPluginExtension> {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Tests bootstrap Minecraft, whose JOML would otherwise use sun.misc.Unsafe (deprecated for removal on Java 24+).
    systemProperty("joml.nounsafe", "true")
}
