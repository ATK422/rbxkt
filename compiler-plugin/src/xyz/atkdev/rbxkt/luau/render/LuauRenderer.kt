package xyz.atkdev.rbxkt.luau.render

import xyz.atkdev.rbxkt.luau.*

class LuauRenderer {
    private var out = StringBuilder()
    private var indent = 0
    private fun indent() = "\t".repeat(indent)
    private fun <T> usingIndent(fn: () -> T): T {
        indent++
        val result = fn()
        indent--
        return result
    }

    private fun renderString(str: String) {
        out.append(str)
    }
    private fun renderStringIndented(str: String) {
        out.append("$indent()$str")
    }
    private fun renderLine(l: String) {
        out.appendLine("${indent()}$l")
    }

    public fun output(): String {
        return out.toString()
    }
    public fun renderNode(n: LuauNode) {
        when (n) {
            is LuauFile -> renderFile(n)
            is LuauComment -> renderComment(n)
            is LuauIdentifier -> renderIdentifier(n)
            is LuauBoolLiteral -> renderBoolLiteral(n)
            is LuauNumberLiteral -> renderNumberLiteral(n)
            is LuauStringLiteral -> renderStringLiteral(n)
            is LuauFunction -> renderFunction(n)
            is LuauParam -> null
            is LuauCall -> renderCall(n)
            is LuauNamecall -> null
            is LuauVarDecl -> renderVarDecl(n)
            is LuauAssign -> renderAssign(n)
        }
    }

    private fun renderFile(file: LuauFile) {
        for (directive in file.directives) {
            renderNode(directive)
        }

        renderLine("--// rbxkt v1.0")

        for (stmt in file.stmts) {
            renderNode(stmt)
        }
    }
    private fun renderComment(comment: LuauComment) {
        // TODO: Add multiline support
        renderLine(comment.comment)
    }
    private fun renderFunction(func: LuauFunction) {
        renderLine("function ${func.name}()")
        usingIndent {
            func.body.forEach { n ->
                renderNode(n)
            }
        }
        renderLine("end")
    }
    private fun renderCall(call: LuauCall) {
        val name = call.name
        val args = call.args.map { arg ->
            renderNode(arg)
        }.joinToString(", ")
        renderString("$name($args)")
    }
    private fun renderIdentifier(iden: LuauIdentifier) {
        renderString(iden.name)
    }
    private fun renderBoolLiteral(lit: LuauBoolLiteral) {
        renderString(lit.value.toString())
    }
    private fun renderNumberLiteral(lit: LuauNumberLiteral) {
        renderString(lit.value.toString())
    }
    private fun renderStringLiteral(lit: LuauStringLiteral) {
        renderString("\"${lit.value}\"")
    }
    private fun renderVarDecl(decl: LuauVarDecl) {
        val name = decl.name
        val init = decl.init
        if (init != null) {
            renderLine("local $name = ${renderNode(init)}")
        } else {
            renderLine("local $name")
        }
    }
    private fun renderAssign(assign: LuauAssign) {
        val value = renderNode(assign.value)
        renderLine("${assign.target} = $value")
    }
}
