package xyz.atkdev.rbxkt.luau

import xyz.atkdev.rbxkt.luau.IndentedStringBuilder

sealed interface LuauNode
sealed interface LuauExpr : LuauNode {
    fun render(): String
}
sealed interface LuauStmt : LuauNode {
    fun render(builder: IndentedStringBuilder)
}

data class LuauFile(val directives: List<LuauComment>, val stmts: List<LuauStmt>) : LuauNode {
    fun render(): String {
        val builder = IndentedStringBuilder()

        for (directive in directives) {
            builder.line(directive.render())
        }

        for (stmt in stmts) {
            stmt.render(builder)
        }

        return builder.toString()
    }
}

data class LuauIdentifier(val name: String) : LuauExpr {
    override fun render() = name
}

data class LuauBoolLiteral(val value: Boolean) : LuauExpr {
    override fun render() = value.toString()
}

data class LuauNumberLiteral(val value: Number) : LuauExpr {
    override fun render() = value.toString()
}

data class LuauStringLiteral(val value: String) : LuauExpr {
    override fun render() = "\"$value\""
}

data class LuauParameter(val name: LuauIdentifier, val type: String) : LuauExpr {
    override fun render() = "${name.render()}: $type"
}

data class LuauComment(val comment: String, val multiline: Boolean) : LuauExpr {
    override fun render() = if (multiline) "--[[\n$comment\n]]" else "--$comment"
}

data class LuauBinaryExpr(val left: LuauExpr, val op: String, val right: LuauExpr) : LuauExpr {
    override fun render() = "${left.render()} $op ${right.render()}"
}

data class LuauCall(val name: String, val args: List<LuauExpr>) : LuauExpr {
    override fun render() = "$name(${args.joinToString(", ") { it.render() }})"
}

data class LuauLambdaExpr(val params: List<LuauParameter>, val body: List<LuauStmt>) : LuauExpr {
    // Hello vro
    override fun render() = ""
}

data class LuauFunctionStmt(val name: String, val params: List<LuauParameter>, val body: List<LuauStmt>, val returnType: String?) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        builder.line("function $name(${params.joinToString(", ") { it.render() }})${if (returnType != null) ": $returnType" else ""}")
        builder.indent {
            for (stmt in body) {
                stmt.render(builder)
            }
        }
        builder.line("end")
    }
}

data class LuauNamecall(val recv: String, val name: String, val args: List<LuauExpr>) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        builder.line("name call Lol!")
    }
}
data class LuauVarDecl(val name: String, val type: String, val init: LuauExpr?) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        builder.line("local $name: $type = ${init?.render() ?: "nil"}")
    }
}
data class LuauAssign(val target: String, val value: LuauExpr) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        builder.line("$target = ${value.render()}")
    }
}
data class LuauReturn(val args: List<LuauExpr>) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        builder.line("return ${args.joinToString(", ") { it.render() }}")
    }
}