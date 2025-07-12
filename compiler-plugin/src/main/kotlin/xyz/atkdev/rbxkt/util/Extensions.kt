package xyz.atkdev.rbxkt.util;

fun String.parenthesize(wrap: Boolean = true) = if (wrap) "($this)" else this