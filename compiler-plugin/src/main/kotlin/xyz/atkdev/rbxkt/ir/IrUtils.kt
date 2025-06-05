package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction

fun getConstructorName(constructor: IrConstructor): String {
    return if (constructor.isPrimary) {
        "constructor"
    } else {
        "from${
            constructor.valueParameters.joinToString("And") {
                it.name.asString().replaceFirstChar { it.uppercase() }
            }
        }"
    }
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