package xyz.atkdev.rbxkt.luau

class IndentedStringBuilder {
    private val builder = StringBuilder()
    private var level = 0

    val indentString get() = "    ".repeat(level)

    fun indent(block: () -> Unit) {
        level++
        block()
        level--
    }

    fun line(text: String) = text.split("\n").forEach { append("$indentString$it\n") }
    fun append(text: String): StringBuilder = builder.append(text)

    override fun toString() = builder.toString()
}