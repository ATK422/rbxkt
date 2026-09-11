package xyz.atkdev.rbxkt.luau

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

fun main(args: Array<String>) {
    val work = Files.createTempDirectory("rbxkt-emitter-test-").toFile()
    try {
        val stdlib = File(Unit::class.java.protectionDomain.codeSource.location.toURI())
        fun compile(vararg options: String): Pair<ExitCode, String> {
            val diagnostics = ByteArrayOutputStream()
            val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, "-no-stdlib", "-no-reflect", *options) }
            return result to diagnostics.toString()
        }
        val stubs = File(work, "stubs").apply { mkdirs() }
        File(stubs, "Services.kt").writeText("""
            package com.rbxkt.types.classes
            object Players { val localPlayer: Player get() = TODO() }
            class Player { external fun findFirstChild(name: String): Player? }
        """.trimIndent())
        File(stubs, "Datatypes.kt").writeText("""
            package com.rbxkt.types.datatypes
            class UDim2(val x: Number, val y: Number) {
                companion object { external fun fromScale(x: Number, y: Number): UDim2 }
            }
        """.trimIndent())
        File(stubs, "Enums.kt").writeText("""
            package com.rbxkt.types.enums
            enum class Font { SourceSans }
        """.trimIndent())
        val api = File(work, "api")
        val stubResult = compile("-classpath", stdlib.path, "-d", api.path, stubs.path)
        check(stubResult.first == ExitCode.OK) { stubResult.second }
        val source = File(work, "src").apply { mkdirs() }
        val fixture = File(source, "Example.kt")
        val output = File(work, "out")
        fun compileFixture() = compile(
            "-classpath", "${stdlib.path}${File.pathSeparator}${api.path}",
            "-Xplugin=${args.single()}",
            "-P", "plugin:com.rbxkt:outputDir=${output.path}",
            "-P", "plugin:com.rbxkt:moduleKind=client",
            "-P", "plugin:com.rbxkt:sourceRoot=${source.path}",
            "-d", File(work, "classes").path, fixture.path
        )
        fixture.writeText("""
            import com.rbxkt.types.classes.Players
            import com.rbxkt.types.datatypes.UDim2
            import com.rbxkt.types.enums.Font
            fun example() {
                val player = Players.localPlayer.findFirstChild("PlayerGui")!!
                val scale = UDim2.fromScale(0.5, 0.8)
                val size = UDim2(1, 2)
                val font = Font.SourceSans
            }
        """.trimIndent())
        val success = compileFixture()
        check(success.first == ExitCode.OK) { success.second }
        val emitted = File(output, "client/Example.luau").readText()
        for (expected in listOf("game:GetService(\"Players\").LocalPlayer:FindFirstChild(\"PlayerGui\")",
            "assert((value ~= nil))", "UDim2.fromScale(0.5, 0.8)", "UDim2.new(1, 2)", "Enum.Font.SourceSans")) {
            check(expected in emitted) { "Missing '$expected':\n$emitted" }
        }
        fixture.writeText("fun example() { try { println(1) } finally { println(2) } }")
        val failure = compileFixture()
        check(failure.first == ExitCode.COMPILATION_ERROR) { failure.toString() }
        check("rbxkt: Unsupported Kotlin construct (IrTryImpl)" in failure.second) { failure.second }
        check("Example.kt:1:" in failure.second || "Example.kt:1" in failure.second) { failure.second }
        check("IllegalStateException" !in failure.second && "Internal compiler error" !in failure.second) { failure.second }
        check(File(output, "client/Example.luau").readText() == emitted)
        println("PASS service objects, datatype factories/constructors, enums, null assertions, and source-located unsupported-node diagnostics")
    } finally {
        work.deleteRecursively()
    }
}
