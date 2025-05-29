package xyz.atkdev.rbxkt.luau

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

data class LuauNamecall(val recv: String, val name: String, val args: List<LuauExpr>) : LuauExpr {
    override fun render() = ""
}

data class LuauConstructorCall(val name: String, val args: List<LuauExpr>) : LuauExpr {
    override fun render() = "$name.new(${args.joinToString(", ") { it.render() }})"
}

data class LuauConstructor(val name: String) : LuauExpr {
    override fun render() = "$name()"
}

data class LuauLambdaExpr(val params: List<LuauParameter>, val body: List<LuauStmt>) : LuauExpr {
    override fun render(): String {
        val builder = IndentedStringBuilder()
        builder.line("function(${params.joinToString(", ") { it.render() }})")
        builder.indent {
            for (stmt in body) {
                stmt.render(builder)
            }
        }
        builder.append("end")
        return builder.toString()
    }
}

data class LuauFunctionStmt(val name: String, val typeParams: List<String>, val params: List<LuauParameter>, val body: List<LuauStmt>, val returnType: String?) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        val typeParams = if (typeParams.isNotEmpty()) {
            "<${typeParams.joinToString(", ")}>"
        } else ""
        builder.line("function $name$typeParams(${params.joinToString(", ") { it.render() }})${if (returnType != null) ": $returnType" else ""}")
        builder.indent {
            for (stmt in body) {
                stmt.render(builder)
            }
        }
        builder.line("end")
    }
}

data class LuauClass(val name: String, val memberTypes: List<String>, val constructorExprs: List<LuauAssign>, val initStmts: List<LuauStmt>) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        val params = constructorExprs.joinToString(", ") { it.target }
        builder.line("type $name = {")
        builder.indent {
            builder.line("${memberTypes.joinToString(",\n") { it }}")
        }
        builder.line("}")

        builder.line("local $name")
        builder.line("$name = setmetatable({}, {")
        builder.indent {
            builder.line("__tostring = function()")
            builder.indent {
                builder.line("return \"$name\"")
            }
            builder.line("end")
        }
        builder.line("})")
        builder.line("$name.__index = $name")
        builder.line("function $name.new($params)")
        builder.indent {
            builder.line("local self = setmetatable({}, $name)")
            builder.line("self:constructor($params)")
            builder.line("self:init()")
            builder.line("return self")
        }
        builder.line("end")
        builder.line("function $name:constructor($params)")
        builder.indent {
            for (init in constructorExprs) {
                builder.line("self.${init.target} = ${init.value.render()}")
            }
        }
        builder.line("end")
        builder.line("function $name:init()")
        builder.indent {
            for (stmt in initStmts) {
                stmt.render(builder)
            }
        }
        builder.line("end")
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