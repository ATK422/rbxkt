package com.rbxkt.typegen.fetch

import com.charleskorn.kaml.Yaml
import com.rbxkt.typegen.generator.DocsModel
import com.rbxkt.typegen.models.GithubFileTreeResponse
import com.rbxkt.typegen.models.GithubTree
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val yaml = Yaml(configuration = Yaml.default.configuration.copy(strictMode = false))

private val client = HttpClient(CIO)

internal object GithubApi {
    internal const val BASE_GITHUB_URL =
        "https://github.com/Roblox/creator-docs/tree/main/content/en-us/reference/engine"
    internal const val BASE_RAW_GITHUB_URL = "https://raw.githubusercontent.com/Roblox/creator-docs/refs/heads/main"

    suspend fun listFiles(url: String, isDocs: Boolean = true): GithubTree {
        val response = client.get {
            url(if (isDocs) "$BASE_GITHUB_URL/$url" else url)
            headers {
                append("Accept", "application/json")
            }
        }
        if (response.status.value != 200) error("status: ${response.status} url: $BASE_GITHUB_URL/$url")
        // TODO: i love error handling
        val text = response.bodyAsText()
        val body = json.decodeFromString<GithubFileTreeResponse>(text)
        return body.payload.tree
    }

    suspend fun readFiles(paths: GithubTree, root: String = BASE_RAW_GITHUB_URL): List<String> = coroutineScope {
        paths.items.map { item ->
            async {
                client.get {
                    url("$root/${item.path}")
                    headers {
                        append("Accept", "application/json")
                    }
                }.bodyAsText()
            }
        }.awaitAll()
    }

    suspend fun readFile(rawPath: String, isDocs: Boolean = true): String = coroutineScope {
        async {
            client.get {
                url(if (isDocs) "$BASE_RAW_GITHUB_URL/$rawPath" else rawPath)
                headers {
                    append("Accept", "application/json")
                }
            }.bodyAsText()
        }.await()
    }

    suspend fun listAndReadFiles(url: String, isDocs: Boolean = true) = readFiles(listFiles(url, isDocs))

    suspend inline fun <reified T> getJsonFile(url: String, isDocs: Boolean = true) =
        json.decodeFromString<T>(readFile(url, isDocs))

    suspend inline fun <reified T : DocsModel> getYamlFiles(url: String, isDocs: Boolean = true) =
        processDocsYamlFiles<T>(listAndReadFiles(url, isDocs))
}

private inline fun <reified T : DocsModel> processDocsYamlFiles(files: List<String>) = files.associate {
    val file = yaml.decodeFromString<T>(it)
    file.name to file
}