package xyz.atkdev.rbxkt.luau

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid

object LuauImportAnalyzer : IrVisitorVoid() {
    lateinit var currentFilePath: String
    private val importsMap: MutableMap<String, MutableList<String>> = mutableMapOf()

    fun analyze(file: IrFile): MutableMap<String, MutableList<String>> {
        currentFilePath = file.path
        file.accept(this, null)
        return importsMap
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildren(this, null)

        if (element is IrFile) return

        val (exportName, pkgName) = when(element) {
            is IrCall -> {
                if (element.superQualifierSymbol != null) return
                if (element.dispatchReceiver != null) return

                val owner = element.symbol.owner
                val name = owner.kotlinFqName.asString().split(".").last()

                name to owner.getPackageFragment().packageFqName.asString()
            }
            else -> null to null
        }

        if (pkgName == null
            || exportName == null
            || pkgName.startsWith("kotlin")) return

        val filePath = LuauExportAnalyzer.getFilePathByExportAndPkg(
            exportName,
            pkgName
        )?: return

        if (currentFilePath == filePath) return

        val identifiers = importsMap.getOrPut(filePath) { mutableListOf() }
        identifiers.add(exportName)
    }
}

fun nameToService(name: String): String = when (name) {
    "shared" -> "ReplicatedStorage"
    "client" -> "ReplicatedStorage"
    "server" -> "ServerScriptService"
    else -> error("Expected name for service")
}