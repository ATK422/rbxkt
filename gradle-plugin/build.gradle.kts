plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("com.github.gmazzo.buildconfig")
    kotlin("jvm") version "2.2.0"
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
    implementation(kotlin("gradle-plugin-api"))
    implementation(kotlin("stdlib"))
}

buildConfig {
    packageName(project.group.toString())


    val compilerProject = project(":compiler-plugin")
    buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${compilerProject.group}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${compilerProject.name}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${compilerProject.version}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")

    val typeProject = project(":types")
    buildConfigField(
        type = "String",
        name = "TYPE_COORDINATES",
        expression = "\"${typeProject.group}:${typeProject.name}:${typeProject.version}\""
    )
}

gradlePlugin {
    plugins {
        create("RbxKtPlugin") {
            id = "com.rbxkt"
            displayName = "RbxKtPlugin"
            description = "RbxKtPlugin"
            implementationClass = "xyz.atkdev.rbxkt.RbxKtGradlePlugin"
        }
    }
}