package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNumber
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

private val kotlinArrayFactories = setOf(
    "kotlin.arrayOf",
    "kotlin.emptyArray",
    "kotlin.booleanArrayOf",
    "kotlin.byteArrayOf",
    "kotlin.charArrayOf",
    "kotlin.shortArrayOf",
    "kotlin.intArrayOf",
    "kotlin.longArrayOf",
    "kotlin.floatArrayOf",
    "kotlin.doubleArrayOf",
)

fun IrSimpleFunction.isKotlinArrayFactory(): Boolean =
    fqNameWhenAvailable?.asString() in kotlinArrayFactories

private val kotlinListFactories = setOf(
    "kotlin.collections.listOf",
    "kotlin.collections.emptyList",
    "kotlin.collections.mutableListOf",
)

fun IrSimpleFunction.isKotlinListFactory(): Boolean =
    fqNameWhenAvailable?.asString() in kotlinListFactories

fun getConstructorName(constructor: IrConstructor): String {
    return if (constructor.isPrimary) {
        "constructor"
    } else {
        "from${
            constructor.parameters.joinToString("And") {
                it.name.asString().replaceFirstChar { it.uppercase() }
            }
        }"
    }
}

fun IrType.isNumericalType(): Boolean {
    return this.isByte() || this.isShort() || this.isInt() || this.isLong() || this.isFloat() || this.isDouble() || this.isChar() || this.isNumber()
}

fun isDefaultSetterGetter(function: IrSimpleFunction): Boolean {
    return function.origin == IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
}

fun getSetterGetterName(function: IrSimpleFunction): String {
    val property = function.correspondingPropertySymbol?.owner!!
    var name = property.name.asString()

    val isSetter = function == property.setter
    val isGetter = function == property.getter
    val isDefault = isDefaultSetterGetter(function)

    if (!isDefault) {
        name = name.replaceFirstChar { it.uppercase() }
        if (isSetter) {
            return "set$name"
        } else if (isGetter) {
            return "get$name"
        }
    }

    return name
}
