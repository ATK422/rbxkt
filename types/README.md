# Publishing Roblox types

The publication is `com.rbxkt:types:1.0.0` by default. It contains the compiled
generated API and a `sources` JAR. Its only Maven dependency is Kotlin stdlib;
the generator, HTTP client, YAML parser, and KotlinPoet are not published with it.
These declarations are for rbxkt compilation, not for running Roblox APIs on a JVM.

## Maven local

From the repository root:

```sh
./gradlew :types:publishToMavenLocal
```

This runs compatibility tests, generates the API when needed, compiles it, and
publishes the JARs and POM to `~/.m2/repository/com/rbxkt/types/1.0.0/` (or your
configured Maven-local directory).

To choose a new version:

```sh
./gradlew :types:publishToMavenLocal -PrbxktVersion=1.0.1-SNAPSHOT
```

`rbxktVersion` applies to all projects so plugin dependency coordinates stay
consistent when publishing the plugins and types together.

Publish the compiler + Gradle plugins together with:

```sh
./gradlew publishAllPluginsLocal
```

## Regeneration and artifacts

Generated sources live under `types/build/generated/sources/roblox/`; publication
JARs live under `types/build/type-publication/`. Generation is reused until its
inputs or outputs change. To fetch the latest upstream docs explicitly:

```sh
./gradlew :types:generateTypes --rerun-tasks
./gradlew :types:publishToMavenLocal -PrbxktVersion=1.0.1
```

To create and inspect JARs without publishing, run `./gradlew :types:build`.

In a consuming Kotlin project:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnly("com.rbxkt:types:1.0.0")
}
```

The generated classes target Java 21 and Kotlin 2.2.0. The rbxkt Gradle plugin
already supplies its configured types dependency to projects using that plugin.

## Sample project

`sample/` is a standalone Gradle project (not part of the multi-module build). After
publishing plugins and types to Maven local, build it with:

```sh
./gradlew publishAllPluginsLocal
./gradlew -p sample compileAll
```
