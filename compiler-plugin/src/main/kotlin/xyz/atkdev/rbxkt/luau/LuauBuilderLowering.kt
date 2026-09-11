package xyz.atkdev.rbxkt.luau

import xyz.atkdev.rbxkt.util.IndentedStringBuilder

/** Statements at the current scope, without introducing a do/end block. */
data class LuauStatements(val statements: List<LuauStmt>) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) = statements.forEach { it.render(builder) }
    override fun display(builder: IndentedStringBuilder) = statements.forEach { it.display(builder) }
}

/** An expression with initialization. Its fallback keeps execution at the original expression site. */
data class LuauInitExpr(val statements: List<LuauStmt>, val value: LuauIdentifier) : LuauExpr {
    override fun render(): String {
        val builder = IndentedStringBuilder()
        builder.line("(function()")
        builder.indent {
            statements.forEach { it.render(builder) }
            builder.line("return ${value.render()}")
        }
        builder.append("end)()")
        return builder.toString()
    }
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauInitExpr value=${value.name}")
        builder.indent { statements.forEach { it.display(builder) } }
    }
}

/** Move builder initialization into statements only where evaluation order is explicit. */
class LuauBuilderLowering(private val freshName: () -> String) {
    private data class Expression(val before: List<LuauStmt>, val value: LuauExpr)

    fun lower(statements: List<LuauStmt>): List<LuauStmt> = statements.flatMap(::statement)

    private fun statement(stmt: LuauStmt): List<LuauStmt> = when (stmt) {
        is LuauStatements -> lower(stmt.statements)
        is LuauFunctionStmt -> listOf(stmt.copy(body = lower(stmt.body)))
        is LuauBlock -> listOf(stmt.copy(body = lower(stmt.body)))
        is LuauClass -> listOf(stmt.copy(
            initializer = stmt.initializer.copy(body = lower(stmt.initializer.body)),
            constructors = stmt.constructors.map { it.copy(body = lower(it.body)) },
            functions = stmt.functions.map { it.copy(body = lower(it.body)) }
        ))
        is LuauVarDecl -> {
            val init = stmt.init?.let(::expression)
            init?.before.orEmpty() + stmt.copy(init = init?.value)
        }
        is LuauAssign -> {
            val init = expression(stmt.value)
            when {
                init.before.isEmpty() -> listOf(stmt.copy(value = init.value))
                stmt.target.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) -> init.before + stmt.copy(value = init.value)
                stmt.target.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) -> {
                    val receiver = LuauIdentifier(freshName())
                    listOf(LuauVarDecl(receiver, "any", LuauIdentifier(stmt.target.substringBeforeLast('.')))) +
                        init.before + stmt.copy(target = "${receiver.name}.${stmt.target.substringAfterLast('.')}", value = init.value)
                }
                else -> listOf(stmt) // The textual target may itself evaluate arbitrary code.
            }
        }
        is LuauExprStmt -> {
            val init = expression(stmt.expr)
            init.before + if (stmt.expr is LuauInitExpr) emptyList() else listOf(stmt.copy(expr = init.value))
        }
        is LuauReturn -> if (stmt.args.all { it is LuauExpr }) {
            val (before, values) = ordered(stmt.args.map { it as LuauExpr })
            before + stmt.copy(args = values)
        } else listOf(stmt)
        is LuauWhileLoop -> listOf(stmt.copy(body = lower(stmt.body))) // Conditions execute every iteration.
        is LuauForLoop -> listOf(stmt.copy(init = lower(stmt.init), body = lower(stmt.body)))
        is LuauWhen -> listOf(stmt.copy(branches = stmt.branches.map {
            when (it) {
                is LuauBranch.Conditional -> it.copy(result = lower(it.result))
                is LuauBranch.Else -> it.copy(result = lower(it.result))
            }
        }))
    }

    private fun expression(expr: LuauExpr): Expression = when (expr) {
        is LuauInitExpr -> Expression(lower(expr.statements), expr.value)
        is LuauLambdaExpr -> Expression(emptyList(), expr.copy(body = lower(expr.body)))
        is LuauUnaryOp -> expression(expr.operand).let { it.copy(value = expr.copy(operand = it.value)) }
        is LuauNot -> expression(expr.operand).let { it.copy(value = expr.copy(operand = it.value)) }
        is LuauCastExpr -> expression(expr.expr).let { it.copy(value = expr.copy(expr = it.value)) }
        is LuauBinaryExpr -> when (expr.op) {
            "and", "or" -> expression(expr.left).let { it.copy(value = expr.copy(left = it.value)) }
            "+=", "-=", "*=", "/=", "%=" -> Expression(emptyList(), expr)
            else -> ordered(listOf(expr.left, expr.right)).let { (before, values) ->
                Expression(before, expr.copy(left = values[0], right = values[1]))
            }
        }
        is LuauCall -> {
            val (before, values) = ordered(expr.args)
            if (before.isEmpty()) Expression(before, expr.copy(args = values)) else {
                // Resolve the callee before any argument can mutate its binding.
                val callee = LuauIdentifier(freshName())
                Expression(listOf(LuauVarDecl(callee, "any", LuauIdentifier(expr.name))) + before,
                    expr.copy(name = callee.name, args = values))
            }
        }
        is LuauArrayLiteral -> ordered(expr.elements).let { (before, values) -> Expression(before, expr.copy(elements = values)) }
        else -> Expression(emptyList(), expr)
    }

    private fun ordered(expressions: List<LuauExpr>): Pair<List<LuauStmt>, List<LuauExpr>> {
        val lowered = expressions.map(::expression)
        val before = mutableListOf<LuauStmt>()
        val values = lowered.mapIndexed { index, item ->
            before += item.before
            if (lowered.drop(index + 1).any { it.before.isNotEmpty() } && !item.value.isLiteral()) {
                val saved = LuauIdentifier(freshName())
                before += LuauVarDecl(saved, "any", item.value)
                saved
            } else item.value
        }
        return before to values
    }

    private fun LuauExpr.isLiteral() = this is LuauNumberLiteral || this is LuauStringLiteral ||
        this is LuauBoolLiteral || this === LuauNilLiteral
}
