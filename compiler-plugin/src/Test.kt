package wtf.lynn

fun add(a: Int, b: Int): Int {
    return a + b
}

fun <T> wrap(fn: () -> T): T {
    return fn()
}

fun main() {
    var a = 1
    var b = 2
    var c = add(a, b) + 1
    var d = wrap {
        a + b + c
    }
}