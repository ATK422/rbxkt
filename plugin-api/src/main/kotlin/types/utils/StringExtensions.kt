package types.utils

internal fun String.toCamelCase(): String = this.first().lowercase() + this.substring(1)

internal fun String.fromOperator(): String = when (this) {
    "+" -> "plus"
    "-" -> "minus"
    "*" -> "times"
    "/" -> "div"
    "%" -> "rem"
    "//" -> "floorDiv"
    else -> error("$this is not an operator")
}
