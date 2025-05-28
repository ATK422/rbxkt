package xyz.atkdev.rbxkt.luau

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.backend.js.lower.calls.PrimitiveType
import org.jetbrains.kotlin.ir.backend.js.lower.calls.getPrimitiveType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isArray
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isCollection
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.getArrayElementType

object LuauTypeSolver {
    var irContext: IrPluginContext? = null
    private val pluginContext get() = irContext!!

    fun fromIr(ir: IrType): String {
        val result =
            if (ir.run { isByte() || isShort() || isInt() || isLong() || isFloat() || isDouble() }) "number"
            else if (ir.isBoolean()) "bool"
            else if (ir.isString()) "string"
            else if (ir.isArray()) "{ [number]: ${fromIr(ir.getArrayElementType(pluginContext.irBuiltIns))} }>"
            else "any"

        return result + ir.isMarkedNullable().let { if (it) "?" else "" }
    }
}