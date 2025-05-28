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

    fun line(text: String) = builder.appendLine("$indentString$text")

    override fun toString() = builder.toString()
}