plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("com.github.gmazzo.buildconfig")
    kotlin("jvm") version "2.2.0"
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
        java.setSrcDirs(listOf("src/main/kotlin"))
        resources.setSrcDirs(listOf("resources"))
    }
}

dependencies {
    compileOnly(kotlin("compiler"))
    testImplementation(kotlin("compiler"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
}

val typeRenderingGoldenTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks Luau type rendering against Kotlin IR golden fixtures."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("xyz.atkdev.rbxkt.luau.TypeRenderingGoldenTestKt")
    args(layout.projectDirectory.dir("src/test/resources/type-rendering").asFile.absolutePath)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

tasks.named("test") {
    dependsOn(typeRenderingGoldenTest)
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
