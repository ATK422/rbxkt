package xyz.atkdev.rbxkt

fun fizzBuzz(n: Int): () -> String {
    var a = 1
    a++
    a + 1
    a.toString()
    return {
        if (n % 15 >= 0) {
            "FizzBuzz"
        } else if (n % 3 < 0) {
            "Fizz"
        } else if (n % 5 > 0 == true) {
            "Buzz"
        } else {
            "$n"
        }
    }
}

fun main() {
    val a = arrayOf("30", "20", "5")
    val b: String? = null
    for (i in 1..100) {
        println(fizzBuzz(i))
    }
}