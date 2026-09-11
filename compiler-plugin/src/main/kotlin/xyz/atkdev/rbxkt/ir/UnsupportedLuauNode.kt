package xyz.atkdev.rbxkt.ir

import org.jetbrains.kotlin.ir.IrElement

internal class UnsupportedLuauNode(val element: IrElement, reason: String) : RuntimeException(reason)
