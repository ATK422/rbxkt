package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.incremental.deleteDirectoryContents
import org.jetbrains.kotlin.ir.backend.js.utils.nameWithoutExtension
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.*
import xyz.atkdev.rbxkt.util.IndentedStringBuilder
import xyz.atkdev.rbxkt.luau.LuauTypeSolver
import java.io.File

object PluginEnvironment {
    var logger: MessageCollector = MessageCollector.NONE
    lateinit var irPluginContext: IrPluginContext
    lateinit var irModuleFragment: IrModuleFragment
}

class LuauIrGenerationExtension(
    private val outputDir: File,
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        PluginEnvironment.irPluginContext = pluginContext
        PluginEnvironment.irModuleFragment = moduleFragment
        LuauTypeSolver.irContext = pluginContext

        outputDir.mkdirs()
        outputDir.deleteDirectoryContents()

        moduleFragment.files.forEach { irFile ->
            val emitter = LuauEmitter(pluginContext)
            val luauFile = emitter.emitFile(irFile)
            val code = luauFile.render()

            val astBuilder = IndentedStringBuilder()
            luauFile.display(astBuilder)

            val astOut = File(outputDir, irFile.nameWithoutExtension + ".luauast")
            val luauOut = File(outputDir, irFile.nameWithoutExtension + ".luau")
            val irOut = File(outputDir, irFile.nameWithoutExtension + ".ir")
            luauOut.parentFile.mkdirs()
            luauOut.writeText(code)
            astOut.writeText(astBuilder.toString())
            irOut.writeText(irFile.dump())
        }
    }
}