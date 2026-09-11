package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import xyz.atkdev.rbxkt.luau.LuauImportAnalyzer
import xyz.atkdev.rbxkt.luau.*

class LuauEmitter(private val context: IrPluginContext): IrVisitor<LuauNode, Nothing?>() {
    private lateinit var currentClass: IrClass
    private val valueNames = mutableMapOf<IrValueSymbol, String>()
    private val reservedNames = mutableSetOf<String>()
    private var receiverIndex = 0

    private fun freshName(): String {
        var name: String
        do { name = "__rbxkt_tmp_${receiverIndex++}" } while (!reservedNames.add(name))
        return name
    }

    private fun LuauExpr.renderReceiver(): String = when (this) {
        is LuauIdentifier, is LuauCall, is LuauNamecall -> render()
        else -> "(${render()})"
    }

    private fun valueName(declaration: IrValueDeclaration): String = valueNames.getOrPut(declaration.symbol) {
        val name = declaration.name.asString()
        if (name.startsWith("\$this\$") || name == "<this>") {
            var candidate: String
            do { candidate = "__rbxkt_receiver_${receiverIndex++}" } while (!reservedNames.add(candidate))
            candidate
        } else name
    }

    private fun nativeName(function: IrSimpleFunction): String {
        fun annotatedName(function: IrSimpleFunction): String? {
            val annotation = function.correspondingPropertySymbol?.owner?.getAnnotation(FqName("com.rbxkt.types.LuauName"))
                ?: function.getAnnotation(FqName("com.rbxkt.types.LuauName"))
            return (annotation?.arguments?.firstOrNull() as? IrConst)?.value as? String
                ?: function.overriddenSymbols.firstNotNullOfOrNull { annotatedName(it.owner) }
        }
        return annotatedName(function) ?: (function.correspondingPropertySymbol?.owner?.name ?: function.name)
            .asString().replaceFirstChar { it.uppercase() }
    }

    private fun lowerToStmt(node: LuauNode): LuauStmt {
        if (node is LuauExpr) {
            return LuauExprStmt(node)
        }
        return node as LuauStmt
    }

