import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.20"
    id("xyz.atkdev.rbxkt") version "1.0.0"
    `java-library`
}

repositories {
    mavenLocal()
    mavenCentral()
}
kotlin {
    jvmToolchain(21)
}

rbxkt {
    outputDir.set("${project.layout.buildDirectory.get().asFile.absolutePath}/out")
}

tasks.withType<KotlinCompile>().configureEach {
    outputs.upToDateWhen { false }
}

subprojects {
//    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenLocal()
        mavenCentral()
    }

//    apply(plugin = "xyz.atkdev.rbxkt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
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

tasks.register("CompileAll") {
    group = "build"
    dependsOn(
        tasks.named("compileServerKotlin"),
        tasks.named("compileClientKotlin"),
        tasks.named("compileSharedKotlin")
    )
}

tasks.named("build") {
    dependsOn(tasks.named("CompileAll"))
}