plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "1.9.0"
}

dependencies {
    implementation("io.ktor:ktor-client-core:3.1.3")
    implementation("io.ktor:ktor-client-cio:3.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("com.charleskorn.kaml:kaml:0.81.0")
}

repositories {
    mavenLocal()
    mavenCentral()
}

sourceSets {
    val api by creating {
        kotlin.srcDir("src/api/")
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.register<JavaExec>("generateTypes") {
    group = "build"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("types.MainKt")
}