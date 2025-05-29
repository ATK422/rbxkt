package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNumber
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
//import org.jetbrains.kotlin.ir.visitors.IrVisitor
import xyz.atkdev.rbxkt.luau.*

class LuauEmitter(private val context: IrPluginContext): IrElementVisitor<LuauNode?, Nothing?> {
    fun emitFile(irFile: IrFile): LuauFile {
        val comments = listOf(
            LuauComment("!optimize 2", false),
            LuauComment("!native", false)
        )
        val statements: List<LuauStmt> = irFile.declarations.mapNotNull { it.accept(this, null) as LuauStmt? }
        return LuauFile(comments, statements)
    }

    override fun visitElement(element: IrElement, data: Nothing?): LuauNode? {
        error("Unhandled element: ${element::class.simpleName}")
        //println("Unhandled element: ${element::class.simpleName}")
        //return null
    }

    override fun visitCall(expression: IrCall, data: Nothing?): LuauNode? {
        // TODO: implement overloading
        val receiver = expression.dispatchReceiver
            ?.accept(this, null) as? LuauExpr

        val name = expression.symbol.owner.name.asString()
        val args = expression.valueArguments
            .mapNotNull { it?.accept(this, data) as? LuauExpr }

        val isNumericCall = receiver != null && expression.dispatchReceiver!!.type.run {
            isNumber() || isInt() || isDouble() || isFloat() || isByte() || isShort() || isLong()
        }

        if (isNumericCall) {
            val op = when (name) {
                "plus" -> "+"
                "minus" -> "-"
                "times" -> "*"
                "div" -> "/"
                "rem" -> "%"
                else -> error("Unhandled numeric operator: $name")
            }

            val rhs = args.getOrNull(0) ?: error("Expected rhs for numeric operator")
            return LuauBinaryExpr(receiver!!, op, rhs)
        }

        return LuauCall(name, args)
    }

    override fun visitFunction(declaration: IrFunction, data: Nothing?): LuauFunctionStmt? {
        val name = declaration.name.asString()
        var params = declaration.valueParameters.map {
            LuauParameter(LuauIdentifier(it.name.asString()), LuauTypeSolver.fromIr(it.type))
        }
        val body: List<LuauStmt> = (declaration.body as? IrBlockBody)?.statements
            ?.mapNotNull { it.accept(this, null) as LuauStmt? }
            ?: emptyList()

        return LuauFunctionStmt(name, params, body, LuauTypeSolver.fromIr(declaration.returnType))
    }

    // LambdaStmt is technically a LuauFunctionExpr and not a Stmt
    // so maybe revisit this in the future
    // TODO: Needs a return type
    override fun visitFunctionExpression(expression: IrFunctionExpression, data: Nothing?): LuauLambdaStmt? {
        val params = expression.function.valueParameters.map {
            LuauParameter(LuauIdentifier(it.name.asString()), LuauTypeSolver.fromIr(it.type))
        }
        val body = expression.function.body?.statements
            ?.mapNotNull { it.accept(this, null) as LuauStmt? }
            ?: emptyList()

        return LuauLambdaStmt(params, body)
    }

    override fun visitConst(expression: IrConst, data: Nothing?): LuauExpr? = when(val value = expression.value) {
        is String -> LuauStringLiteral(value)
        is Int, is UInt, is Double, is Float -> LuauNumberLiteral(value as Number)
        is Boolean -> LuauBoolLiteral(value)
        else -> error("Unsupported constant type: ${value?.let { it::class.simpleName }}")
    }

    override fun visitVariable(declaration: IrVariable, data: Nothing?): LuauVarDecl? {
        val name = declaration.name.asString()
        val init = declaration.initializer?.accept(this, data) as LuauExpr?
        return LuauVarDecl(name, LuauTypeSolver.fromIr(declaration.type), init)
    }

    override fun visitGetValue(expression: IrGetValue, data: Nothing?): LuauIdentifier? {
        val name = expression.symbol.owner.name.asString()
        return LuauIdentifier(name)
    }

    override fun visitSetValue(expression: IrSetValue, data: Nothing?): LuauAssign? {
        val name = expression.symbol.owner.name.asString()
        val value = expression.value.accept(this, data) as LuauExpr
        return LuauAssign(name, value)
    }

    override fun visitReturn(expression: IrReturn, data: Nothing?): LuauReturn? {
        val args = expression.value.accept(this, data) as LuauExpr
        return LuauReturn(listOf(args))
    }

//    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?): LuauBinaryExpr {
//        return LuauBinaryExpr(expression.argument.accept(this, data) as LuauExpr, expression.operator.toString(), expression.typeOperand.)
//    }
}