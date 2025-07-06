package com.rbxkt.typegen

import com.rbxkt.typegen.generator.RobloxTypeGenerator
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val generator = RobloxTypeGenerator()
    generator.generate()
}
