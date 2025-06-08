package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.backend.js.utils.nameWithoutExtension
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.util.*
import xyz.atkdev.rbxkt.luau.LuauExportAnalyzer
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
        LuauExportAnalyzer.analyze(outputDir, moduleFragment.files)

        outputDir.mkdirs()

        val deleted = mutableSetOf<String>()

        moduleFragment.files.forEach { irFile ->
            val emitter = LuauEmitter(pluginContext)
            val luauFile = emitter.emitFile(irFile)
            val code = luauFile.render()

            if (!irFile.path.contains("src/")) error("Incorrect project structure, requires src folder")

            val localOutputDir = File(outputDir, irFile.path.substringAfter("src/").substringBeforeLast("/"))
            localOutputDir.mkdirs()
            if (localOutputDir.path !in deleted) {
                localOutputDir.deleteRecursively()
                deleted.add(localOutputDir.path)
            }

            val astBuilder = IndentedStringBuilder()
            luauFile.display(astBuilder)

            val astOut = File(localOutputDir, irFile.nameWithoutExtension + ".luauast")
            val luauOut = File(localOutputDir, irFile.nameWithoutExtension + ".luau")
            val irOut = File(localOutputDir, irFile.nameWithoutExtension + ".ir")
            luauOut.writeText(code)
            astOut.writeText(astBuilder.toString())
            irOut.writeText(irFile.dump())
        }
    }
}