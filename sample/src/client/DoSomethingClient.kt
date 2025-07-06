package xyz.atkdev.rbxkt

fun fizzBuzz(n: Int): String {
    if (n % 15 == 0) {
        return "FizzBuzz"
    } else if (n % 3 == 0) {
        return "Fizz"
    } else if (n % 5 == 0) {
        return "Buzz"
    } else {
        return "$n"
    }
}

fun main() {
    for (i in 1..100) {
        println(fizzBuzz(i))
    }
}