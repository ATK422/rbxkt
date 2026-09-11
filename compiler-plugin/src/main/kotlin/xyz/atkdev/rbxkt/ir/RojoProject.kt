package xyz.atkdev.rbxkt.ir

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/** Regenerate from the complete output so compiling one side preserves the other mappings. */
internal fun writeRojoProject(outputDir: File) {
    fun node(className: String, children: Map<String, JsonObject> = emptyMap()) = buildJsonObject {
        put("\$className", className)
        children.forEach { (name, child) -> put(name, child) }
    }
    fun path(path: String) = buildJsonObject { put("\$path", path) }
    fun modules(vararg kinds: String) = node("Folder", kinds
        .filter { File(outputDir, it).isDirectory }
        .associateWith { path(it) })

    val server = mutableMapOf("rbxkt" to node("Folder", mapOf("Modules" to modules("server"))))
    if (File(outputDir, "main.server.luau").isFile) server["main"] = path("main.server.luau")
    val client = mutableMapOf<String, JsonObject>()
    if (File(outputDir, "main.client.luau").isFile) client["main"] = path("main.client.luau")

    val project = buildJsonObject {
        put("name", "rbxkt")
        putJsonArray("globIgnorePaths") {
            add(kotlinx.serialization.json.JsonPrimitive("**/*.ir"))
            add(kotlinx.serialization.json.JsonPrimitive("**/*.luauast"))
        }
        put("tree", node("DataModel", mapOf(
            "ReplicatedStorage" to node("ReplicatedStorage", mapOf(
                "rbxkt" to node("Folder", mapOf("Modules" to modules("client", "shared")))
            )),
            "ServerScriptService" to node("ServerScriptService", server),
            "StarterPlayer" to node("StarterPlayer", mapOf(
                "StarterPlayerScripts" to node("StarterPlayerScripts", client)
            ))
        )))
    }
    File(outputDir, "default.project.json").writeText(
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), project) + "\n"
    )
}
