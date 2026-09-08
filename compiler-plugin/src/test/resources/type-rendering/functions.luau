functions.action: () -> nil
functions.transform: (number, string?) -> boolean
functions.nullableReturn: () -> number?
functions.nullableFunction: (() -> number)?
functions.both: (() -> number?)?
functions.nested: ((number) -> string) -> (() -> boolean)?
functions.extension: (string, number) -> boolean
functions.callbacks: { [number]: ((number?) -> string?)? }
functions.return: nil
