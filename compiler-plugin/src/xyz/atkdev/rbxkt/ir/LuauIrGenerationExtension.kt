package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.backend.js.utils.nameWithoutExtension
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import xyz.atkdev.rbxkt.luau.LuauTypeSolver
import java.io.File

class LuauIrGenerationExtension(
    private val outputDir: File,
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        LuauTypeSolver.irContext = pluginContext

        moduleFragment.files.forEach { irFile ->
            val emitter = LuauEmitter(pluginContext)
            val luauFile = emitter.emitFile(irFile)
//            val renderer = LuauRenderer()
//            renderer.renderNode(luauFile)
            val code = luauFile.render()


//            val code = LuauPrinter.printFile(luauAst)

            val outFile = File(outputDir, irFile.nameWithoutExtension + ".luau")
            outFile.parentFile.mkdirs()
            outFile.writeText(code)
        }
    }
}