package org.hime.lang

import org.hime.lang.exception.HimeParserException
import org.hime.lang.exception.HimeRuntimeException
import org.hime.parse.Token

fun himeAssertRuntime(value: Boolean, lazyMessage: () -> String) {
    if (!value)
        throw HimeRuntimeException(lazyMessage())
}

fun himeAssertParser(value: Boolean, lazyMessage: () -> String) {
    if (!value)
        throw HimeParserException(lazyMessage())
}

fun himeAssertType(tok: Token, type: HimeType, env: Env) {
    himeAssertRuntime(
        env.isType(
            tok,
            type
        )
    ) { "$tok is not ${type.name}." }
}

fun himeAssertType(tok: Token, typeName: String, env: Env) {
    himeAssertRuntime(
        env.isType(
            tok,
            env.getType(typeName)
        )
    ) { "$tok is not $typeName." }
}