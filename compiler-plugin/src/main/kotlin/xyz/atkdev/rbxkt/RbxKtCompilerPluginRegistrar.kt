package xyz.atkdev.rbxkt

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.incremental.deleteDirectoryContents
import xyz.atkdev.rbxkt.fir.RbxKtCheckerExtension
import xyz.atkdev.rbxkt.ir.LuauIrGenerationExtension
import xyz.atkdev.rbxkt.ir.PluginEnvironment
import java.io.File

class RbxKtCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2 = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val outDir = configuration.get(ConfigurationKeys.OUTPUT_DIR)?.let { File(it) } ?: File("build/generated/luau")
        PluginEnvironment.logger = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

        FirExtensionRegistrarAdapter.registerExtension(object : FirExtensionRegistrar() {
            override fun ExtensionRegistrarContext.configurePlugin() {
                +::RbxKtCheckerExtension
            }
        })

        IrGenerationExtension.registerExtension(
            LuauIrGenerationExtension(outDir)
        )
    }
}
