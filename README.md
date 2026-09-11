kotlin to roblox luau transpiler plugin

See [publishing Roblox types](types/README.md) for Maven-local setup.

The `sample/` project is standalone and resolves `com.rbxkt` plus `com.rbxkt:types`
from Maven local (not from the multi-module build):

```sh
./gradlew publishAllPluginsLocal
./gradlew -p sample compileAll
```

Annotate a startup function with `com.rbxkt.types.Entrypoint`:

```kotlin
import com.rbxkt.types.Entrypoint

@Entrypoint
fun main() {
    // Initialize this side of the game here.
}
```

Classify Kotlin compilations in `build.gradle.kts`. These are also the defaults:

```kotlin
rbxkt {
    moduleKinds.set(mapOf("client" to "client", "server" to "server", "shared" to "shared"))
}
```

Keys are compilation names; values must be `client`, `server`, or `shared`. Each
kind has one owning compilation. Custom compilation names and source directories
are supported: paths within each source root are preserved under the output kind.
Keep the client and server source sets separate, with each depending only on
shared output, as in the sample. The classification does not add Kotlin classpath
dependencies between source sets.

Each side supports at most one entrypoint, so startup order is explicit in that
function. It must be a non-private, top-level, non-suspend function returning
`Unit`, without parameters, type parameters, or overloads. Shared code cannot
declare entrypoints. A side without an entrypoint emits only modules.

The compiler generates `main.client.luau` and `main.server.luau` in the output
directory for sides with entrypoints. Each launcher requires the annotated
function's module and calls it once. The function remains exported from its
ModuleScript; requiring that module alone does not call the entrypoint.

The compiler also generates `default.project.json` in `rbxkt.outputDir` (by
default `build/out`). It is regenerated on compilation and maps the available
module folders and entrypoint launchers into Roblox, excluding debug artifacts.
Serve the sample with `rojo serve sample/build/out/default.project.json` after
compiling it. Keep custom Rojo configuration in a separate project file.

The generated project uses these mappings (only `.luau` files are runtime code):

| Output | Roblox location |
| --- | --- |
| `client/` | `ReplicatedStorage.rbxkt.Modules.client` |
| `shared/` | `ReplicatedStorage.rbxkt.Modules.shared` |
| `server/` | `ServerScriptService.rbxkt.Modules.server` |
| `main.client.luau` | A LocalScript in `StarterPlayer.StarterPlayerScripts` |
| `main.server.luau` | A Script in `ServerScriptService` |

Module imports use these kind folders as well. Existing mappings that placed
modules directly under `rbxkt.Modules` need to include the kind folder.
