import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.Library
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    eclipse
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
    id("com.diffplug.spotless") version "8.11.0"
}

// Libraries bundled into the mod jar via jar-in-jar, together with their transitive dependencies.
val bundled: Configuration = configurations.create("bundled")

// Minecraft already provides these at runtime.
val minecraftGroups =
    setOf("com.google.guava", "com.google.code.gson", "org.jspecify", "org.slf4j", "org.jetbrains")

configurations.implementation { extendsFrom(bundled) }

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val loaderVersion = providers.gradleProperty("loader_version").get()
val fabricApiVersion = providers.gradleProperty("fabric_api_version").get()
val fabricKotlinVersion = providers.gradleProperty("fabric_kotlin_version").get()
val ktorVersion = providers.gradleProperty("ktor_version").get()
val mcpVersion = providers.gradleProperty("mcp_version").get()
val jsonSchemaVersion = providers.gradleProperty("json_schema_version").get()

dependencies {
    // gradle.properties sets the versions.
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    // Fabric API, which the mod requires.
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    // CIO keeps a second Netty out of the game, which already ships its own.
    bundled("io.ktor:ktor-server-cio:$ktorVersion")
    bundled("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    bundled("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    bundled("io.modelcontextprotocol:kotlin-sdk-server:$mcpVersion")
    bundled("com.networknt:json-schema-validator:$jsonSchemaVersion")

    testImplementation(kotlin("test"))

    // fabric-language-kotlin ships the Kotlin libraries its POM lists, such as coroutines and
    // kotlinx-serialization, so the mod uses those instead of bundling its own copies.
    val fabricKotlin = create("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")
    val fabricKotlinModules =
        configurations
            .detachedConfiguration(fabricKotlin)
            .resolvedConfiguration
            .resolvedArtifacts
            .map { "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}" }
            .toSet()

    // Loom's include configuration is not transitive, so this lists every resolved module.
    bundled.resolvedConfiguration.resolvedArtifacts
        .map { it.moduleVersion.id }
        .filter { it.group !in minecraftGroups && "${it.group}:${it.name}" !in fabricKotlinModules }
        .forEach { include("${it.group}:${it.name}:${it.version}") }
}

tasks.processResources {
    val properties =
        mapOf(
            "version" to version,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion,
        )
    inputs.properties(properties)

    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

// The game version is part of the file name, since each build only supports one Minecraft version.
tasks.withType<AbstractArchiveTask>().configureEach {
    archiveVersion = "${project.version}+$minecraftVersion"
}

tasks.test {
    useJUnitPlatform()

    // The game's logging config writes a logs folder into the working directory.
    workingDir = layout.buildDirectory.get().asFile

    // A test checks that the README lists every trigger event type, so it reads the README.
    inputs.file("README.md")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

// Eclipse-based Java tooling, such as Buildship and jdtls, compiles only Java, so the mixins see
// the Kotlin classes they call only through the Kotlin compiler output.
eclipse {
    synchronizationTasks(tasks.compileKotlin)
    classpath.file.whenMerged(
        Action<Classpath> {
            val kotlinClasses = tasks.compileKotlin.get().destinationDirectory.get().asFile
            entries.add(Library(fileReference(kotlinClasses)))
        },
    )
}

java {
    // Loom attaches sourcesJar to a RemapSourcesJar task and to the build task. Without this
    // line, the build produces no sources jar.
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

spotless {
    java {
        target("**/*.java")
        targetExclude("**/build/**")

        googleJavaFormat().aosp()

        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlin {
        target("**/*.kt")
        // Eclipse-based tooling such as jdtls copies the sources into bin.
        targetExclude("**/build/**", "**/bin/**")

        ktfmt().configure {
            it.setMaxWidth(100)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
        }

        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")

        ktfmt().configure {
            it.setMaxWidth(100)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
        }

        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.jar {
    val projectName = project.name
    inputs.property("projectName", projectName)

    from("LICENSE") {
        rename { "${it}_$projectName" }
    }
}

val bun: String =
    providers
        .environmentVariable("PATH")
        .get()
        .split(File.pathSeparator)
        .map { File(it, "bun") }
        .firstOrNull { it.canExecute() }
        ?.path ?: "${System.getProperty("user.home")}/.bun/bin/bun"

val formatWithBun = tasks.register<Exec>("formatWithBun") { commandLine(bun, "fmt") }

tasks.spotlessApply { finalizedBy(formatWithBun) }

val checkFormatWithBun =
    tasks.register<Exec>("checkFormatWithBun") { commandLine(bun, "fmt", "--check") }

tasks.spotlessCheck { finalizedBy(checkFormatWithBun) }
