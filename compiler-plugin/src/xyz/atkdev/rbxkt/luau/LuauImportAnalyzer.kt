package wtf.lynn.xyz.atkdev.rbxkt.luau

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.name.FqName
import wtf.lynn.xyz.atkdev.rbxkt.ir.getConstructorName

class LuauImportAnalyzer : IrElementVisitorVoid {
    var srcRoot = "D:/Projects/IntelliJ/rbxkt-example"
    private lateinit var currentFile: IrFile
    private lateinit var currentFilePath: String
    private val importsMap: MutableMap<String, MutableList<String>> = mutableMapOf()

    fun analyze(file: IrFile): MutableMap<String, MutableList<String>> {
        currentFile = file
        currentFilePath = file.path
        file.accept(this, null)
        return importsMap
    }

    fun getSymbol(element: IrElement): IrSymbol? {
        return element?.javaClass
            ?.methods?.firstOrNull { it.name == "getSymbol" }
            ?.invoke(element) as? IrSymbol
    }

    fun getFile(element: IrElement): IrFile? {
        var current: IrElement? = element

        while (current != null && current !is IrFile) {
            var symbol = getSymbol(current)?: break
            var owner = symbol.owner
            if (owner is IrDeclarationBase) {
                current = owner.parent
            } else {
                break
            }
        }

        return current as? IrFile
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildren(this, null)

        if (element is IrFile) return

        var file: IrFile? = null
        val fqName = when(element) {
            is IrCall -> {
                var owner = element.symbol.owner
                file = getFile(owner) ?: return

                if (element.superQualifierSymbol != null) return
                if (element.dispatchReceiver != null) return

                owner.kotlinFqName
            }
            is IrConstructorCall -> {
                var owner = element.symbol.owner
                val constructorName = getConstructorName(owner)
                file = getFile(owner) ?: return
                //FqName(owner.kotlinFqName.asString().replace("<init>", owner.name.asString()))
                FqName(owner.kotlinFqName.asString().substringBeforeLast("."))
            }
            else -> null
        }?: return
        if (file == null) return

        val name = fqName.asString()
        val filePath = file.path

        if (
            !name.startsWith("kotlin") &&
            currentFilePath != filePath
        ) {
            val parts = name.split('.')
            val identifier = parts.last()
            val path = filePath.replace(srcRoot, "@").removeSuffix(".kt")

            val identifiers = importsMap.getOrPut(path) { mutableListOf() }
            identifiers.add(identifier)
        }
    }
}