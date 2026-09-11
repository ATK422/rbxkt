package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
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
    private val moduleKind: String? = null,
    private val sourceRoots: List<File> = emptyList(),
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        val files = moduleFragment.files
        if (files.isEmpty()) return

        require(moduleKind in setOf("client", "server", "shared")) {
            "Configure this compilation in rbxkt.moduleKinds as client, server, or shared"
        }
        val kind = moduleKind!!
        val roots = sourceRoots.map { it.toPath().toAbsolutePath().normalize() }
            .sortedByDescending { it.nameCount }
        val relativePaths = files.associateWith { file ->
            val path = File(file.path).toPath().toAbsolutePath().normalize()
            val root = roots.firstOrNull { path.startsWith(it) }
                ?: error("No configured source root contains ${file.path}")
            root.relativize(path).toString().replace(File.separatorChar, '/')
        }
        require(relativePaths.values.distinct().size == files.size) {
            "Source roots contain duplicate module paths for $kind"
        }
        val entrypoint = findEntrypoint(files, kind)

        PluginEnvironment.irPluginContext = pluginContext
        PluginEnvironment.irModuleFragment = moduleFragment

        LuauTypeSolver.irContext = pluginContext
        LuauExportAnalyzer.analyze(outputDir, files, kind,
            relativePaths.mapKeys { it.key.path }.mapValues { "src/$kind/${it.value}" })

        var failed = false
        val generated = files.mapNotNull { irFile ->
            try {
                irFile to LuauEmitter(pluginContext).emitFile(irFile)
            } catch (failure: UnsupportedLuauNode) {
                failed = true
                val offset = failure.element.startOffset
                val location = if (offset >= 0) CompilerMessageLocation.create(
                    irFile.path, irFile.fileEntry.getLineNumber(offset) + 1,
                    irFile.fileEntry.getColumnNumber(offset) + 1, null
                ) else CompilerMessageLocation.create(irFile.path)
                PluginEnvironment.logger.report(
                    CompilerMessageSeverity.ERROR,
                    "rbxkt: ${failure.message}. Luau generation stopped for this file. " +
                        "Rewrite this construct or report it with a minimal Kotlin example. " +
                        "IR node: ${failure.element::class.simpleName}.",
                    location
                )
                null
            }
        }
        if (failed) return

        outputDir.mkdirs()

        File(outputDir, kind).takeIf { it.isDirectory }?.deleteDirectoryContents()
        val launcher = File(outputDir, "main.$kind.luau")
        if (kind != "shared") launcher.delete()

        generated.forEach { (irFile, luauFile) ->
            val code = luauFile.render()

            val relativeDir = File(File(outputDir, kind), relativePaths.getValue(irFile)).parentFile
            relativeDir.mkdirs()

            val astBuilder = IndentedStringBuilder()
            luauFile.display(astBuilder)

            val fileName = irFile.nameWithoutExtension
            File(relativeDir, "$fileName.luau").writeText(code)
            File(relativeDir, "$fileName.luauast").writeText(astBuilder.toString())
            File(relativeDir, "$fileName.ir").writeText(irFile.dump())
        }
        entrypoint?.let { launcher.writeText(it.render(kind, relativePaths.getValue(it.file))) }
        writeRojoProject(outputDir)
    }
}
