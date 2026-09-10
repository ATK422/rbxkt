package com.rbxkt.typegen.fetch

import com.charleskorn.kaml.Yaml
import com.rbxkt.typegen.generator.DocsModel
import com.rbxkt.typegen.models.GithubTree
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal class GithubApi(
    private val client: HttpClient = HttpClient(CIO),
    private val treeRoot: String = BASE_TREE_API_URL,
    private val rawRoot: String = BASE_RAW_GITHUB_URL,
) : AutoCloseable {
    companion object {
        internal const val ENGINE_PATH = "content/en-us/reference/engine"
        internal const val BASE_GITHUB_URL = "https://github.com/Roblox/creator-docs/tree/main/$ENGINE_PATH"
        internal const val BASE_RAW_GITHUB_URL = "https://raw.githubusercontent.com/Roblox/creator-docs/main"
        internal const val BASE_TREE_API_URL = "https://api.github.com/repos/Roblox/creator-docs/git/trees/main:$ENGINE_PATH"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val yaml = Yaml(configuration = Yaml.default.configuration.copy(strictMode = false))
    private val requests = Semaphore(8)

    private suspend fun get(url: String, accept: String): String = requests.withPermit {
        val response = client.get(url) {
            header(HttpHeaders.Accept, accept)
            header(HttpHeaders.UserAgent, "rbxkt-typegen")
        }
        check(response.status == HttpStatusCode.OK) { "GitHub request failed: ${response.status} ($url)" }
        response.bodyAsText()
    }

    suspend fun listFiles(directory: String): GithubTree {
        val url = "$treeRoot/$directory"
        val tree = json.decodeFromString<GithubTree>(get(url, "application/vnd.github+json"))
        check(!tree.truncated) { "GitHub returned a truncated tree for $url; refusing to generate incomplete types" }
        return tree.copy(tree = tree.tree
            .filter { it.type == "blob" && (it.path.endsWith(".yaml") || it.path.endsWith(".yml")) }
            .sortedBy { it.path }
            .map { it.copy(path = "$ENGINE_PATH/$directory/${it.path}") })
    }

    suspend fun readFile(rawPath: String, isDocs: Boolean = true): String =
        get(if (isDocs) "$rawRoot/$rawPath" else rawPath, "text/plain")

    suspend inline fun <reified T> getJsonFile(url: String, isDocs: Boolean = true): T =
        json.decodeFromString<T>(readFile(url, isDocs))

    suspend inline fun <reified T : DocsModel> getYamlFiles(directory: String): Map<String, T> = coroutineScope {
        listFiles(directory).tree.map { item ->
            async {
                val text = readFile(item.path)
                val file = try {
                    yaml.decodeFromString<T>(text)
                } catch (exception: SerializationException) {
                    throw IllegalArgumentException("Invalid creator docs YAML: ${item.path}", exception)
                }
                file.name to file
            }
        }.awaitAll().toMap()
    }

    override fun close() = client.close()
}
