package com.rbxkt.typegen

import com.rbxkt.typegen.fetch.GithubApi
import com.rbxkt.typegen.generator.RobloxTypeGenerator
import com.rbxkt.typegen.models.EnumModel
import com.rbxkt.typegen.models.SchemaModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

private fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/creator-docs/$name")).readText()

private suspend fun expectFailure(message: String, block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    check(failure != null && failure.message.orEmpty().contains(message)) {
        "Expected failure containing '$message', got $failure"
    }
}

fun main(args: Array<String>): Unit = runBlocking {
    if (args.contentEquals(arrayOf("--live"))) {
        val output = Files.createTempDirectory("rbxkt-live-docs-").toFile()
        try {
            GithubApi().use { api -> RobloxTypeGenerator(api).generate(output) }
            val files = output.walkTopDown().filter { it.extension == "kt" }.toList()
            check(files.size == 4) { "Expected four generated files, got ${files.size}" }
            check(files.all { it.length() > 0 })
            println("PASS live creator-docs generation (${files.sumOf { it.length() }} bytes)")
        } finally {
            output.deleteRecursively()
        }
        return@runBlocking
    }

    val json = Json { ignoreUnknownKeys = true }
    val current = json.decodeFromString<SchemaModel>(fixture("current-schema.json"))
    val legacy = json.decodeFromString<SchemaModel>(fixture("legacy-schema.json"))
    check(current.annotationNames() == legacy.annotationNames())
    check(current.annotationNames().containsAll(listOf("NotCreatable", "PluginSecurity", "ReadSafe")))
    check("Deprecated" !in current.annotationNames())
    check(current.annotationNames().count { it == "ReadOnly" } == 1)
    val renamed = current.copy(definitions = current.definitions.values.withIndex().associate { "new${it.index}" to it.value })
    check(renamed.annotationNames() == current.annotationNames())
    expectFailure("missing") { SchemaModel(emptyMap()).annotationNames() }
    println("PASS current, legacy, renamed, and invalid schemas")

    val requests = CopyOnWriteArrayList<String>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val prefix = "/raw/${GithubApi.ENGINE_PATH}/enums"
    server.createContext("/") { exchange ->
        val path = exchange.requestURI.path
        requests += path
        val (status, body) = when (path) {
            "/tree/enums" -> 200 to fixture("tree.json")
            "/tree/truncated" -> 200 to """{"tree":[],"truncated":true}"""
            "/tree/forbidden" -> 403 to "rate limited"
            "$prefix/First.yaml" -> 200 to fixture("enum.yaml")
            "$prefix/Second.yml" -> 200 to fixture("enum.yaml").replace("name: First", "name: Second")
            "/raw/schema.json" -> 200 to fixture("current-schema.json")
            "/tree/broken" -> 200 to """{"tree":[{"path":"Invalid.yaml","type":"blob"}],"truncated":false}"""
            "/raw/${GithubApi.ENGINE_PATH}/broken/Invalid.yaml" -> 200 to "invalid: yaml"
            else -> 404 to "not found"
        }
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }
    server.start()
    try {
        val base = "http://127.0.0.1:${server.address.port}"
        GithubApi(treeRoot = "$base/tree", rawRoot = "$base/raw").use { api ->
            val enums = api.getYamlFiles<EnumModel>("enums")
            check(enums.keys.toList() == listOf("First", "Second"))
            check(enums.values.all { it.items.single().name == "Value" })
            check(requests.toSet() == setOf("/tree/enums", "$prefix/First.yaml", "$prefix/Second.yml"))
            check(api.getJsonFile<SchemaModel>("schema.json").annotationNames() == current.annotationNames())
            check(api.getJsonFile<SchemaModel>("$base/raw/schema.json", false).annotationNames() == current.annotationNames())
            expectFailure("truncated") { api.listFiles("truncated") }
            expectFailure("403") { api.listFiles("forbidden") }
            expectFailure("404") { api.readFile("missing.yaml") }
            expectFailure("broken/Invalid.yaml") { api.getYamlFiles<EnumModel>("broken") }
        }
    } finally {
        server.stop(0)
    }
    println("PASS Git tree parsing, YAML filtering, raw paths, HTTP failures, and YAML diagnostics")
}
