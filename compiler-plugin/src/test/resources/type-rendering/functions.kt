fun functions(
    action: () -> Unit,
    transform: (Int, String?) -> Boolean,
    nullableReturn: () -> Int?,
    nullableFunction: (() -> Int)?,
    both: (() -> Int?)?,
    nested: ((Int) -> String) -> (() -> Boolean)?,
    extension: String.(Int) -> Boolean,
    callbacks: List<((Int?) -> String?)?>,
) {}
