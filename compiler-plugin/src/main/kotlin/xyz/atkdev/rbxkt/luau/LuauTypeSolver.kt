package xyz.atkdev.rbxkt.luau

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.getArrayElementType
import org.jetbrains.kotlin.ir.util.isPrimitiveArray
import org.jetbrains.kotlin.ir.util.isFunction

object LuauTypeSolver {
    var irContext: IrPluginContext? = null
    private val pluginContext get() = irContext!!

    fun fromName(name: Name) = when(val name = name.asString()) {
        "Unit" -> "nil"
        else -> name
    }

    fun fromClass(ir: IrClass): List<String> {
        return ir.declarations
            .filterIsInstance<IrProperty>()
            .map { property ->
                val name = property.name.asString()
                val typeStr = property.backingField?.type?.let { LuauTypeSolver.fromIr(it) } ?: "any"
                "$name: $typeStr"
            }
    }

    fun fromFunction(ir: IrType): String {
        if (ir !is IrSimpleType || !ir.isFunction()) return "any"
        val typeParams = ir.arguments.dropLast(1).mapNotNull {
            it.typeOrNull?.let { fromIr(it) }
        }
        val returnType = fromIr(ir.arguments.lastOrNull()?.typeOrNull ?: return "any")
        return "(${typeParams.joinToString(", ")}) -> $returnType"
    }

    fun fromIr(ir: IrType): String {
        if (ir.isMarkedNullable()) {
            if (ir.classFqName?.asString() == "kotlin.Nothing") return "nil"
            val nonNullable = ir.makeNotNull()
            val type = fromIr(nonNullable)
            return when {
                type == "any" || type == "nil" -> type
                nonNullable.isFunction() -> "($type)?"
                else -> "$type?"
            }
        }

        val result =
            if (ir.run { isByte() || isShort() || isInt() || isLong() || isFloat() || isDouble() || isNumber() }) "number"
            else if (ir.isBoolean()) "boolean"
            else if (ir.isString() || ir.isChar()) "string"
            else if (ir.isAny()) "any"
            else if (ir.isNothing()) "never"
            else if (ir.isArray() || ir.isPrimitiveArray()) "{ [number]: ${fromIr(ir.getArrayElementType(pluginContext.irBuiltIns))} }"
            else if (ir is IrSimpleType && ir.classFqName?.asString() in setOf("kotlin.collections.List", "kotlin.collections.MutableList")) {
                val elementType = ir.arguments.singleOrNull()?.typeOrNull?.let { fromIr(it) } ?: "any"
                "{ [number]: $elementType }"
            }
            else if (ir.isFunction()) fromFunction(ir)
            else if (ir is IrSimpleType) when (val owner = ir.classifier.owner) {
                is IrClass -> {
                    val name = fromName(owner.name)
                    if (ir.arguments.isEmpty()) name
                    else ir.arguments.joinToString(", ", "$name<", ">") {
                        it.typeOrNull?.let { type -> fromIr(type) } ?: "any"
                    }
                }
                is IrTypeParameter -> fromName(owner.name)
                else -> "any"
            }
            else "any"

        return result
    }
}
