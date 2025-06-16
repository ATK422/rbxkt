package types

import kotlinx.coroutines.runBlocking
import types.generator.RobloxTypeGenerator

internal fun main() = runBlocking {
    val generator = RobloxTypeGenerator()
    generator.generate()
}