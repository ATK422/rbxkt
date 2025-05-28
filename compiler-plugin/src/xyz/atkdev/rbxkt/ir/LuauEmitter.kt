package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.util.statements

sealed interface LuauNode
sealed interface LuauStatement : LuauNode
sealed interface LuauExpression : LuauNode

data class LuauFile(val statements: List<LuauStatement>) : LuauNode

data class LuauFunction(val name: String, val params: List<String>, val body: List<LuauStatement>) : LuauStatement
data class LuauReturn(val expression: LuauExpression) : LuauStatement
data class LuauExpressionStatement(val expression: LuauExpression) : LuauStatement

data class LuauStringLiteral(val value: String) : LuauExpression

class LuauEmitter(private val context: IrPluginContext) {
    fun emitFile(irFile: IrFile): LuauFile {
        val statements = irFile.declarations.flatMap { emitDeclaration(it) }
        return LuauFile(statements)
    }

    private fun emitDeclaration(irDeclaration: IrDeclaration): List<LuauStatement> = when (irDeclaration) {
        is IrFunction -> emitFunction(irDeclaration)
        else -> emptyList()
    }

    private fun emitExpression(irExpression: IrExpression): LuauExpression = when(irExpression) {
        is IrConst -> LuauStringLiteral(irExpression.value.toString())
//        is IrCall
        else -> error("Unexpected expression: $irExpression")
    }

    private fun emitStatement(irStatement: IrStatement): List<LuauStatement> = when (irStatement) {
        is IrFunction -> emitFunction(irStatement)
        is IrReturn -> listOf(LuauReturn(emitExpression(irStatement.value)))
        is IrBlock -> irStatement.statements.flatMap { emitStatement(it) }
        is IrExpression -> listOf(LuauExpressionStatement(emitExpression(irStatement)))
        else -> emptyList()
    }

    private fun emitFunction(irFunction: IrFunction): List<LuauFunction> {
        val name = irFunction.name.asString()
        val params = irFunction.valueParameters.map { it.name.asString() }
        val bodyStatements = irFunction.body?.let { it.statements.flatMap { emitStatement(it) } } ?: emptyList()
        return listOf(LuauFunction(name, params, bodyStatements))
    }
}