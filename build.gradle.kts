plugins {
    kotlin("jvm") version "2.1.20" apply false
    id("com.github.gmazzo.buildconfig") version "5.6.5"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3" apply false
}

allprojects {
    group = "xyz.atkdev.rbxkt"
    version = "1.0.0"
}

tasks.register("publishAllPlugins") {
    dependsOn(
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