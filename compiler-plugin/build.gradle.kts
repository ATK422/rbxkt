plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("com.github.gmazzo.buildconfig")
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.0"
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
}

dependencies {
    compileOnly(kotlin("compiler"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
}

buildConfig {
    useKotlinOutput {
        internalVisibility = true
    }

    packageName(group.toString())
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}