plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    kotlin("jvm") version "2.1.20"
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}