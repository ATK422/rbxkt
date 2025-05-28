plugins {
    kotlin("jvm")
    id("com.github.gmazzo.buildconfig")
    id("java-gradle-plugin")
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    test {
        java.setSrcDirs(listOf("test"))
        resources.setSrcDirs(listOf("testResources"))
    }
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))

    testImplementation(kotlin("test-junit5"))
}

buildConfig {
    packageName(project.group.toString())

    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")

    val pluginProject = project(":compiler-plugin")
    buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${pluginProject.group}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${pluginProject.name}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${pluginProject.version}\"")

    val annotationsProject = project(":plugin-annotations")
    buildConfigField(
        type = "String",
        name = "ANNOTATIONS_LIBRARY_COORDINATES",
        expression = "\"${annotationsProject.group}:${annotationsProject.name}:${annotationsProject.version}\""
    )
}

group = "xyz.atkdev.rbxkt"
version = "1.0.0"

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
