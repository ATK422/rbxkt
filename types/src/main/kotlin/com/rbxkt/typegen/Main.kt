package com.rbxkt.typegen

import com.rbxkt.typegen.generator.RobloxTypeGenerator
import com.rbxkt.typegen.fetch.GithubApi
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>): Unit = runBlocking {
    require(args.size <= 1) { "Usage: typegen [output-directory]" }
    GithubApi().use { api ->
        val output = args.singleOrNull()?.let(::File) ?: File("src/generated")
        RobloxTypeGenerator(api).generate(output)
    }
}
