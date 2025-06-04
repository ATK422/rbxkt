plugins {
    id("java-gradle-plugin")
    `maven-publish`
    kotlin("jvm")
    id("com.github.gmazzo.buildconfig")
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
    implementation(project(":compiler-plugin"))
    implementation(kotlin("gradle-plugin-api"))
    implementation(kotlin("stdlib"))
}

buildConfig {
    packageName(project.group.toString())

    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")

    val pluginProject = project(":compiler-plugin")
    buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${pluginProject.group}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${pluginProject.name}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${pluginProject.version}\"")
}

gradlePlugin {
    plugins {
        create("RbxKtPlugin") {
            id = "xyz.atkdev.rbxkt"
            displayName = "RbxKtPlugin"
            description = "RbxKtPlugin"
            implementationClass = "xyz.atkdev.rbxkt.RbxKtGradlePlugin"
        }
    }
}