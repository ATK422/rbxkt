package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.incremental.deleteDirectoryContents
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
        val files = moduleFragment.files
        if (files.isEmpty()) return

        PluginEnvironment.irPluginContext = pluginContext
        PluginEnvironment.irModuleFragment = moduleFragment

        LuauTypeSolver.irContext = pluginContext
        LuauExportAnalyzer.analyze(outputDir, files)

        outputDir.mkdirs()

        files.first().run {
            if (!path.contains("src/")) error("Incorrect project structure, requires src folder")

            File(
                outputDir,
                path.substringAfter("src/").substringBefore("/")
            ).takeIf { it.isDirectory }?.deleteDirectoryContents()
        }

        files.forEach { irFile ->
            val emitter = LuauEmitter(pluginContext)
            val luauFile = emitter.emitFile(irFile)
            val code = luauFile.render()

            val relativeDir = File(outputDir, irFile.path.substringAfter("src/").substringBeforeLast("/"))
            relativeDir.mkdirs()

            val astBuilder = IndentedStringBuilder()
            luauFile.display(astBuilder)

            val fileName = irFile.nameWithoutExtension
            File(relativeDir, "$fileName.luau").writeText(code)
            File(relativeDir, "$fileName.luauast").writeText(astBuilder.toString())
            File(relativeDir, "$fileName.ir").writeText(irFile.dump())
        }
    }
}