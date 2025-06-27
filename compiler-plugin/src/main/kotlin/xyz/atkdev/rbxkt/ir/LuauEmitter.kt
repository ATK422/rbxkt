package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import xyz.atkdev.rbxkt.luau.LuauImportAnalyzer
import xyz.atkdev.rbxkt.luau.*

class LuauEmitter(private val context: IrPluginContext): IrVisitor<LuauNode, Nothing?>() {
    private lateinit var currentClass: IrClass

    private fun lowerToStmt(node: LuauNode): LuauStmt {
        if (node is LuauExpr) {
            return LuauExprStmt(node)
        }
        return node as LuauStmt
    }

    fun emitFile(irFile: IrFile): LuauFile {
        val comments = listOf(
            LuauComment("!optimize 2", false),
            LuauComment("!native", false)
        )

        val statements: List<LuauStmt> = irFile.declarations.map {
            lowerToStmt(it.accept(this, null))
        }

        val file = LuauFile(irFile.kotlinFqName.asString(), comments, statements)
        file.exports = LuauExportAnalyzer.getExportsForFile(irFile)
        file.imports = LuauImportAnalyzer.analyze(irFile)

        return file
    }

    override fun visitElement(element: IrElement, data: Nothing?): LuauNode {
        error("Unhandled element: ${element::class.simpleName}")
    }

    override fun visitCall(expression: IrCall, data: Nothing?): LuauNode {
        // TODO: implement overloading
        val receiver = expression.dispatchReceiver
            ?.accept(this, null) as? LuauExpr

        val owner = expression.symbol.owner
        val name = owner.name.asString()
        val args = expression.arguments
            .mapNotNull { it?.accept(this, data) as? LuauExpr }

        val isSetterGetter = owner.correspondingPropertySymbol != null
        val isSuperCall = expression.superQualifierSymbol != null
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
                "rangeTo" -> ""
                else -> error("Unhandled numeric operator: $name")
            }

