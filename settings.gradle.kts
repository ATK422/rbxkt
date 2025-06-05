pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
        mavenLocal()
    }

}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
    }
}

rootProject.name = "rbxkt"

include("compiler-plugin")
include("gradle-plugin")
if (!gradle.startParameter.projectProperties.containsKey("withoutSample")) {
    includeBuild("sample")
}