    fun emitFile(irFile: IrFile): LuauFile {
        irFile.accept(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                if (element is IrDeclarationWithName) reservedNames += element.name.asString()
                element.acceptChildren(this, null)
            }
        }, null)
        val comments = listOf(
            LuauComment("!optimize 2", false),
            LuauComment("!native", false)
        )

        val statements = LuauBuilderLowering(::freshName).lower(irFile.declarations.map {
            lowerToStmt(it.accept(this, null))
        })

        val file = LuauFile(irFile.kotlinFqName.asString(), comments, statements)
        file.exports = LuauExportAnalyzer.getExportsForFile(irFile)
        file.imports = LuauImportAnalyzer.analyze(irFile)

        return file
    }

    override fun visitElement(element: IrElement, data: Nothing?): LuauNode {
        error("Unhandled element: ${element::class.simpleName}")
    }

    override fun visitVararg(expression: IrVararg, data: Nothing?): LuauExpr {
        val elements = expression.elements.map {
            require(it !is IrSpreadElement) { "Spread elements in collection construction are not supported yet" }
            it.accept(this, data) as LuauExpr
        }
        return LuauArrayLiteral(elements)
    }

    override fun visitCall(expression: IrCall, data: Nothing?): LuauNode {
        // TODO: implement overloading
        val receiver = expression.dispatchReceiver
            ?.accept(this, null) as? LuauExpr

        val owner = expression.symbol.owner
        val name = owner.name.asString()
        val args = expression.arguments
            .mapNotNull { it?.accept(this, data) as? LuauExpr }

        if (name == "invoke" && receiver != null && expression.dispatchReceiver!!.type.isFunction()) {
            return LuauCall(receiver.renderReceiver(), args.drop(1))
        }

        val nativeOwner = (owner.parent as? IrClass)?.fqNameWhenAvailable?.asString()
        if (name != "toString" && receiver != null &&
            (nativeOwner?.startsWith("com.rbxkt.types.classes.") == true ||
                nativeOwner in setOf("com.rbxkt.types.datatypes.RBXScriptSignal", "com.rbxkt.types.datatypes.RBXScriptConnection"))) {
            val property = owner.correspondingPropertySymbol?.owner
            val memberName = nativeName(owner)
            return when {
                property?.setter == owner -> LuauAssign("${receiver.renderReceiver()}.$memberName", args.last())
                property?.getter == owner -> LuauIdentifier("${receiver.renderReceiver()}.$memberName")
                else -> LuauNamecall(receiver.renderReceiver(), memberName, args.drop(1))
            }
        }

        if (owner.isKotlinArrayFactory() || owner.isKotlinListFactory()) {
            return if (owner.parameters.any { it.varargElementType != null } && args.isNotEmpty()) {
                args.single()
            } else {
                LuauArrayLiteral(args)
            }
        }

        //Handle unary operations
        if (name == "unaryMinus") {
            return LuauUnaryOp("-", args.first())
        } else if (name == "unaryPlus") {
            return args.first()
        } else if (name == "not" && expression.origin != IrStatementOrigin.EXCLEQ) {
            return LuauNot(args.first())
        }

        val isSetterGetter = owner.correspondingPropertySymbol != null
        val isSuperCall = expression.superQualifierSymbol != null
        if (name == "toString")
            return LuauCall("tostring", listOf(args.first()))
        else if (isSuperCall) {
            return LuauNamecall("self.super", name, args)
        } else if (isSetterGetter) {
            val property = owner.correspondingPropertySymbol?.owner!!
            val isSetter = owner == property.setter
            val isGetter = owner == property.getter
            var receiverName = receiver?.render() ?: error("Property access requires a receiver")
            if (receiverName == "<this>") {
                receiverName = "self"
            }

            val name = getSetterGetterName(owner)
            val isDefault = isDefaultSetterGetter(owner)

            if (isDefault) {
                if (isSetter) {
                    val value = args.last()
                    return LuauAssign("$receiverName.$name", value)
                } else if (isGetter) {
                    return LuauIdentifier("$receiverName.$name")
                }
            } else {
                if (isSetter) {
                    return LuauNamecall(receiverName, name, listOf(args.last()))
                }
                return LuauNamecall(receiverName, name, listOf())
            }
        } else if (expression.origin != null && args.size == 2 && expression.origin != IrStatementOrigin.EXCLEQ) {
            val lhs = args[0]
            val rhs = args[1]

            val op = when (expression.origin) {
                //Numerical Operations
                IrStatementOrigin.PLUS -> "+"
                IrStatementOrigin.MINUS -> "-"
                IrStatementOrigin.MUL -> "*"
                IrStatementOrigin.DIV -> "/"
                IrStatementOrigin.PERC -> "%"
                IrStatementOrigin.PLUSEQ -> "+="
                IrStatementOrigin.MINUSEQ -> "-="
                IrStatementOrigin.MULTEQ -> "*="
                IrStatementOrigin.DIVEQ -> "/="
                IrStatementOrigin.PERCEQ -> "%="

                //Comparison Operations
                IrStatementOrigin.LT -> "<"
                IrStatementOrigin.GT -> ">"
                IrStatementOrigin.LTEQ -> "<="
                IrStatementOrigin.GTEQ -> ">="
                IrStatementOrigin.EQEQ -> "=="
                IrStatementOrigin.ANDAND -> "and"
                IrStatementOrigin.OROR -> "or"

                //Custom Handled Expressions
                //TODO: Handle these properly without dropping them
                IrStatementOrigin.RANGE -> null
                IrStatementOrigin.RANGE_UNTIL -> null
                IrStatementOrigin.IN -> null
                IrStatementOrigin.NOT_IN -> null
                IrStatementOrigin.POSTFIX_INCR -> null
                IrStatementOrigin.POSTFIX_DECR -> null
                else -> error("Unhandled Operator Expression: ${expression.origin}")
            }

            op?.let {
                return LuauBinaryExpr(lhs, it, rhs, expression.origin!!.isComparisonOperator() || expression.origin == IrStatementOrigin.EQEQ)
            }
        } else if (name == "not" && expression.origin == IrStatementOrigin.EXCLEQ) {
            val args = (expression.arguments[0] as IrCall).arguments.map { it?.accept(this, data) as LuauExpr }
            return LuauBinaryExpr(args[0], "~=", args[1], true)
        } else if (expression.dispatchReceiver != null) {
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

    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?): LuauNode {
        return when (expression.operator) {
            IrTypeOperator.IMPLICIT_CAST, IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> expression.argument.accept(this, data)
            IrTypeOperator.CAST -> LuauCastExpr(expression.argument.accept(this, data) as LuauExpr, LuauTypeSolver.fromIr(expression.typeOperand))
            else -> error("Unhandled type operator: ${expression.operator}")
        }
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
            LuauParameter(LuauIdentifier(valueName(it)), LuauTypeSolver.fromIr(it.type))
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

    override fun visitConstructorCall(expression: IrConstructorCall, data: Nothing?): LuauExpr {
        lowerBuilder(expression)?.let { return it }
        val targetConstructor = expression.symbol.owner
        val className = (targetConstructor.parent as IrClass).name.asString()
        val constructorName = getConstructorName(targetConstructor)
        val constructorType = expression.type.classFqName!!.asString()

        val args = expression.arguments
            .mapNotNull { it?.accept(this, data) as? LuauExpr }

        if (constructorType.startsWith("com.rbxkt.types.classes.")) {
            val instance = LuauCall("Instance.new", listOf(LuauStringLiteral(className)))
            if (args.isEmpty()) return instance
            require(targetConstructor.parameters.size == 1 && targetConstructor.parameters.single().type.isFunction()) {
                "Unsupported Roblox constructor: $constructorType"
            }
            return LuauBuilderExpr(instance, args.single())
        }

        return LuauNamecall(className, constructorName, args)
    }

    private fun lowerBuilder(expression: IrConstructorCall, preferredName: String? = null): LuauInitExpr? {
        val constructor = expression.symbol.owner
        val className = expression.type.classFqName?.asString() ?: return null
        if (!className.startsWith("com.rbxkt.types.classes.") || constructor.parameters.size != 1 ||
            !constructor.parameters.single().type.isFunction()) return null
        val argument = expression.arguments.singleOrNull() ?: return null
        val lambda = (argument as? IrFunctionExpression)?.function
        var hasReturn = false
        var shadowsName = false
        argument.accept(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                if (element is IrReturn && element.returnTargetSymbol == lambda?.symbol) hasReturn = true
                if (element is IrGetValue && element.symbol.owner.name.asString() == preferredName) shadowsName = true
                if (element is IrDeclarationWithName && element.name.asString() == preferredName) shadowsName = true
                element.acceptChildren(this, null)
            }
        }, null)
        // Local returns must still return from the builder, not the enclosing function.
        if (hasReturn) return null
        val name = if (!shadowsName && preferredName != null) preferredName else freshName()
        val instance = LuauIdentifier(name)
        val statements = mutableListOf<LuauStmt>()
        val configure = if (lambda == null) {
            val value = argument.accept(this, null) as LuauExpr
            if (value is LuauIdentifier) value else {
                val temporary = LuauIdentifier(freshName())
                statements += LuauVarDecl(temporary, LuauTypeSolver.fromIr(argument.type), value)
                temporary
            }
        } else null
        statements += LuauVarDecl(instance, LuauTypeSolver.fromIr(expression.type),
            LuauCall("Instance.new", listOf(LuauStringLiteral(className.substringAfterLast('.')))))
        if (lambda != null) {
            val receiver = lambda.parameters.single().symbol
            val previousName = valueNames.put(receiver, name)
            val body = try {
                lambda.body!!.statements.map { lowerToStmt(it.accept(this, null)) }
            } finally {
                if (previousName == null) valueNames.remove(receiver) else valueNames[receiver] = previousName
            }
            // Keep builder-local declarations out of the surrounding Kotlin scope.
            if (lambda.body!!.statements.any { it is IrDeclaration }) statements += LuauBlock(body)
            else statements += body
        } else {
            statements += LuauExprStmt(LuauCall(configure!!.renderReceiver(), listOf(instance)))
        }
        return LuauInitExpr(statements, instance)
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?): LuauInterpolatedStringLiteral {
        val args = expression.arguments.map { it.accept(this, null) as LuauExpr }
        return LuauInterpolatedStringLiteral(args)
    }

    override fun visitConst(expression: IrConst, data: Nothing?): LuauExpr = when(val value = expression.value) {
        null -> LuauNilLiteral
        is String -> LuauStringLiteral(value)
        is Char -> LuauStringLiteral(value.toString())
        is Number -> LuauNumberLiteral(value)
        is Boolean -> LuauBoolLiteral(value)
        else -> error("Unsupported constant type: ${value?.let { it::class.simpleName }}")
    }

    override fun visitVariable(declaration: IrVariable, data: Nothing?): LuauStmt {
        val name = declaration.name.asString()
        (declaration.initializer as? IrConstructorCall)?.let { constructor ->
            // A callback capturing the receiver must keep the original instance even if a var is reassigned.
            lowerBuilder(constructor, name.takeUnless { declaration.isVar })?.let { init ->
                return LuauStatements(init.statements + if (init.value.name == name) emptyList() else
                    listOf(LuauVarDecl(LuauIdentifier(name), LuauTypeSolver.fromIr(declaration.type), init.value)))
            }
        }
        val init = declaration.initializer?.accept(this, data)
        return LuauVarDecl(LuauIdentifier(name), LuauTypeSolver.fromIr(declaration.type), init as? LuauExpr)
    }

    override fun visitGetValue(expression: IrGetValue, data: Nothing?): LuauExpr {
        val owner = expression.symbol.owner
        return LuauIdentifier(valueNames[owner.symbol] ?: if (owner.name.asString() == "<this>") "self" else valueName(owner))
    }

    override fun visitSetValue(expression: IrSetValue, data: Nothing?): LuauAssign {
        val name = expression.symbol.owner.name.asString()
        val value = expression.value.accept(this, data) as LuauExpr
        return LuauAssign(name, value)
    }

    override fun visitReturn(expression: IrReturn, data: Nothing?): LuauReturn {
        if (expression.value is IrGetObjectValue && expression.value.type.isUnit()) return LuauReturn(emptyList())
        val args = expression.value.accept(this, data) as LuauReturnable
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

    override fun visitBlock(expression: IrBlock, data: Nothing?): LuauNode {
        return when (expression.origin) {
            IrStatementOrigin.FOR_LOOP -> visitForLoop(expression, data)
            IrStatementOrigin.POSTFIX_INCR -> {
                val lhs = (expression.statements[1] as IrSetValue)
                val type = (expression.statements[2] as IrGetValue).type
                if (type.isNumericalType())
                    LuauBinaryExpr(LuauIdentifier(lhs.symbol.owner.name.asString()), "+=", LuauNumberLiteral(1))
                else
                    LuauNamecall(lhs.symbol.owner.name.asString(), "inc", emptyList())
            }
            IrStatementOrigin.POSTFIX_DECR -> {
                val lhs = (expression.statements[1] as IrSetValue)
                val type = (expression.statements[2] as IrGetValue).type
                if (type.isNumericalType())
                    LuauBinaryExpr(LuauIdentifier(lhs.symbol.owner.name.asString()), "-=", LuauNumberLiteral(1))
                else
                    LuauNamecall(lhs.symbol.owner.name.asString(), "dec", emptyList())
            }
            else -> LuauBlock(expression.statements.map { lowerToStmt(it.accept(this, data)) })
        }
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

    override fun visitBranch(branch: IrBranch, data: Nothing?): LuauBranch {
        val condition = branch.condition.accept(this, data) as LuauExpr
        val result = ((branch.result as? IrBlock)?.statements ?: listOf(branch.result)).map {
            lowerToStmt(it.accept(this, data))
        }
        return LuauBranch.Conditional(condition, result)
    }

    override fun visitElseBranch(branch: IrElseBranch, data: Nothing?): LuauBranch {
        val result = ((branch.result as? IrBlock)?.statements ?: listOf(branch.result)).map {
            lowerToStmt(it.accept(this, data))
        }
        return LuauBranch.Else(result)
    }

    override fun visitWhen(expression: IrWhen, data: Nothing?): LuauWhen {
        val branches = expression.branches.map { it.accept(this, data) as LuauBranch  }
        return LuauWhen(branches)
    }
}
