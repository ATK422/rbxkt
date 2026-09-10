import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.0"
    id("com.rbxkt") version "1.0.0"
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

rbxkt {
    outputDir.set("${project.layout.buildDirectory.get().asFile.absolutePath}/out")
}

dependencies {
}

tasks.withType<KotlinCompile>().configureEach {
    outputs.upToDateWhen { false }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
    named("main") {
        kotlin.setSrcDirs(emptyList<SourceDirectorySet>())
        resources.setSrcDirs(emptyList<SourceDirectorySet>())
    }

    named("test") {
        kotlin.setSrcDirs(emptyList<SourceDirectorySet>())
        resources.setSrcDirs(emptyList<SourceDirectorySet>())
    }

    // 1) Shared code, visible to both server & client
    val shared by creating {
        kotlin.srcDir("src/shared/")
    }

    // 2) Server code: can see shared, but not client
    val server by creating {
        kotlin.srcDir("src/server/")
        compileClasspath += shared.output
        runtimeClasspath += shared.output
    }

    // 3) Client code: can see shared, but not server
    val client by creating {
        kotlin.srcDir("src/client/")
        compileClasspath += shared.output
        runtimeClasspath += shared.output
    }
}

tasks.register("compileAll") {
    group = "build"
    dependsOn(
        tasks.named("compileServerKotlin"),
        tasks.named("compileClientKotlin"),
        tasks.named("compileSharedKotlin")
    )
}
