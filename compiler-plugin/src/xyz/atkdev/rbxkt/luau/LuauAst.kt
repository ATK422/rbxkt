package xyz.atkdev.rbxkt.luau

import wtf.lynn.xyz.atkdev.rbxkt.util.IndentedStringBuilder

sealed interface LuauNode {
    fun display(builder: IndentedStringBuilder)
}
sealed interface LuauExpr : LuauNode {
    fun render(): String
}
sealed interface LuauStmt : LuauNode {
    fun render(builder: IndentedStringBuilder)
}

data class LuauExprStmt(val expr: LuauExpr) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        builder.line(expr.render())
    }

    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauExprStmt")
        builder.indent { expr.display(builder) }
    }
}

data class LuauFile(val name: String, val directives: List<LuauComment>, val stmts: List<LuauStmt>) : LuauNode {
    var imports: MutableMap<String, MutableList<String>> = mutableMapOf()
    var exports: MutableList<LuauIdentifier> = mutableListOf()

    fun render(): String {
        val builder = IndentedStringBuilder()

        for (directive in directives) {
            builder.line(directive.render())
        }

        for ((importPath, identifiers) in imports) {
            val moduleName = importPath.substringAfterLast('/')
            builder.line("local $moduleName = require($importPath)")
            for (identifier in identifiers) {
                builder.line("local $identifier = $moduleName.$identifier")
            }
        }

        for (stmt in stmts) {
            stmt.render(builder)
        }

        builder.line("return {")
        builder.indent {
            for (export in exports) {
                val name = export.render()
                builder.line("${name} = ${name}")
            }
        }
        builder.line("}")

        return builder.toString()
    }

    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauFile name=$name")
        builder.indent {
            builder.line("directives")
            builder.indent { directives.forEach { it.display(builder) } }
            builder.line("statements")
            builder.indent { stmts.forEach { it.display(builder) } }
        }
    }
}

data class LuauIdentifier(val name: String) : LuauExpr {
    override fun render() = name
    override fun display(builder: IndentedStringBuilder) { builder.line("LuauIdentifier name=$name") }
}

data class LuauBoolLiteral(val value: Boolean) : LuauExpr {
    override fun render() = value.toString()
    override fun display(builder: IndentedStringBuilder) { builder.line("LuauBoolLiteral value=$value") }
}

data class LuauNumberLiteral(val value: Number) : LuauExpr {
    override fun render() = value.toString()
    override fun display(builder: IndentedStringBuilder) { builder.line("LuauNumberLiteral value=$value") }
}

data class LuauStringLiteral(val value: String) : LuauExpr {
    override fun render() = "\"$value\""
    override fun display(builder: IndentedStringBuilder) { builder.line("LuauStringLiteral value=$value") }
}

data class LuauInterpolatedStringLiteral(val args: List<LuauExpr>) : LuauExpr {
    override fun render() = "`${args.joinToString("") { when(it) {
        is LuauStringLiteral -> it.value
        else -> "{${it.render()}}"
    }}}`"
    override fun display(builder: IndentedStringBuilder) { builder.line("LuauInterpolatedStringLiteral args=$args") }
}

data class LuauParameter(val name: LuauIdentifier, val type: String) : LuauExpr {
    override fun render() = "${name.render()}: $type"
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauParameter type=$type")
        builder.indent { name.display(builder) }
    }
}

data class LuauComment(val comment: String, val multiline: Boolean) : LuauExpr {
    override fun render() = if (multiline) "--[[\n$comment\n]]" else "--$comment"
    override fun display(builder: IndentedStringBuilder) { builder.line("LuauComment comment=$comment multiline=$multiline") }
}

data class LuauBinaryExpr(val left: LuauExpr, val op: String, val right: LuauExpr) : LuauExpr {
    override fun render() = "${left.render()} $op ${right.render()}"
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauBinaryExpr")
        builder.indent {
            left.display(builder)
            builder.append("op=$op")
            right.display(builder)
        }
    }
}

data class LuauCall(val name: String, val args: List<LuauExpr>) : LuauExpr {
    override fun render() = "$name(${args.joinToString(", ") { it.render() }})"
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauCall")
        builder.indent {
            builder.line("name=$name")
            builder.line("Arguments")
            builder.indent { args.forEach { it.display(builder) } }
        }
    }
}

data class LuauNamecall(val recv: String, val name: String, val args: List<LuauExpr>) : LuauExpr {
    override fun render() = ""
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauNamecall")
        builder.indent {
            builder.line("recv=$recv")
            builder.line("name=$name")
            builder.line("Arguments")
            builder.indent { args.forEach { it.display(builder) } }
        }
    }
}

data class LuauConstructorCall(val name: String, val args: List<LuauExpr>) : LuauExpr {
    override fun render() = "$name(${args.joinToString(", ") { it.render() }})"
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauConstructorCall name=$name")
        builder.indent {
            builder.line("Arguments")
            builder.indent { args.forEach { it.display(builder) } }
        }
    }
}

data class LuauConstructor(val name: String) : LuauExpr {
    override fun render() = "$name()"
    override fun display(builder: IndentedStringBuilder) { builder.line("LuauConstructor name=$name") }
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
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauLambdaExpr")
        builder.indent {
            builder.line("Parameters")
            builder.indent { params.forEach { it.display(builder) } }
            builder.line("Body")
            builder.indent { body.forEach { it.display(builder) } }
        }
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
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauFunctionStmt name=$name")
        builder.indent {
            builder.line("Parameters")
            builder.indent { params.forEach { it.display(builder) } }
            builder.line("Body")
            builder.indent { body.forEach { it.display(builder) } }
        }
    }
}

data class LuauClass(
    val name: String,
    val memberTypes: List<String>,
    val initializer: LuauFunctionStmt,
    val constructors: List<LuauFunctionStmt>,
    val functions: List<LuauFunctionStmt>
) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
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

        initializer.render(builder)

        for (constructor in constructors) {
            constructor.render(builder)
        }

        for (function in functions) {
            function.render(builder)
        }
    }
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauClass name=$name")
        builder.indent {
            builder.line("Initializer")
            builder.indent { initializer.display(builder) }
            builder.line("Constructors")
            builder.indent { constructors.forEach { it.display(builder) } }
            builder.line("Functions")
            builder.indent { functions.forEach { it.display(builder) } }
        }
    }
}

data class LuauVarDecl(val name: String, val type: String, val init: LuauExpr?) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        builder.line("local $name: $type = ${init?.render() ?: "nil"}")
    }
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauVarDecl name=$name type=$type")
        builder.indent { init?.display(builder) }
    }
}

data class LuauAssign(val target: String, val value: LuauExpr) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        builder.line("$target = ${value.render()}")
    }
    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauAssign target=$target}")
        builder.indent { value.display(builder) }
    }
}

data class LuauReturn(val args: List<LuauExpr>) : LuauStmt {
    override fun render(builder: IndentedStringBuilder) {
        builder.line("return ${args.joinToString(", ") { it.render() }}")
    }

    override fun display(builder: IndentedStringBuilder) {
        builder.line("LuauReturn")
        builder.indent { args.forEach { it.display(builder) } }
    }
}