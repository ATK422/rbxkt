package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import xyz.atkdev.rbxkt.luau.*

class LuauEmitter(private val context: IrPluginContext): IrElementVisitor<LuauNode?, Nothing?> {
    fun emitFile(irFile: IrFile): LuauFile {
        val comments = listOf(
            LuauComment("--!optimize 2", false),
            LuauComment("--!native", false)
        )
        val statements = irFile.declarations.mapNotNull { it.accept(this, null) }
        return LuauFile(comments, statements)
    }

    override fun visitElement(element: IrElement, data: Nothing?): LuauNode? {
        error("Unhandled element: ${element::class.simpleName}")
        //println("Unhandled element: ${element::class.simpleName}")
        //return null
    }

    override fun visitCall(expression: IrCall, data: Nothing?): LuauNode? {
        // TODO: implement overloading and receivers/methods
        val name = expression.symbol.owner.name.asString()
        val args = expression.valueArguments.mapNotNull { arg ->
            arg?.accept(this, data) as? LuauExpr
        }

        return LuauCall(name, args)
    }

    override fun visitFunction(declaration: IrFunction, data: Nothing?): LuauNode? {
        val name = declaration.name.asString()
        var params = declaration.valueParameters.map {
            LuauParameter(LuauIdentifier(it.name.asString()), it.type.toString())
        }
        val body: List<LuauNode> = (declaration.body as? IrBlockBody)?.statements
            ?.mapNotNull { it.accept(this, null) }
            ?: emptyList()

        return LuauFunctionStmt(name, params, body)
    }

    override fun visitConst(expression: IrConst, data: Nothing?): LuauNode? = when(val value = expression.value) {
        is String -> LuauStringLiteral(value)
        is Int, is UInt, is Double, is Float -> LuauNumberLiteral(value as Number)
        is Boolean -> LuauBoolLiteral(value)
        else -> error("Unsupported constant type: ${value?.let { it::class.simpleName }}")
    }

    override fun visitVariable(declaration: IrVariable, data: Nothing?): LuauNode? {
        val name = declaration.name.asString()
        val init = declaration.initializer?.accept(this, data) as LuauExpr?
        return LuauVarDecl(name, "?", init)
    }

    override fun visitGetValue(expression: IrGetValue, data: Nothing?): LuauNode? {
        val name = expression.symbol.owner.name.asString()
        return LuauIdentifier(name)
    }

    override fun visitSetValue(expression: IrSetValue, data: Nothing?): LuauNode? {
        val name = expression.symbol.owner.name.asString()
        val value = expression.value.accept(this, data) as LuauExpr
        return LuauAssign(name, value)
    }
}