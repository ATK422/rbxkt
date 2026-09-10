plugins {
    kotlin("jvm") version "2.2.0" apply false
    id("com.github.gmazzo.buildconfig") version "5.6.5"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3" apply false
}

allprojects {
    group = "com.rbxkt"
    version = providers.gradleProperty("rbxktVersion").orElse("1.0.0").get()
}

tasks.register("buildAll") {
    group = "build"
    dependsOn(
        ":types:build",
        ":compiler-plugin:build",
        ":gradle-plugin:build"
    )
}

tasks.register("publishAllPluginsLocal") {
    group = "publishing"
    dependsOn(
        "buildAll",
        ":types:publishToMavenLocal",
        ":compiler-plugin:publishToMavenLocal",
        ":gradle-plugin:publishToMavenLocal"
    )
}
