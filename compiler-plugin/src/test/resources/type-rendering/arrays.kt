fun arrays(
    values: Array<Int>, nullable: Array<Int>?, elements: Array<Int?>,
    both: Array<Int?>?, nested: Array<Array<String?>?>,
    projected: Array<out Number>, contravariant: Array<in String>,
) {}

fun primitives(
    bytes: ByteArray, shorts: ShortArray, ints: IntArray, longs: LongArray,
    floats: FloatArray, doubles: DoubleArray, booleans: BooleanArray, chars: CharArray,
    nullable: IntArray?,
) {}
