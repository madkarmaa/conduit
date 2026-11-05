import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("com.google.protobuf") version "0.10.1"
}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from
    // automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}

// Libraries bundled into the mod jar via jar-in-jar, together with their transitive dependencies.
val bundled: Configuration = configurations.create("bundled")

// Already provided at runtime by Minecraft or fabric-language-kotlin.
val providedGroups = setOf(
    "com.google.guava",
    "com.google.code.gson",
    "org.jspecify",
    "org.slf4j",
    "org.jetbrains",
    "org.jetbrains.kotlin",
    "org.jetbrains.kotlinx",
)

configurations.implementation { extendsFrom(bundled) }

val grpcVersion = providers.gradleProperty("grpc_version").get()
val grpcKotlinVersion = providers.gradleProperty("grpc_kotlin_version").get()
val protobufVersion = providers.gradleProperty("protobuf_version").get()

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

    // Fabric API. This is technically optional, but you probably want it anyway.
    implementation(
        "net.fabricmc.fabric-api:fabric-api:${
            providers.gradleProperty(
                "fabric_api_version",
            ).get()
        }",
    )
    implementation(
        "net.fabricmc:fabric-language-kotlin:${
            providers.gradleProperty(
                "fabric_kotlin_version",
            ).get()
        }",
    )

    bundled("io.grpc:grpc-netty-shaded:$grpcVersion")
    bundled("io.grpc:grpc-protobuf:$grpcVersion")
    bundled("io.grpc:grpc-stub:$grpcVersion")
    bundled("io.grpc:grpc-services:$grpcVersion")
    bundled("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    bundled("com.google.protobuf:protobuf-java:$protobufVersion")
    bundled("com.google.protobuf:protobuf-java-util:$protobufVersion")
    bundled("com.google.protobuf:protobuf-kotlin:$protobufVersion")

    // Loom's include configuration is not transitive, so every resolved module is listed
    // explicitly.
    bundled.resolvedConfiguration.resolvedArtifacts.map { it.moduleVersion.id }
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

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val loaderVersion = providers.gradleProperty("loader_version").get()

tasks.processResources {
    val properties = mapOf(
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

java {
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    val projectName = project.name
    inputs.property("projectName", projectName)

    from("LICENSE") {
        rename { "${it}_$projectName" }
    }
}

// configure the maven publication
publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how
    // to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
