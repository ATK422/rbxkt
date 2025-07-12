package xyz.atkdev.rbxkt.util

class IndentedStringBuilder(var level: Int = 0) {
    private val builder = StringBuilder()

    val indentString get() = "    ".repeat(level)

    fun indent(block: () -> Unit) {
        level++
        block()
        level--
    }

    fun line(text: String) = text.split("\n").forEach { append("$indentString$it\n") }
    fun append(text: String): StringBuilder = builder.append(text)

    fun removeLine() = builder.deleteAt(builder.lastIndexOf('\n'))

    override fun toString() = builder.toString()
}