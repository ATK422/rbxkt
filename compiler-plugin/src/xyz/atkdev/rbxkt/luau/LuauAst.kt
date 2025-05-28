package xyz.atkdev.rbxkt.luau

sealed class LuauNode
sealed class LuauExpr : LuauNode()
sealed class LuauStmt : LuauNode()

data class LuauIdentifier(val name: String) : LuauExpr()
data class LuauBoolLiteral(val value: Boolean) : LuauExpr()
data class LuauNumberLiteral(val value: Number) : LuauExpr()
data class LuauStringLiteral(val value: String) : LuauExpr()
data class LuauFunctionExpr(val params: List<LuauParameter>, val stmts: List<LuauStmt>) : LuauExpr()

data class LuauFile(val directives: List<LuauComment>, val stmts: List<LuauNode>) : LuauNode()
data class LuauComment(val comment: String, val multiline: Boolean) : LuauNode()
data class LuauParameter(val name: LuauIdentifier, val type: String) : LuauNode()

data class LuauFunctionStmt(val name: String, val params: List<LuauParameter>, val body: List<LuauNode>) : LuauStmt()
data class LuauCall(val name: String, val args: List<LuauExpr>) : LuauStmt()
data class LuauNamecall(val recv: String, val name: String, val args: List<LuauExpr>) : LuauStmt()
data class LuauVarDecl(val name: String, val type: String, val init: LuauExpr?) : LuauStmt()
data class LuauAssign(val target: String, val value: LuauExpr) : LuauStmt()