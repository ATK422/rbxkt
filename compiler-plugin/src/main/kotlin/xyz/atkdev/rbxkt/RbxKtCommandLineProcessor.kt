package xyz.atkdev.rbxkt

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

object ConfigurationKeys {
    val OUTPUT_DIR = CompilerConfigurationKey<String>("OUTPUT_DIR")
    val MODULE_KIND = CompilerConfigurationKey<String>("MODULE_KIND")
    val SOURCE_ROOTS = CompilerConfigurationKey<List<String>>("SOURCE_ROOTS")
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
        ),
        CliOption("moduleKind", "client|server|shared", "Execution side of this compilation",
            required = false,
            allowMultipleOccurrences = false
        ),
        CliOption("sourceRoot", "<path>", "Kotlin source root of this compilation",
            required = false,
            allowMultipleOccurrences = true
        )
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            "outputDir" -> configuration.put(ConfigurationKeys.OUTPUT_DIR, value)
            "moduleKind" -> configuration.put(ConfigurationKeys.MODULE_KIND, value)
            "sourceRoot" -> configuration.put(ConfigurationKeys.SOURCE_ROOTS,
                configuration.get(ConfigurationKeys.SOURCE_ROOTS).orEmpty() + value)
            else -> error("Unexpected config option ${option.optionName}")
        }
    }
}
