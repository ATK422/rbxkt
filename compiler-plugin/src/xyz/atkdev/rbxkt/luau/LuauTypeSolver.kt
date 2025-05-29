package xyz.atkdev.rbxkt.luau

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.backend.js.lower.calls.PrimitiveType
import org.jetbrains.kotlin.ir.backend.js.lower.calls.getPrimitiveType
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.getArrayElementType
import org.jetbrains.kotlin.ir.util.isFunction

object LuauTypeSolver {
    var irContext: IrPluginContext? = null
    private val pluginContext get() = irContext!!

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
        val result =
            if (ir.run { isByte() || isShort() || isInt() || isLong() || isFloat() || isDouble() }) "number"
            else if (ir.isBoolean()) "bool"
            else if (ir.isString()) "string"
            else if (ir.isArray()) "{ [number]: ${fromIr(ir.getArrayElementType(pluginContext.irBuiltIns))} }>"
            else if (ir.isFunction()) fromFunction(ir)
            else if (ir is IrSimpleType && ir.classifier.owner is IrClass) (ir.classifier.owner as IrClass).name.asString()
            else if (ir is IrSimpleType && ir.classifier.owner is IrTypeParameter) (ir.classifier.owner as IrTypeParameter).name.asString()
            else "any"

        return result + ir.isMarkedNullable().let { if (it) "?" else "" }
    }
}