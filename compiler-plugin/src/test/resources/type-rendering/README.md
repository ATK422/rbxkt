# Type-rendering golden tests

Run `./gradlew :compiler-plugin:typeRenderingGoldenTest` from the repository root.
The task also runs as part of `:compiler-plugin:test`, `:compiler-plugin:check`,
and `:compiler-plugin:build`.

Each `.kt` file is compiled independently with Kotlin 2.2.0. A test-only IR plugin
passes top-level function parameter and return types directly to `LuauTypeSolver`.
The result must exactly match the corresponding `.luau` file (with CRLF normalized
to LF). These files are named type snapshots, not executable Luau programs.
Function bodies and the production expression emitter are outside these tests.

Add a `.kt` / `.luau` pair to add coverage. Golden files are reviewed expectations;
the runner never regenerates them or accepts current output automatically. Every
fixture runs, and mismatches report expected and actual output. Compiler failures
include diagnostics and do not prevent later fixtures from running.

Coverage includes scalars, nullable types, arrays, lists, function types, type
parameters, and generic classes. The goldens also require preserving generic class
arguments, mapping `Number` to `number`, and mapping non-null `Nothing` to `never`.
Do not replace expectations with incorrect output to make tests pass.
