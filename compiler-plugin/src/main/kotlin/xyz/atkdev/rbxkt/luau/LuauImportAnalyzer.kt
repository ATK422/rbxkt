package xyz.atkdev.rbxkt.luau

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.name.FqName
import xyz.atkdev.rbxkt.ir.PluginEnvironment
import xyz.atkdev.rbxkt.ir.getConstructorName
import xyz.atkdev.rbxkt.luau.LuauExportAnalyzer.pkgExports

object LuauImportAnalyzer : IrElementVisitorVoid {
    var srcRoot = "D:/Projects/IntelliJ/rbxkt-example"
    private var currentPkgName: String? = null
    private val importsMap: MutableMap<String, MutableList<String>> = mutableMapOf()

    fun analyze(file: IrFile): MutableMap<String, MutableList<String>> {
        currentPkgName = file.getPackageFragment()?.packageFqName!!.asString()
        file.accept(this, null)
        return importsMap
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildren(this, null)

        if (element is IrFile) return
        if (currentPkgName == null) return

        var pkgName: String? = null
        val exportName = when(element) {
            is IrCall -> {
                if (element.superQualifierSymbol != null) return
                if (element.dispatchReceiver != null) return

                val owner = element.symbol.owner
                val name = owner.kotlinFqName.asString().split(".").last()

                pkgName = owner.getPackageFragment().packageFqName!!.asString()
                name
            }
            else -> null
        }

        if (pkgName == null) return
        if (exportName == null) return
        val filePath = LuauExportAnalyzer.getFilePathByExportAndPkg(
            exportName,
            pkgName
        )?: return

        PluginEnvironment.logger.report(CompilerMessageSeverity.WARNING, filePath)
        PluginEnvironment.logger.report(CompilerMessageSeverity.WARNING, pkgName)
        PluginEnvironment.logger.report(CompilerMessageSeverity.WARNING, currentPkgName!!)

        if (
            !pkgName.startsWith("kotlin") &&
            currentPkgName!! != pkgName
        ) {
            val path = filePath.replace(srcRoot, "@").removeSuffix(".kt")
            val identifiers = importsMap.getOrPut(path) { mutableListOf() }
            identifiers.add(exportName)
        }
    }
}