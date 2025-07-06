package xyz.atkdev.rbxkt

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

object ConfigurationKeys {
    val OUTPUT_DIR = CompilerConfigurationKey<String>("OUTPUT_DIR")
}

class RbxKtCommandLineProcessor() : CommandLineProcessor {
    override val pluginId: String = "com.rbxkt"
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "outputDir",
            valueDescription = "<path>",
            description = "Output directory for generated files",
            required = false,
            allowMultipleOccurrences = false,
        )
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            "outputDir" -> configuration.put(ConfigurationKeys.OUTPUT_DIR, value)
            else -> error("Unexpected config option ${option.optionName}")
        }
    }
}