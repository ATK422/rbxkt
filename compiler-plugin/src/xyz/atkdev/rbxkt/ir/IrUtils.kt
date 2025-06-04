package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.ir.declarations.IrConstructor

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