plugins {
    kotlin("jvm") version "2.1.20" apply false
    id("com.github.gmazzo.buildconfig") version "5.6.5"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3" apply false
}

allprojects {
    group = "xyz.atkdev.rbxkt"
    version = "1.0.0"
}

tasks.register("buildAll") {
    dependsOn(
        ":plugin-annotations:build",
        ":compiler-plugin:build",
        ":gradle-plugin:build"
    )
}

tasks.register("publishAllPlugins") {
    dependsOn(
        "buildAll",
        ":plugin-annotations:publish",
        ":compiler-plugin:publish",
        ":gradle-plugin:publish"
    )

    if (!gradle.startParameter.projectProperties.containsKey("withoutSample")) {
        finalizedBy("buildSample")
    }
}

tasks.register("publishAllPluginsLocal") {
    dependsOn(
        "buildAll",
        ":plugin-annotations:publishToMavenLocal",
        ":compiler-plugin:publishToMavenLocal",
        ":gradle-plugin:publishToMavenLocal"
    )

    if (!gradle.startParameter.projectProperties.containsKey("withoutSample")) {
        finalizedBy("buildSample")
    }
}

if (!gradle.startParameter.projectProperties.containsKey("withoutSample")) {
    tasks.register("buildSample") {
        dependsOn(gradle.includedBuild("sample").task(":CompileAll"))
    }
}