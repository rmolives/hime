package ink.hime.lang

fun himeAssertRuntime(value: Boolean, lazyMessage: () -> String) {
    if (!value)
        throw HimeRuntimeException(lazyMessage())
}

fun himeAssertParser(value: Boolean, lazyMessage: () -> String) {
    if (!value)
        throw HimeParserException(lazyMessage())
}