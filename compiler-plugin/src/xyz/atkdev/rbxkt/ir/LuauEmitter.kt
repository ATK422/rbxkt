package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.ir.constantValue
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.util.statements

sealed interface LuauNode
sealed interface LuauStatement : LuauNode
sealed interface LuauExpression : LuauNode

data class LuauFile(val statements: List<LuauStatement>) : LuauNode

data class LuauFunction(val name: String, val params: List<String>, val body: List<LuauStatement>) : LuauStatement
data class LuauReturn(val expression: LuauExpression) : LuauStatement
data class LuauExpressionStatement(val expression: LuauExpression) : LuauStatement
data class LuauVariable(val name: String, val expression: LuauExpression) : LuauStatement

data class LuauStringLiteral(val value: String) : LuauExpression
data class LuauCall(val name: String, val arguments: List<LuauExpression>) : LuauExpression
data class LuauSetVariable(val name: String, val expression: LuauExpression) : LuauExpression
data class LuauGetVariable(val name: String) : LuauExpression
data class LuauBinaryExpression(val left: LuauExpression, val right: LuauExpression, val operator: String) : LuauExpression
class LuauNil() : LuauExpression

class LuauEmitter(private val context: IrPluginContext) {
    fun emitFile(irFile: IrFile): LuauFile {
        val statements = irFile.declarations.flatMap(::emitDeclaration)
        return LuauFile(statements)
    }

    private fun emitDeclaration(irDeclaration: IrDeclaration): List<LuauStatement> = when (irDeclaration) {
        is IrFunction -> emitFunction(irDeclaration)
        is IrProperty -> listOf(LuauVariable(irDeclaration.name.asString(), emitExpression(irDeclaration.backingField?.initializer?.expression)))
        is IrField -> listOf(LuauVariable(irDeclaration.name.asString(), emitExpression(irDeclaration.initializer?.expression)))
        else -> emptyList()
    }

    private fun emitExpression(irExpression: IrExpression?): LuauExpression = when(irExpression) {
        is IrConst -> LuauStringLiteral(irExpression.value.toString())
        is IrCall -> LuauCall(irExpression.symbol.owner.name.asString(), irExpression.valueArguments.mapNotNull { emitExpression(it) })
        is IrGetValue -> LuauGetVariable(irExpression.symbol.owner.name.asString())
        is IrSetValue -> LuauSetVariable(irExpression.symbol.owner.name.asString(), emitExpression(irExpression.value))
        null -> LuauNil()
        else -> error("Unexpected expression: $irExpression")
    }

    private fun emitStatement(irStatement: IrStatement): List<LuauStatement> = when (irStatement) {
        is IrFunction -> emitFunction(irStatement)
        is IrReturn -> listOf(LuauReturn(emitExpression(irStatement.value)))
        is IrBlock -> irStatement.statements.flatMap(::emitStatement)
        is IrExpression -> listOf(LuauExpressionStatement(emitExpression(irStatement)))
        is IrVariable -> listOf(LuauVariable(irStatement.name.asString(), emitExpression(irStatement.initializer)))
        else -> emptyList()
    }

    private fun emitFunction(irFunction: IrFunction): List<LuauFunction> {
        val name = irFunction.name.asString()
        val params = irFunction.valueParameters.map { it.name.asString() }
        val bodyStatements = irFunction.body?.statements?.flatMap(::emitStatement) ?: emptyList()
        return listOf(LuauFunction(name, params, bodyStatements))
    }
}