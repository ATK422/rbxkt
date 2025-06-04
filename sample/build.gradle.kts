plugins {
    kotlin("jvm")
    id("xyz.atkdev.rbxkt") version "1.0.0"
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