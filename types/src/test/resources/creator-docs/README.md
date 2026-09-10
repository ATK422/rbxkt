# Creator docs compatibility tests

Run `./gradlew :types:check` for offline tests. A local HTTP server exercises the
actual importer: Git tree responses, filtering `.yaml`/`.yml` blobs, raw-file paths,
unknown JSON/YAML fields, truncated listings, HTTP failures, and YAML diagnostics.
Schema tests cover generated definition names, semantic `$id` lookup, the older
named definitions, deduplication, and missing required definitions.

Run `./gradlew :types:verifyLiveCreatorDocs` to fetch the current public docs and
generate all four Kotlin files in a temporary directory. This opt-in check requires
network access and deletes its generated files afterward. It checks generation;
it does not compile the generated Kotlin API. Normal offline tests never fetch
upstream data or publish artifacts.

`current-schema.json` was derived from Roblox's
[classes schema](https://github.com/Roblox/creator-docs/blob/main/tools/schemas/engine/classes.json)
on 2026-09-08, with descriptions/comments removed. `legacy-schema.json` models the
previous named definitions without `$id` fields. The tree and enum fixtures are
minimal synthetic examples of the
[Git trees API](https://docs.github.com/en/rest/git/trees#get-a-tree) and the
[engine YAML](https://github.com/Roblox/creator-docs/tree/main/content/en-us/reference/engine).

The importer follows semantic schema IDs rather than generated `__schemaN` names.
The old GitHub web-page `payload`/`codeViewTreeRoute` structures are no longer used.
