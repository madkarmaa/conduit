import com.google.protobuf.gradle.id
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.Library
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    `maven-publish`
    eclipse
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("com.google.protobuf") version "0.10.1"
    id("com.diffplug.spotless") version "8.11.0"
}

// Libraries bundled into the mod jar via jar-in-jar, together with their transitive dependencies.
val bundled: Configuration = configurations.create("bundled")

// Already provided at runtime by Minecraft or fabric-language-kotlin.
val providedGroups =
    setOf(
        "com.google.guava",
        "com.google.code.gson",
        "org.jspecify",
        "org.slf4j",
        "org.jetbrains",
        "org.jetbrains.kotlin",
        "org.jetbrains.kotlinx",
    )

configurations.implementation { extendsFrom(bundled) }

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val loaderVersion = providers.gradleProperty("loader_version").get()
val fabricApiVersion = providers.gradleProperty("fabric_api_version").get()
val fabricKotlinVersion = providers.gradleProperty("fabric_kotlin_version").get()
val grpcVersion = providers.gradleProperty("grpc_version").get()
val grpcKotlinVersion = providers.gradleProperty("grpc_kotlin_version").get()
val protobufVersion = providers.gradleProperty("protobuf_version").get()

dependencies {
    // gradle.properties sets the versions.
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    // Fabric API, which the mod requires.
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    bundled("io.grpc:grpc-netty-shaded:$grpcVersion")
    bundled("io.grpc:grpc-protobuf:$grpcVersion")
    bundled("io.grpc:grpc-stub:$grpcVersion")
    bundled("io.grpc:grpc-services:$grpcVersion")
    bundled("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    bundled("com.google.protobuf:protobuf-java:$protobufVersion")
    bundled("com.google.protobuf:protobuf-java-util:$protobufVersion")
    bundled("com.google.protobuf:protobuf-kotlin:$protobufVersion")

    // Loom's include configuration is not transitive, so this lists every resolved module.
    bundled.resolvedConfiguration.resolvedArtifacts
        .map { it.moduleVersion.id }
        .filter { it.group !in providedGroups }
        .forEach { include("${it.group}:${it.name}:${it.version}") }
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
    plugins {
        id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" }
        id("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar" }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                id("grpc")
                id("grpckt")
            }
            builtins {
                id("kotlin")
            }
        }
    }
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

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

// Eclipse-based Java tooling (Buildship, jdtls) compiles only Java, so the mixins see the Kotlin
// classes they call only through the Kotlin compiler output.
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
        // Eclipse-based tooling such as jdtls copies the generated proto sources into bin.
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

val installClientDependencies by
    tasks.registering(Exec::class) { commandLine(bun, "install", "--frozen-lockfile") }

// The TypeScript client is generated from the mod's protos, so Gradle rebuilds it with the mod.
val buildClient by
    tasks.registering(Exec::class) {
        dependsOn(installClientDependencies)
        workingDir("client")
        commandLine(bun, "run", "build")

        inputs.dir("src/main/proto")
        inputs.files("client/package.json", "client/tsconfig.json", "client/tsdown.config.ts")
        inputs.files(fileTree("client/src") { exclude("generated/**") })
        outputs.dir("client/dist")
    }

val buildMcp by
    tasks.registering(Exec::class) {
        dependsOn(buildClient)
        workingDir("mcp")
        commandLine(bun, "run", "build")

        inputs.files("mcp/package.json", "mcp/tsconfig.json", "mcp/tsdown.config.ts")
        inputs.dir("mcp/src")
        outputs.dir("mcp/dist")
    }

tasks.build { finalizedBy(buildClient, buildMcp) }

val formatWithBun by tasks.registering(Exec::class) { commandLine(bun, "fmt") }

tasks.spotlessApply { finalizedBy(formatWithBun) }

val checkFormatWithBun by tasks.registering(Exec::class) { commandLine(bun, "fmt", "--check") }

tasks.spotlessCheck { finalizedBy(checkFormatWithBun) }

// Maven publication.
publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
