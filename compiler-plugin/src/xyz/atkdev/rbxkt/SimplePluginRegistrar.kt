package xyz.atkdev.rbxkt

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import xyz.atkdev.rbxkt.fir.SimpleClassGenerator

class SimplePluginRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::SimpleClassGenerator
    }
}
