//TODO: I threw this thing together to test, it probably doesn't work!
package xyz.atkdev.rbxkt.luau

import xyz.atkdev.rbxkt.ir.LuauCall
import xyz.atkdev.rbxkt.ir.LuauExpression
import xyz.atkdev.rbxkt.ir.LuauFile
import xyz.atkdev.rbxkt.ir.LuauFunction
import xyz.atkdev.rbxkt.ir.LuauNil
import xyz.atkdev.rbxkt.ir.LuauReturn
import xyz.atkdev.rbxkt.ir.LuauStatement
import kotlin.math.exp

class LuauPrinter {
    val builder = StringBuilder()
    private var indent = 0

    fun printFile(file: LuauFile): String {
        file.statements.forEach { printStatement(it) }
        return builder.toString()
    }

    private fun indent() = repeat(indent) { "   " }
    private fun newline() = builder.appendLine().append(indent())

    private fun printStatement(statement: LuauStatement) {
        when (statement) {
            is LuauFunction -> {
                builder.append("function ${statement.name}(${statement.params.joinToString()})")
                indent++
                statement.body.forEach { newline(); printStatement(it) }
                indent--
                newline().append("end")
            }
            is LuauReturn -> {
                builder.append("return ")
                printExpression(statement.expression)
            }

            else -> builder.appendLine("unknown statement: $statement")
        }
    }

    private fun printExpression(expression: LuauExpression) {
        when (expression) {
            is LuauNil -> builder.append("nil")
            is LuauCall -> {
                builder.append("${expression.name}(")
                expression.arguments.forEachIndexed { index, value ->
                    printExpression(value)
                    if (index != expression.arguments.lastIndex) builder.append(", ")
                }
                builder.append(")")
            }
            else -> builder.append("unknown expression: $expression")
        }
    }
}