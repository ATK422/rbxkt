package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.backend.js.utils.nameWithoutExtension
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.*
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
            val code = luauFile.render()

            val astOut = File(outputDir, irFile.nameWithoutExtension + ".luauast")
            val luauOut = File(outputDir, irFile.nameWithoutExtension + ".luau")
            val irOut = File(outputDir, irFile.nameWithoutExtension + ".ir")
            luauOut.parentFile.mkdirs()
            luauOut.writeText(code)
            astOut.writeText(luauFile.toString())
            irOut.writeText(irFile.dumpKotlinLike(
                KotlinLikeDumpOptions(

                )
            ))
        }
    }
}