            val rhs = args.getOrNull(0) ?: error("Expected rhs for numeric operator")
            return LuauBinaryExpr(receiver!!, op, rhs)
        } else if (isSuperCall) {
            return LuauNamecall("self.super", name, args)
        } else if (isSetterGetter) {
            val property = owner.correspondingPropertySymbol?.owner!!
            val isSetter = owner == property.setter
            val isGetter = owner == property.getter
            var receiverName = (expression.dispatchReceiver as IrValueAccessExpression).symbol.owner.name.asString()
            if (receiverName == "<this>") {
                receiverName = "self"
            }

            val name = getSetterGetterName(owner)
            val isDefault = isDefaultSetterGetter(owner)

            if (isDefault) {
                if (isSetter) {
                    val value = args.first()
                    return LuauAssign("$receiverName.$name", value)
                } else if (isGetter) {
                    return LuauIdentifier("$receiverName.$name")
                }
            } else {
                var args: List<LuauExpr> = listOf()
                if (isSetter) {
                    args = listOf(args.first())
                }
                return LuauNamecall(receiverName, name, args)
            }
        }

        if (expression.dispatchReceiver != null) {
            val symbol = expression.dispatchReceiver?.javaClass
                ?.methods?.firstOrNull { it.name == "getSymbol" }
                ?.invoke(expression.dispatchReceiver) as? IrSymbol
            val owner = symbol?.owner as IrDeclarationWithName
            return LuauCall(owner.name.asString(), args)
        }

        //possibly error on normal print
        if (name == "println" && owner.getPackageFragment().packageFqName.asString() == "kotlin.io") return LuauCall("print", args)

        return LuauCall(name, args)
    }

    override fun visitFunction(declaration: IrFunction, data: Nothing?): LuauFunctionStmt {
        var name = declaration.name.asString()
        if (declaration.dispatchReceiverParameter != null) {
            val receiverName = declaration.dispatchReceiverParameter?.type?.classOrNull?.owner?.name?.asString()
            name = "$receiverName:$name"
        }
        val typeParams = declaration.typeParameters.map {
            it.name.asString()
        }
        val params = declaration.parameters.map {
            LuauParameter(LuauIdentifier(it.name.asString()), LuauTypeSolver.fromIr(it.type))
        }
        val body: List<LuauStmt> = (declaration.body as? IrBlockBody)?.statements
            ?.map { lowerToStmt(it.accept(this, null)) }
            ?: emptyList()

        return LuauFunctionStmt(name, typeParams, params, body, LuauTypeSolver.fromIr(declaration.returnType))
    }

    // TODO: Needs a return type
    override fun visitFunctionExpression(expression: IrFunctionExpression, data: Nothing?): LuauLambdaExpr {
        val params = expression.function.parameters.map {
            LuauParameter(LuauIdentifier(it.name.asString()), LuauTypeSolver.fromIr(it.type))
        }
        val body = expression.function.body?.statements
            ?.map { lowerToStmt(it.accept(this, null)) }
            ?: emptyList()

        return LuauLambdaExpr(params, body)
    }

    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: Nothing?): LuauNamecall {
        return LuauNamecall("self", "init", listOf())
    }

    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: Nothing?): LuauExpr {
        val currentClassName = currentClass.name.asString()
        val targetConstructor = expression.symbol.owner
        val targetClassName = (targetConstructor.parent as IrClass).name.asString()
        val args = expression.arguments
            .mapNotNull { it?.accept(this, data) as? LuauExpr }
        return if (targetClassName == "Any") {
            LuauNoOp
        } else {
            if (currentClassName == targetClassName) {
                LuauStmtExpr(LuauReturn(listOf(LuauNamecall("self", "constructor", args))))
            } else {
                LuauNamecall("self.super", "constructor", args)
            }
        }
    }

    override fun visitConstructor(declaration: IrConstructor, data: Nothing?): LuauFunctionStmt {
        val clazz = (declaration.parent as IrClass)
        val className = clazz.name.asString()
        val superClass = clazz.superTypes
            .mapNotNull { it.classifierOrNull as? IrClassSymbol }
            .map { it.owner }
            .firstOrNull { it.fqNameWhenAvailable?.asString() != "kotlin.Any" }

        val name = "$className:${getConstructorName(declaration)}"
        val typeParameters = declaration.typeParameters.map {
            it.name.asString()
        }
        val params = declaration.parameters.map {
            LuauParameter(LuauIdentifier(it.name.asString()), LuauTypeSolver.fromIr(it.type))
        }
        var body = declaration.body?.statements?.map {
            lowerToStmt(it.accept(this, null))
        }?: emptyList()
        val returnType = LuauTypeSolver.fromIr(declaration.returnType)
        if (declaration.isPrimary) {
            if (superClass != null) {
                val superClassName = superClass.name.asString()
                body = listOf(LuauVarDecl(LuauIdentifier("self"), className, LuauCall("setmetatable", listOf(
                    LuauIdentifier("{}"),
                    LuauIdentifier(className)
                ))), LuauAssign("self.super", LuauCall("setmetatable", listOf(
                    LuauIdentifier("{}"),
                    LuauIdentifier(superClassName)
                )))) + body
            } else {
                body = listOf(LuauVarDecl(LuauIdentifier("self"), className, LuauCall("setmetatable", listOf(
                    LuauIdentifier("{}"),
                    LuauIdentifier(className)
                )))) + body
            }
            body = body + listOf(
                LuauReturn(listOf(LuauIdentifier("self")))
            )
        }

        return LuauFunctionStmt(name, typeParameters, params, body, returnType)
    }

    override fun visitClass(declaration: IrClass, data: Nothing?): LuauClass {
        currentClass = declaration
        val name = declaration.name.asString()
        val memberTypes = LuauTypeSolver.fromClass(declaration)
        val declarations = declaration.declarations

        val initializers = declarations.filterIsInstance<IrAnonymousInitializer>()
        val initBody = if(initializers.isNotEmpty()) initializers.last().body.statements.map {
            lowerToStmt(it.accept(this, null))
        } else emptyList()

        val initializer = LuauFunctionStmt("$name:init", listOf(), listOf(), initBody, null)
        val constructors = declarations.filterIsInstance<IrConstructor>().map {
            it.accept(this, null) as LuauFunctionStmt
        }

        val memberFunctions = declaration.functions.map {
            it.accept(this, null) as LuauFunctionStmt
        }.filter {
            !it.name.startsWith("Any")
        }.toList()

        return LuauClass(name, memberTypes, initializer, constructors, memberFunctions)
    }

    override fun visitConstructorCall(expression: IrConstructorCall, data: Nothing?): LuauNamecall {
        val targetConstructor = expression.symbol.owner
        val className = (targetConstructor.parent as IrClass).name.asString()
        val constructorName = getConstructorName(targetConstructor)

        val args = expression.arguments
            .mapNotNull { it?.accept(this, data) as? LuauExpr }

        return LuauNamecall(className, constructorName, args)
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?): LuauInterpolatedStringLiteral {
        val args = expression.arguments.map { it.accept(this, null) as LuauExpr }
        return LuauInterpolatedStringLiteral(args)
    }

    override fun visitConst(expression: IrConst, data: Nothing?): LuauExpr = when(val value = expression.value) {
        is String -> LuauStringLiteral(value)
        is Number -> LuauNumberLiteral(value)
        is Boolean -> LuauBoolLiteral(value)
        else -> error("Unsupported constant type: ${value?.let { it::class.simpleName }}")
    }

    override fun visitVariable(declaration: IrVariable, data: Nothing?): LuauVarDecl {
        val name = declaration.name.asString()
        val init = declaration.initializer?.accept(this, data) as LuauExpr?
        return LuauVarDecl(LuauIdentifier(name), LuauTypeSolver.fromIr(declaration.type), init)
    }

    override fun visitGetValue(expression: IrGetValue, data: Nothing?): LuauExpr {
        val owner = expression.symbol.owner
        val name = owner.name.asString()
        val isFunction = owner.type.run {
            isFunction()
        }

        return if (isFunction) {
            LuauCall(name, listOf())
        } else {
            LuauIdentifier(name)
        }
    }

    override fun visitSetValue(expression: IrSetValue, data: Nothing?): LuauAssign {
        val name = expression.symbol.owner.name.asString()
        val value = expression.value.accept(this, data) as LuauExpr
        return LuauAssign(name, value)
    }

    override fun visitReturn(expression: IrReturn, data: Nothing?): LuauReturn {
        val args = expression.value.accept(this, data) as LuauExpr
        return LuauReturn(listOf(args))
    }

    override fun visitWhileLoop(loop: IrWhileLoop, data: Nothing?): LuauWhileLoop {
        val condition = loop.condition.accept(this, data) as LuauExpr
        val body: List<LuauStmt> = when(val b = loop.body!!) {
            is IrBlock -> b.statements.map { lowerToStmt(it.accept(this, data)) }
            else -> listOf(lowerToStmt(b.accept(this, data)))
        }
        return LuauWhileLoop(condition, body)
    }

    override fun visitBlock(expression: IrBlock, data: Nothing?): LuauStmt {
        if (expression.origin == IrStatementOrigin.FOR_LOOP) {
            return visitForLoop(expression, data)
        }
        return LuauBlock(expression.statements.map { lowerToStmt(it.accept(this, data)) })
    }

    fun visitForLoop(expression: IrBlock, data: Nothing?): LuauForLoop {
        val iterVar = expression.statements[0] as IrVariable
        val loop = expression.statements[1] as IrWhileLoop
        val loopBody = loop.body as IrBlock
        val loopVar = loopBody.statements[0] as IrVariable
        val loopBodyStmts: List<IrStatement> = loopBody.statements.drop(1).flatMap { stmt ->
            when (stmt) {
                is IrBlock -> stmt.statements
                else -> listOf(stmt)
            }
        }

        val iteratorType = iterVar.type.classFqName?.asString() ?: error("Missing iterator type")
        val loopVarName = LuauIdentifier(loopVar.name.asString())

        val (range, init, body) = when (iteratorType) {
            "kotlin.collections.IntIterator" -> handleIntIterator(iterVar.initializer, loopBodyStmts, loopVarName, data)
            "kotlin.collections.CharIterator" -> handleCharIterator(iterVar.initializer, loopBodyStmts, loopVarName, data)
            else -> error("Unhandled iterator type: $iteratorType")
        }

        return LuauForLoop(
            loopVarName,
            range.first,
            range.second,
            range.third,
            body,
            init
        )
    }

    private fun handleIntIterator(
        initializer: IrExpression?,
        loopStmts: List<IrStatement>,
        loopVarName: LuauIdentifier,
        data: Nothing?
    ): Triple<Triple<LuauExpr, LuauExpr, LuauExpr>, List<LuauStmt>, List<LuauStmt>> {
        var stepAmount: Int? = null
        var current = (initializer as IrCall).arguments[0] as IrCall
        while (true) {
            val name = current.symbol.owner.name.asString()
            if (name == "step" && stepAmount == null) {
                val stepArg = current.arguments[1] as IrConst
                val stepValue = stepArg.value as Int
                stepAmount = stepValue
            } else if (name != "step") {
                break
            }

            val receiver = current.arguments[0]
            if (receiver is IrCall) {
                current = receiver
            } else break
        }

        val iterName = current.symbol.owner.name.asString()
        if (stepAmount == null) {
            stepAmount = 1
        }

        if (iterName == "downTo") {
            stepAmount = -stepAmount
        }

        val start = current.arguments[0]?.accept(this, data) as LuauExpr
        val end = current.arguments[1]?.accept(this, data) as LuauExpr

        val range = Triple(
            start,
            end,
            LuauNumberLiteral(stepAmount)
        )
        val body = loopStmts.map { lowerToStmt(it.accept(this, data)) }
        return Triple(range, emptyList(), body)
    }

    private fun handleCharIterator(
        initializer: IrExpression?,
        loopStmts: List<IrStatement>,
        loopVarName: LuauIdentifier,
        data: Nothing?
    ): Triple<Triple<LuauExpr, LuauExpr, LuauExpr>, List<LuauStmt>, List<LuauStmt>> {
        val backingVarName = LuauIdentifier("${loopVarName.name}V")
        val strExpr = (initializer as IrCall).arguments[0]?.accept(this, data) as LuauExpr
        val initStmt = LuauVarDecl(backingVarName, "string", strExpr)

        val range = Triple(
            LuauNumberLiteral(1),
            LuauUnaryOp("#", backingVarName),
            LuauNumberLiteral(1)
        )

        val charExtractStmt = LuauVarDecl(
            LuauIdentifier(loopVarName.name),
            "string",
            LuauCall("string.sub", listOf(
                backingVarName,
                loopVarName,
                loopVarName
            ))
        )

        val bodyStmts = loopStmts.map { lowerToStmt(it.accept(this, data)) }
        return Triple(range, listOf(initStmt), listOf(charExtractStmt) + bodyStmts)
    }
}
