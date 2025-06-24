package types

import kotlinx.coroutines.runBlocking
import types.generator.RobloxTypeGenerator

fun main() = runBlocking {
    val generator = RobloxTypeGenerator()
    generator.generate()
    1.downTo(5)
}
