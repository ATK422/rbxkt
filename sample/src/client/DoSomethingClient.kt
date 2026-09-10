package xyz.atkdev.rbxkt

import com.rbxkt.types.classes.Part

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
//        println(fizzBuzz(i))
    }

    val cd = 10
    val c = -(cd + 100 + -cd)

    val p = Part {
        name = "FizzBuzz"
    }
}