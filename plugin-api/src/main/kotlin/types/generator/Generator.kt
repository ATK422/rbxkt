package types.generator

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val logger = System.getLogger("type gen")
private val client = HttpClient(CIO)

@Serializable
internal data class GithubFileTreeResponse(
    val payload: GithubFileTreePayload
)

@Serializable
internal data class GithubFileTreePayload(
    val tree: GithubTree
)

@Serializable
internal data class GithubTree(
    val totalCount: Int,
    val items: List<GithubTreeItem>,
)

@Serializable
internal data class GithubTreeItem(
    val name: String,
    val path: String,
    val contentType: String,
)

internal class RobloxTypeGenerator() {
    companion object {
        private const val BASE_GITHUB_URL = "https://github.com/Roblox/creator-docs/tree/main/content/en-us/reference/engine"
        private const val BASE_RAW_GITHUB_URL = "https://raw.githubusercontent.com/Roblox/creator-docs/refs/heads/main"
    }

    private fun baseUrl(path: String): String = "$BASE_GITHUB_URL/$path"
    private fun baseRawUrl(path: String): String = "$BASE_RAW_GITHUB_URL/$path"

    private suspend fun listFiles(url: String): GithubTree {
        val response = client.get {
            url(url)
            headers {
                append("Accept", "application/json")
            }
        }
        // TODO: i love error handling
        val text = response.bodyAsText()
        val body = json.decodeFromString<GithubFileTreeResponse>(text)
        return body.payload.tree
    }

    private suspend fun readFiles(paths: GithubTree): List<String> = coroutineScope {
        paths.items.map { item ->
            async {
                client.get {
                    url(baseRawUrl(item.path))
                    headers {
                        append("Accept", "application/json")
                    }
                }.bodyAsText()
            }
        }.awaitAll()
    }

    internal suspend fun generate() {
        generateDataTypes()
        generateLuauGlobals()
        generateRobloxGlobals()
        generateLibraries()
        generateEnums()
        generateClasses()
    }

    private suspend fun generateDataTypes() {}
    private suspend fun generateLuauGlobals() {}
    private suspend fun generateRobloxGlobals() {}
    private suspend fun generateLibraries() {}
    private suspend fun generateEnums() {
        val tree = listFiles(baseUrl("enums"))
        val files = readFiles(tree)
    }
    private suspend fun generateClasses() {
        val tree = listFiles(baseUrl("classes"))
        val files = readFiles(tree)
    }
}