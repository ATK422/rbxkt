package com.rbxkt.typegen.utils

internal fun <T> List<List<T>>.cartesianProduct(): List<List<T>> =
    this.fold(listOf(emptyList())) { acc, set ->
        acc.flatMap { prefix -> set.map { prefix + it } }
    }
